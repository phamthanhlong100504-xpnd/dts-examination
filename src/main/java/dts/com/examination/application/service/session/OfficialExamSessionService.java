package dts.com.examination.application.service.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.api.form.*;
import dts.com.examination.api.response.*;
import dts.com.examination.application.event.LearningResultEvent;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.*;
import dts.com.examination.application.service.selection.*;
import dts.com.examination.domain.entity.*;
import dts.com.examination.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialExamSessionService {
    private final ExamRepository exams;
    private final ExamVersionRepository versions;
    private final ExamSessionRepository sessions;
    private final ExamSessionAnswerRepository answers;
    private final CurrentStudentProfileReader profiles;
    private final PublishedExamSnapshotReader publications;
    private final ExamParticipationPolicy participation;
    private final ExamQuestionSourceReader source;
    private final ExamQuestionSelectionService selector;
    private final OfficialSessionGrader grader;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final KafkaTemplate<String, Object> kafka;

    public boolean handles(StartExamSessionRequest request) {
        return request.getExamVersionId() != null
                ? versions.existsByIdAndExamType(request.getExamVersionId(), "OFFICIAL")
                : request.getExamId() != null && versions.existsByExamIdAndExamType(request.getExamId(), "OFFICIAL");
    }

    public boolean handlesSession(UUID id) { return sessions.isOfficialSession(id); }

    @Transactional
    public ExamSessionResponse start(StartExamSessionRequest request, UUID userId) {
        if (request == null || request.getExamId() == null && request.getExamVersionId() == null)
            throw bad("BAD_REQUEST", "examId or examVersionId is required");
        String license = profiles.selectedLicenseClass(userId);
        UUID examId = request.getExamVersionId() == null ? request.getExamId()
                : versions.findExamIdByVersionId(request.getExamVersionId())
                    .orElseThrow(() -> missing("EXAM_NOT_FOUND"));
        if (request.getExamId() != null && !request.getExamId().equals(examId))
            throw bad("BAD_REQUEST", "examId and examVersionId disagree");
        // Same lock order as publication/archive. Serializes starts and admin changes across JVMs.
        Exam exam = exams.findByIdForUpdate(examId).orElseThrow(() -> missing("EXAM_NOT_FOUND"));
        UUID versionId = request.getExamVersionId();
        if (versionId == null) {
            List<ExamVersion> eligible = versions.findStudentDetailVersions(examId, null, userId, license);
            if (eligible.isEmpty()) throw missing("EXAM_NOT_FOUND");
            versionId = eligible.get(0).getId();
        }
        ExamVersion version = versions.findByIdForUpdate(versionId).orElseThrow(() -> missing("EXAM_NOT_FOUND"));
        if (!examId.equals(version.getExamId()) || !"OFFICIAL".equals(version.getExamType()))
            throw missing("EXAM_NOT_FOUND");
        PublishedExamSnapshot config = publications.read(version);
        List<ExamSession> history = sessions.findOfficialHistoryForUpdate(versionId, userId);
        List<ExamSession> live = new ArrayList<>();
        for (var candidate : history) {
            if (candidate.getDeletedAt() == null && Set.of("IN_PROGRESS", "READY").contains(candidate.getStatus())) {
                ExamSession owned = sessions.findByIdForUpdate(candidate.getId()).orElseThrow(() -> missing("SESSION_NOT_FOUND"));
                var frozen = frozen(owned);
                expireIfNeeded(owned, frozen);
                if (Set.of("IN_PROGRESS", "READY").contains(owned.getStatus())) live.add(owned);
            }
        }
        if (live.size() > 1) throw conflict("SESSION_CONFLICT", "Multiple active attempts require administrative reconciliation");
        if (live.size() == 1) {
            ExamSession existing = live.get(0);
            if ("READY".equals(existing.getStatus())) resumeExisting(existing, frozen(existing));
            return startResponse(existing, frozen(existing), true);
        }
        Instant now = clock.instant();
        var decision = participation.assess(version, config, history, now,
                "PUBLISHED".equals(exam.getStatus()) && exam.getDeletedAt() == null,
                license.equals(config.profile().licenseClass()));
        if (!decision.canStart()) throw conflict(decision.reasonCode(), "This examination cannot start: " + decision.reasonCode());
        List<FrozenExamQuestion> questions;
        Map<UUID, String> assignments = new LinkedHashMap<>();
        if (config.profile().selectionMode() == ExamSelectionMode.MANUAL) {
            questions = mapper.convertValue(version.getConfigs().get("manualQuestionsSnapshot"),
                    new TypeReference<List<FrozenExamQuestion>>() {});
            Map<String, String> raw = mapper.convertValue(version.getConfigs().get("manualSectionAssignments"),
                    new TypeReference<Map<String, String>>() {});
            raw.forEach((id, section) -> assignments.put(UUID.fromString(id), section));
            if (!questions.stream().map(FrozenExamQuestion::id).toList().equals(config.profile().manualQuestionIds()))
                throw conflict("VERSION_NOT_READY", "The manual publication does not preserve its selected order");
        } else {
            ExamQuestionPool pool = source.loadBank(config.profile().bankVersion(), license);
            source.requireBankAnchor(pool, version.getContentId());
            var selection = selector.select(license, version.getStartedAt(), pool,
                    config.structure().getSections(), ExamSelectionMode.AUTO_MATRIX, List.of());
            if (!selection.matrixPolicyId().equals(config.policy().id()))
                throw conflict("VERSION_NOT_READY", "The selector policy differs from the published policy");
            assignments.putAll(selection.sectionCodeByQuestionId());
            questions = source.readVerifiedContent(pool, selection.questions().stream().map(ExamSourceQuestionMetadata::id).toList());
            questions = arrange(questions, assignments, config);
        }
        var paper = new FrozenOfficialSession(1, examId, versionId, version.getEndedAt(), config, questions, assignments);
        validateFrozen(paper);
        now = clock.instant();
        if (now.isBefore(version.getStartedAt()) || !now.isBefore(version.getEndedAt()))
            throw conflict("EXAM_CLOSED", "The examination window closed while reading the source");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("officialSessionVersion", 1);
        metadata.put("historyDisplayTitle", exam.getTitle());
        metadata.put("officialPaper", json(paper));
        metadata.put("currentPosition", 1);
        metadata.put("furthestPosition", 1);
        metadata.put("pauseCount", 0);
        metadata.put("totalPausedSeconds", 0L);
        Instant expiry = earlier(now.plusSeconds(config.rule().getDurationSeconds()), version.getEndedAt());
        metadata.put("nextTransitionAt", earlier(expiry.plusSeconds(config.rule().getGracePeriodSeconds()), version.getEndedAt()).toString());
        ExamSession session = sessions.saveAndFlush(ExamSession.builder().examVersionId(versionId).userId(userId)
                .sessionToken(UUID.randomUUID()).attemptNo(history.size() + 1).status("IN_PROGRESS")
                .startedAt(now).expiredAt(expiry)
                .durationSeconds(config.rule().getDurationSeconds()).clientInfo(request.getClientInfo() == null ? Map.of() : request.getClientInfo())
                .metadata(metadata).version(0).build());
        List<ExamSessionAnswer> rows = new ArrayList<>();
        int position = 0;
        for (var question : questions) {
            String code = assignments.get(question.id());
            Section section = config.structure().getSections().stream().filter(s -> code.equals(s.getCode())).findFirst().orElseThrow();
            List<UUID> options = new ArrayList<>(question.options().stream().map(FrozenExamQuestion.Option::id).toList());
            if (config.rule().isShuffleOptions()) Collections.shuffle(options, new java.security.SecureRandom());
            rows.add(ExamSessionAnswer.builder().examSessionId(session.getId()).questionId(question.id()).answeredAt(now).version(0)
                    .displaySnapshot(Map.of("position", ++position, "sectionCode", code, "sectionTitle", section.getTitle(),
                            "optionOrder", options.stream().map(UUID::toString).toList(), "marked", false)).build());
        }
        answers.saveAll(rows);
        return startResponse(session, paper, false);
    }

    @Transactional
    public ExamSessionDetailResponse detail(UUID id, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); expireIfNeeded(session, paper);
        var rows = orderedAnswers(session, paper);
        return ExamSessionDetailResponse.builder().sessionId(id).examId(paper.examId()).examVersionId(paper.examVersionId())
                .attemptNo(session.getAttemptNo()).status(session.getStatus()).startedAt(session.getStartedAt()).expiredAt(session.getExpiredAt())
                .durationSeconds(session.getDurationSeconds()).remainingSeconds(remaining(session))
                .totalQuestions((long) rows.size()).answeredQuestions(answered(rows)).examRule(safeRule(paper.configuration().rule()))
                .version(session.getVersion()).currentPosition(integer(session.getMetadata(), "currentPosition"))
                .markedQuestionIds(rows.stream().filter(row -> Boolean.TRUE.equals(row.getDisplaySnapshot().get("marked"))).map(ExamSessionAnswer::getQuestionId).toList())
                .serverNow(clock.instant()).build();
    }

    @Transactional
    public ExamPaperResponse paper(UUID id, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); expireIfNeeded(session, paper);
        if (!Set.of("IN_PROGRESS", "READY").contains(session.getStatus())) throw conflict("SESSION_CLOSED", "This paper is no longer active");
        var rows = orderedAnswers(session, paper);
        Map<UUID, FrozenExamQuestion> questions = new HashMap<>(); paper.questions().forEach(q -> questions.put(q.id(), q));
        List<QuestionPaperResponse> response = rows.stream().map(row -> {
            var q = questions.get(row.getQuestionId());
            return QuestionPaperResponse.builder().questionId(q.id()).content(q.content()).type(q.type())
                    .mediaFileIds(q.mediaFileIds()).isCritical(q.isCritical()).display(publicDisplay(row))
                    .options(orderedOptions(row, q).stream().map(o -> OptionPaperResponse.builder().id(o.id()).text(o.content()).build()).toList())
                    .selectedAnswer(row.getSelectedAnswer()).build();
        }).toList();
        return ExamPaperResponse.builder().sessionId(id).status(session.getStatus()).remainingSeconds(remaining(session))
                .questions(response).version(session.getVersion()).currentPosition(integer(session.getMetadata(), "currentPosition"))
                .serverNow(clock.instant()).build();
    }

    @Transactional
    public SaveAnswersResponse save(UUID id, SaveAnswersRequest request, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); requireActive(session, paper);
        requireVersion(session, request.getVersion());
        if (request.getAnswers() == null || request.getAnswers().isEmpty() || request.getAnswers().size() > paper.questions().size())
            throw bad("ANSWER_INVALID", "A non-empty, bounded answer list is required");
        Map<UUID, ExamSessionAnswer> rows = rowMap(orderedAnswers(session, paper));
        Map<UUID, FrozenExamQuestion> questions = new HashMap<>(); paper.questions().forEach(q -> questions.put(q.id(), q));
        Map<UUID, Map<String, Object>> normalized = new LinkedHashMap<>();
        for (var item : request.getAnswers()) {
            if (item == null || item.getQuestionId() == null || !questions.containsKey(item.getQuestionId()) || normalized.containsKey(item.getQuestionId()))
                throw bad("ANSWER_INVALID", "Every answer must uniquely belong to this session");
            requireAccessible(session, paper, rows.get(item.getQuestionId()), rows);
            normalized.put(item.getQuestionId(), normalize(item.getSelectedAnswer(), questions.get(item.getQuestionId())));
        }
        // Validate the entire batch before changing any managed row.
        Instant now = clock.instant();
        if (!now.isBefore(session.getExpiredAt())) throw conflict("SESSION_CLOSED", "The answer deadline passed while validating this batch");
        normalized.forEach((question, value) -> { var row = rows.get(question); row.setSelectedAnswer(value); row.setAnsweredAt(now); row.setVersion(row.getVersion() + 1); });
        answers.saveAll(normalized.keySet().stream().map(rows::get).toList()); advance(session);
        return SaveAnswersResponse.builder().savedAt(now).updatedQuestions(normalized.size()).version(session.getVersion()).build();
    }

    @Transactional
    public SessionProgressResponse progress(UUID id, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); expireIfNeeded(session, paper);
        return progress(session, orderedAnswers(session, paper));
    }

    @Transactional
    public SessionProgressResponse mark(UUID id, UUID questionId, Boolean marked, Integer version, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); requireActive(session, paper); requireVersion(session, version);
        if (marked == null) throw bad("BAD_REQUEST", "marked is required");
        var rows = orderedAnswers(session, paper); var row = rowMap(rows).get(questionId);
        if (row == null) throw bad("ANSWER_INVALID", "The marked question is outside this session");
        Map<String, Object> display = new LinkedHashMap<>(row.getDisplaySnapshot()); display.put("marked", marked); row.setDisplaySnapshot(display);
        answers.save(row); advance(session); return progress(session, rows);
    }

    @Transactional
    public SessionProgressResponse navigate(UUID id, Integer target, Integer version, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); requireActive(session, paper); requireVersion(session, version);
        var rows = orderedAnswers(session, paper); int current = integer(session.getMetadata(), "currentPosition");
        if (target == null || target < 1 || target > rows.size()) throw bad("NAVIGATION_INVALID", "The target position is outside this paper");
        var rule = paper.configuration().rule();
        if (!rule.isAllowSkip() && target != current && rows.get(current - 1).getSelectedAnswer() == null)
            throw conflict("SKIP_NOT_ALLOWED", "Answer the current question before navigating");
        if ("SEQUENTIAL".equals(rule.getNavigationMode())) {
            int furthest = integer(session.getMetadata(), "furthestPosition");
            if (target > furthest + 1) throw conflict("NAVIGATION_NOT_ALLOWED", "Sequential navigation cannot jump forward");
            if (target < current) requireAccessible(session, paper, rows.get(target - 1), rowMap(rows));
        }
        Map<String, Object> metadata = mutable(session); metadata.put("currentPosition", target);
        metadata.put("furthestPosition", Math.max(target, integer(metadata, "furthestPosition"))); session.setMetadata(metadata);
        advance(session); return progress(session, rows);
    }

    @Transactional
    public SessionProgressResponse violation(UUID id, SessionViolationRequest request, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session);
        if (request == null || request.eventId() == null || request.eventType() == null
                || !Set.of("TAB_SWITCH", "FULLSCREEN_EXIT").contains(request.eventType()))
            throw bad("BAD_REQUEST", "A unique eventId and supported eventType are required");
        var metadata = mutable(session);
        Set<String> recorded = new LinkedHashSet<>();
        if (metadata.get("violationEventIds") instanceof List<?> ids) ids.forEach(value -> recorded.add(value.toString()));
        if (recorded.contains(request.eventId().toString())) return progress(session, orderedAnswers(session, paper));
        requireActive(session, paper);
        if (recorded.size() >= 1000) throw conflict("VIOLATION_LIMIT", "The event log limit has been reached");
        var rule = paper.configuration().rule();
        recorded.add(request.eventId().toString()); metadata.put("violationEventIds", new ArrayList<>(recorded));
        int tabs = metadata.get("tabSwitchCount") instanceof Number n ? n.intValue() : 0;
        boolean failure = false;
        if ("TAB_SWITCH".equals(request.eventType()) && rule.isPreventTabSwitch()) {
            metadata.put("tabSwitchCount", ++tabs); failure = tabs > rule.getMaxTabSwitchCount();
        }
        if ("FULLSCREEN_EXIT".equals(request.eventType()) && rule.isRequireFullscreen()) failure = true;
        metadata.put("lastViolationAt", clock.instant().toString()); metadata.put("lastViolationType", request.eventType());
        if (failure) metadata.put("proctoringFailed", true);
        session.setMetadata(metadata);
        if (failure) finish(session, paper, null, "PROCTORING_VIOLATION"); else advance(session);
        return progress(session, orderedAnswers(session, paper));
    }

    @Transactional
    public PauseSessionResponse pause(UUID id, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); expireIfNeeded(session, paper);
        var rule = paper.configuration().rule();
        if ("READY".equals(session.getStatus())) return pauseResponse(session);
        requireActive(session, paper);
        if (!rule.isAllowPause() || !rule.isAllowResume()) throw conflict("PAUSE_NOT_ALLOWED", "Pausing is disabled");
        Map<String, Object> metadata = mutable(session); int count = integer(metadata, "pauseCount");
        if (count >= rule.getMaxPauseCount()) throw conflict("PAUSE_LIMIT", "The pause limit has been reached");
        Instant now = clock.instant(); metadata.put("pausedRemainingSeconds", remaining(session)); metadata.put("pausedAt", now.toString());
        metadata.put("pauseCount", count + 1); session.setMetadata(metadata); session.setStatus("READY");
        session.setExpiredAt(earlier(now.plusSeconds(Math.min(rule.getMaxPauseDurationSeconds(), rule.getResumeTimeoutSeconds())), paper.examEndedAt()));
        advance(session); return pauseResponse(session);
    }

    @Transactional
    public ResumeSessionResponse resume(UUID id, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); expireIfNeeded(session, paper);
        if (!"IN_PROGRESS".equals(session.getStatus())) resumeExisting(session, paper);
        return ResumeSessionResponse.builder().sessionId(id).status(session.getStatus()).resumedAt(clock.instant())
                .expiredAt(session.getExpiredAt()).remainingSeconds(Math.toIntExact(remaining(session))).build();
    }

    @Transactional
    public SubmitExamResponse submit(UUID id, String key, UUID user) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}")) throw bad("IDEMPOTENCY_KEY_REQUIRED", "A bounded Idempotency-Key is required");
        ExamSession session = owned(id, user); var paper = frozen(session);
        if ("SUBMITTED".equals(session.getStatus())) {
            if (session.getIdempotencyKey() != null && !key.equals(session.getIdempotencyKey()))
                throw conflict("IDEMPOTENCY_CONFLICT", "This attempt was submitted with a different key");
            return submitted(session, paper);
        }
        // A delayed submit can finish the already-frozen answers inside the configured grace period.
        Instant now = clock.instant();
        if ("IN_PROGRESS".equals(session.getStatus()) && !now.isBefore(session.getExpiredAt())
                && now.isBefore(earlier(session.getExpiredAt().plusSeconds(paper.configuration().rule().getGracePeriodSeconds()), paper.examEndedAt()))) {
            if (!paper.configuration().rule().isAllowSkip()) {
                var rows = orderedAnswers(session, paper);
                if (answered(rows) != rows.size()) throw conflict("UNANSWERED_QUESTIONS", "Every question must be answered before late manual submission");
            }
            finish(session, paper, key, "LATE_SUBMIT"); return submitted(session, paper);
        }
        expireIfNeeded(session, paper);
        if ("SUBMITTED".equals(session.getStatus())) return submitted(session, paper);
        requireActive(session, paper);
        var rows = orderedAnswers(session, paper);
        if (!paper.configuration().rule().isAllowSkip() && answered(rows) != rows.size())
            throw conflict("UNANSWERED_QUESTIONS", "This rule requires every question to be answered before manual submission");
        finish(session, paper, key, "USER_SUBMIT"); return submitted(session, paper);
    }

    @Transactional
    public ExamResultResponse result(UUID id, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); expireIfNeeded(session, paper);
        if (!"SUBMITTED".equals(session.getStatus())) throw conflict("SESSION_NOT_SUBMITTED", "The attempt has no submitted result");
        if (!released(session, paper)) return ExamResultResponse.builder().sessionId(id).status(session.getStatus()).submittedAt(session.getSubmittedAt())
                .resultPublished(false).resultReleaseMode(paper.configuration().rule().getResultReleaseMode()).releaseAt(releaseAt(paper)).build();
        var grade = grade(session); var rule = paper.configuration().rule(); var rows = orderedAnswers(session, paper);
        List<ExamResultDetailResponse> details = rows.stream().map(row -> ExamResultDetailResponse.builder().questionId(row.getQuestionId())
                .display(publicDisplay(row)).selectedAnswer(row.getSelectedAnswer())
                .isCorrect(rule.isShowAnswerAfterSubmit() ? row.getIsCorrect() : null)
                .score(rule.isShowQuestionScoreAfterSubmit() ? row.getScore() : null).build()).toList();
        return ExamResultResponse.builder().sessionId(id).status(session.getStatus()).submittedAt(session.getSubmittedAt()).resultPublished(true)
                .resultReleaseMode(rule.getResultReleaseMode()).releaseAt(releaseAt(paper)).summary(summary(grade, rows.size())).answers(details).build();
    }

    @Transactional
    public ExamReviewResponse review(UUID id, UUID user) {
        ExamSession session = owned(id, user); var paper = frozen(session); expireIfNeeded(session, paper);
        if (!"SUBMITTED".equals(session.getStatus()) || !released(session, paper)) throw conflict("RESULT_NOT_PUBLISHED", "The review is not released");
        var rule = paper.configuration().rule();
        if ("NONE".equals(rule.getReviewMode())) throw conflict("REVIEW_NOT_ALLOWED", "Reviewing is disabled");
        var rows = orderedAnswers(session, paper); Map<UUID, ExamSessionAnswer> rowMap = rowMap(rows);
        List<QuestionReviewResponse> questions = paper.questions().stream().filter(q -> !"CURRENT_SECTION".equals(rule.getReviewMode())
                || paper.sectionAssignments().get(q.id()).equals(rows.get(integer(session.getMetadata(), "currentPosition") - 1).getDisplaySnapshot().get("sectionCode")))
                .map(q -> { var row = rowMap.get(q.id()); return QuestionReviewResponse.builder().id(q.id()).content(q.content()).type(q.type())
                    .mediaFileIds(q.mediaFileIds().stream().map(UUID::toString).toList()).userAnswer(row.getSelectedAnswer())
                    .isCorrect(rule.isShowAnswerAfterSubmit() ? row.getIsCorrect() : null)
                    .options(orderedOptions(row, q).stream().map(o -> OptionReviewResponse.builder().id(o.id()).content(o.content())
                            .isCorrect(rule.isShowAnswerAfterSubmit() ? o.isCorrect() : null).build()).toList())
                    .explanation(rule.isShowExplanationAfterSubmit() && q.explanations() != null ? explanation(q.explanations()) : null).build(); }).toList();
        var grade = grade(session);
        return ExamReviewResponse.builder().sessionId(id).examId(paper.examId()).examVersionId(paper.examVersionId())
                .finalResult(grade.passed() ? "PASS" : "FAIL").score(grade.score()).correctCount(grade.correctCount()).totalQuestions(rows.size()).questions(questions).build();
    }

    @Transactional
    public void releaseManually(UUID id, UUID administrator) {
        ExamSession session = sessions.findByIdForUpdate(id).orElseThrow(() -> missing("SESSION_NOT_FOUND")); var paper = frozen(session);
        if (!"MANUAL".equals(paper.configuration().rule().getResultReleaseMode()) || !"SUBMITTED".equals(session.getStatus()))
            throw conflict("RESULT_RELEASE_NOT_ALLOWED", "Only a submitted MANUAL result can be released");
        if (session.getMetadata().containsKey("resultReleasedAt")) return;
        var metadata = mutable(session); metadata.put("resultReleasedAt", clock.instant().toString()); metadata.put("resultReleasedBy", administrator.toString());
        metadata.put("outboxNextAttemptAt", clock.instant().toString()); session.setMetadata(metadata); advance(session);
    }

    @Transactional
    public void processExpired(UUID id) {
        sessions.findByIdForUpdate(id).filter(s -> s.getDeletedAt() == null).ifPresent(s -> expireIfNeeded(s, frozen(s)));
    }

    @Transactional
    public void deliverResult(UUID id) {
        ExamSession session = sessions.findByIdForUpdate(id).orElseThrow(() -> missing("SESSION_NOT_FOUND")); var paper = frozen(session);
        if (!"PENDING".equals(session.getMetadata().get("outboxState")) || !released(session, paper)) return;
        Instant now = clock.instant();
        if (now.isBefore(Instant.parse((String) session.getMetadata().get("outboxNextAttemptAt")))) return;
        LearningResultEvent event = mapper.convertValue(session.getMetadata().get("resultEvent"), LearningResultEvent.class);
        if (!session.getId().equals(event.getSourceId()) || event.getEventId() == null
                || !session.getUserId().equals(event.getUserId()) || !paper.examId().equals(event.getTargetId())
                || !"EXAM".equals(event.getTargetType()) || !"EXAM_SESSION".equals(event.getSourceType()))
            throw conflict("SESSION_SNAPSHOT_INVALID", "The result event is inconsistent");
        if (event.getEventVersion() == null || event.getEventVersion() < 2) {
            event = historyEvent(session,paper,event);
            var upgraded=mutable(session);upgraded.put("resultEvent",json(event));
            upgraded.put("historySchemaVersion",2);session.setMetadata(upgraded);
        }
        var metadata = mutable(session); int tries = integer(metadata, "outboxAttempts") + 1;
        try {
            kafka.send("learning-results", session.getUserId().toString(), event).get(10, TimeUnit.SECONDS);
            metadata.put("outboxState", "DELIVERED"); metadata.put("outboxDeliveredAt", clock.instant().toString());
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            metadata.put("outboxNextAttemptAt", now.plusSeconds(Math.min(300, 1L << Math.min(tries, 8))).toString());
            log.warn("Official result delivery will retry: session={}, event={}, failure={}", id, event.getEventId(), failure.getClass().getSimpleName());
        }
        metadata.put("outboxAttempts", tries); session.setMetadata(metadata); sessions.save(session);
    }

    @Transactional
    public void backfillHistory(UUID id) {
        var session=sessions.findByIdForUpdate(id).orElseThrow(() -> missing("SESSION_NOT_FOUND"));
        if (session.getDeletedAt()!=null || !"SUBMITTED".equals(session.getStatus())) return;
        if (Integer.valueOf(2).equals(session.getMetadata().get("historySchemaVersion"))) return;
        var paper=frozen(session);
        var event=mapper.convertValue(session.getMetadata().get("resultEvent"),LearningResultEvent.class);
        if (event==null || event.getDurationSeconds()==null || event.getEventId()==null
                || !session.getId().equals(event.getSourceId()) || !session.getUserId().equals(event.getUserId())
                || !paper.examId().equals(event.getTargetId()) || session.getSubmittedAt()==null)
            throw conflict("SESSION_SNAPSHOT_INVALID","A valid persisted source result is required for backfill");
        event=historyEvent(session,paper,event);
        var metadata=mutable(session);metadata.put("resultEvent",json(event));metadata.put("historySchemaVersion",2);
        metadata.put("outboxState","PENDING");metadata.put("outboxAttempts",0);
        metadata.put("outboxNextAttemptAt", released(session,paper) ? clock.instant().toString()
                : "AFTER_EXAM_END".equals(paper.configuration().rule().getResultReleaseMode())
                    ? paper.examEndedAt().toString() : "9999-12-31T23:59:59Z");
        session.setMetadata(metadata);sessions.save(session);
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordHistoryBackfillFailure(UUID id,String errorCode) {
        sessions.findByIdForUpdate(id).ifPresent(session -> {
            var metadata=mutable(session);metadata.put("historyBackfillState","FAILED");
            metadata.put("historyBackfillError",errorCode);metadata.put("historyBackfillFailedAt",clock.instant().toString());
            session.setMetadata(metadata);sessions.save(session);
        });
    }
    private LearningResultEvent historyEvent(ExamSession session,FrozenOfficialSession paper,LearningResultEvent event) {
        var grade=grade(session);
        if (event.getScore()==null || event.getMaxScore()==null
                || event.getScore().compareTo(grade.score())!=0 || event.getMaxScore().compareTo(grade.maxScore())!=0
                || !(grade.passed() ? "PASSED" : "FAILED").equals(event.getResult()))
            throw conflict("SESSION_SNAPSHOT_INVALID","The result event differs from its persisted grade");
        event.setEventVersion(2);event.setActivityType("OFFICIAL_EXAM");
        event.setDisplayTitle((String)session.getMetadata().get("historyDisplayTitle"));
        event.setLicenseClass(paper.configuration().profile().licenseClass());
        event.setCorrectCount(grade.correctCount());event.setQuestionCount(paper.questions().size());
        event.setResultAvailable(true);event.setReviewAvailable(!"NONE".equals(paper.configuration().rule().getReviewMode()));
        event.setAttemptNo(session.getAttemptNo());event.setStartedAt(session.getStartedAt());event.setCompletedAt(session.getSubmittedAt());
        // Historical title remains null if it was never snapshotted. Do not read today's admin title.
        return event;
    }

    private void finish(ExamSession session, FrozenOfficialSession paper, String key, String reason) {
        if ("SUBMITTED".equals(session.getStatus())) return;
        var rows = orderedAnswers(session, paper); var grade = grader.grade(paper, rows, Boolean.TRUE.equals(session.getMetadata().get("proctoringFailed")));
        var indexed = rowMap(rows); grade.questions().forEach(g -> { var row = indexed.get(g.questionId()); row.setIsCorrect(g.correct()); row.setScore(g.score()); });
        // Keep the first HTTP response and database retries on the same PostgreSQL timestamp precision.
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant activeEnd = "READY".equals(session.getStatus()) ? Instant.parse((String) session.getMetadata().get("pausedAt"))
                : earlier(now, session.getExpiredAt());
        long pausedSeconds = session.getMetadata().get("totalPausedSeconds") instanceof Number n ? n.longValue() : 0;
        int activeSeconds = Math.toIntExact(Math.min(session.getDurationSeconds(),
                Math.max(0, Duration.between(session.getStartedAt(), activeEnd).getSeconds() - pausedSeconds)));
        session.setStatus("SUBMITTED"); session.setSubmittedAt(now); session.setIdempotencyKey(key);
        var metadata = mutable(session); metadata.put("gradingSnapshot", json(grade)); metadata.put("submissionReason", reason);
        // Stable event ID is stored in the same DB transaction as grading. Delivery is at least once.
        var event = LearningResultEvent.builder().eventId(UUID.randomUUID()).eventType("LEARNING_RESULT_CREATED").eventVersion(2).occurredAt(now)
                .userId(session.getUserId()).sourceType("EXAM_SESSION").sourceId(session.getId()).targetType("EXAM").targetId(paper.examId())
                .activityType("OFFICIAL_EXAM").displayTitle((String) metadata.get("historyDisplayTitle"))
                .licenseClass(paper.configuration().profile().licenseClass()).correctCount(grade.correctCount()).questionCount(rows.size())
                .resultAvailable(true).reviewAvailable(!"NONE".equals(paper.configuration().rule().getReviewMode()))
                .attemptNo(session.getAttemptNo()).result(grade.passed() ? "PASSED" : "FAILED").score(grade.score()).maxScore(grade.maxScore()).progress(100.0)
                .durationSeconds(activeSeconds)
                .startedAt(session.getStartedAt()).completedAt(now).resultSnapshot(Map.of("totalQuestions", rows.size(), "correctCount", grade.correctCount(),
                        "examId", paper.examId().toString(), "examVersionId", paper.examVersionId().toString(), "licenseClass", paper.configuration().profile().licenseClass()))
                .metadata(Map.of("submissionReason", reason)).build();
        metadata.put("historySchemaVersion",2);
        metadata.put("resultEvent", json(event)); metadata.put("outboxState", "PENDING"); metadata.put("outboxAttempts", 0);
        String mode = paper.configuration().rule().getResultReleaseMode();
        metadata.put("outboxNextAttemptAt", "MANUAL".equals(mode) ? "9999-12-31T23:59:59Z"
                : "AFTER_EXAM_END".equals(mode) ? paper.examEndedAt().toString() : now.toString());
        session.setMetadata(metadata); answers.saveAll(rows); advance(session);
    }

    private void expireIfNeeded(ExamSession session, FrozenOfficialSession paper) {
        if (!Set.of("IN_PROGRESS", "READY").contains(session.getStatus())) return;
        Instant now = clock.instant();
        Instant cutoff = "READY".equals(session.getStatus()) ? session.getExpiredAt()
                : earlier(session.getExpiredAt().plusSeconds(paper.configuration().rule().getGracePeriodSeconds()), paper.examEndedAt());
        if (now.isBefore(cutoff)) return;
        if (paper.configuration().rule().isAutoSubmit()) finish(session, paper, null, "TIME_EXPIRED");
        else { session.setStatus("EXPIRED"); advance(session); }
    }

    private void requireActive(ExamSession session, FrozenOfficialSession paper) {
        expireIfNeeded(session, paper);
        if (!"IN_PROGRESS".equals(session.getStatus()) || !clock.instant().isBefore(session.getExpiredAt()))
            throw conflict("SESSION_CLOSED", "The session is paused, submitted or past its answer deadline");
    }

    private void resumeExisting(ExamSession session, FrozenOfficialSession paper) {
        var rule = paper.configuration().rule();
        if (!"READY".equals(session.getStatus()) || !rule.isAllowPause() || !rule.isAllowResume()
                || !clock.instant().isBefore(session.getExpiredAt())) throw conflict("SESSION_NOT_RESUMABLE", "This paused session cannot resume");
        long seconds = number(session.getMetadata(), "pausedRemainingSeconds");
        if (seconds < 1) throw conflict("SESSION_NOT_RESUMABLE", "The paused timer is invalid");
        var metadata = mutable(session);
        long prior = metadata.get("totalPausedSeconds") instanceof Number n ? n.longValue() : 0;
        long pauseLength = Duration.between(Instant.parse((String) metadata.get("pausedAt")), clock.instant()).getSeconds();
        if (pauseLength < 0) throw conflict("SESSION_NOT_RESUMABLE", "The paused server time is inconsistent");
        metadata.put("totalPausedSeconds", Math.addExact(prior, pauseLength)); session.setMetadata(metadata);
        session.setExpiredAt(earlier(clock.instant().plusSeconds(seconds), paper.examEndedAt())); session.setStatus("IN_PROGRESS"); advance(session);
    }

    private ExamSession owned(UUID id, UUID user) {
        ExamSession session = sessions.findByIdForUpdate(id).orElseThrow(() -> missing("SESSION_NOT_FOUND"));
        if (session.getDeletedAt() != null) throw missing("SESSION_NOT_FOUND");
        if (user == null || !user.equals(session.getUserId())) throw new ExaminationException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only the session owner can access this attempt");
        return session;
    }

    private FrozenOfficialSession frozen(ExamSession session) {
        if (session.getMetadata() == null || !(session.getMetadata().get("officialSessionVersion") instanceof Number version) || version.intValue() != 1
                || session.getMetadata().get("officialPaper") == null) throw conflict("SESSION_SNAPSHOT_MISSING", "This older official attempt has no immutable paper; it requires administrative reconciliation");
        try {
            var paper = mapper.convertValue(session.getMetadata().get("officialPaper"), FrozenOfficialSession.class);
            validateFrozen(paper);
            if (!session.getExamVersionId().equals(paper.examVersionId()) || session.getExpiredAt() == null) throw new IllegalArgumentException();
            return paper;
        } catch (RuntimeException failure) { throw conflict("SESSION_SNAPSHOT_INVALID", "The private session snapshot is inconsistent"); }
    }

    private void validateFrozen(FrozenOfficialSession paper) {
        if (paper == null || paper.schemaVersion() != 1 || paper.examId() == null || paper.examVersionId() == null || paper.examEndedAt() == null
                || paper.configuration() == null || paper.configuration().rule() == null || paper.configuration().criteria() == null
                || paper.questions().size() != paper.configuration().policy().totalQuestions()
                || paper.sectionAssignments().size() != paper.questions().size()) throw conflict("SESSION_SNAPSHOT_INVALID", "A complete frozen paper is required");
        Set<UUID> unique = new HashSet<>(); Map<String, Integer> counts = new HashMap<>(), critical = new HashMap<>();
        Set<String> sections = new HashSet<>(); paper.configuration().structure().getSections().forEach(s -> sections.add(s.getCode()));
        for (var question : paper.questions()) {
            String section = paper.sectionAssignments().get(question.id());
            if (question.id() == null || !unique.add(question.id()) || !sections.contains(section) || question.content() == null || question.content().isBlank()
                    || question.contentVersion() == null || !question.contentVersion().matches("sha256:[0-9a-f]{64}")
                    || !Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(question.type()) || question.options().size() < 2
                    || question.options().stream().anyMatch(o -> o.id() == null || o.content() == null || o.content().isBlank())
                    || question.options().stream().map(FrozenExamQuestion.Option::id).distinct().count() != question.options().size()
                    || question.options().stream().filter(FrozenExamQuestion.Option::isCorrect).count() < 1
                    || !"MULTIPLE_CHOICE".equals(question.type()) && question.options().stream().filter(FrozenExamQuestion.Option::isCorrect).count() != 1)
                throw conflict("SESSION_SNAPSHOT_INVALID", "A frozen question or answer key is invalid");
            counts.merge(section, 1, Integer::sum); if (question.isCritical()) critical.merge(section, 1, Integer::sum);
        }
        for (var section : paper.configuration().structure().getSections()) {
            if (!Objects.equals(section.getQuestionCount(), counts.getOrDefault(section.getCode(), 0))
                    || section.getCriticalQuestionCount() != null && !Objects.equals(section.getCriticalQuestionCount(), critical.getOrDefault(section.getCode(), 0)))
                throw conflict("SESSION_SNAPSHOT_INVALID", "The frozen paper violates its section quota");
        }
        if (paper.questions().stream().filter(FrozenExamQuestion::isCritical).count() != paper.configuration().policy().criticalQuestionCount())
            throw conflict("SESSION_SNAPSHOT_INVALID", "The frozen paper violates its critical quota");
        OfficialSessionGrader.validateRounding(paper.configuration().structure().getSections(), paper.configuration().criteria().getCriteria());
    }

    private List<ExamSessionAnswer> orderedAnswers(ExamSession session, FrozenOfficialSession paper) {
        List<ExamSessionAnswer> rows = new ArrayList<>(answers.findByExamSessionId(session.getId()));
        rows.sort(Comparator.comparingInt(row -> integer(row.getDisplaySnapshot(), "position")));
        if (rows.size() != paper.questions().size()) throw conflict("SESSION_SNAPSHOT_INVALID", "Persisted answer rows are incomplete");
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            if (!paper.questions().get(i).id().equals(row.getQuestionId()) || integer(row.getDisplaySnapshot(), "position") != i + 1
                    || !Objects.equals(paper.sectionAssignments().get(row.getQuestionId()), row.getDisplaySnapshot().get("sectionCode")))
                throw conflict("SESSION_SNAPSHOT_INVALID", "Persisted answer rows do not match the frozen paper");
        }
        return rows;
    }

    private List<FrozenExamQuestion.Option> orderedOptions(ExamSessionAnswer row, FrozenExamQuestion question) {
        Object raw = row.getDisplaySnapshot().get("optionOrder");
        if (!(raw instanceof List<?> order) || order.size() != question.options().size()) throw conflict("SESSION_SNAPSHOT_INVALID", "The frozen option order is incomplete");
        Map<String, FrozenExamQuestion.Option> options = new HashMap<>(); question.options().forEach(o -> options.put(o.id().toString(), o));
        Set<Object> unique = new HashSet<>(order);
        if (unique.size() != order.size() || !options.keySet().containsAll(unique)) throw conflict("SESSION_SNAPSHOT_INVALID", "The frozen option order is invalid");
        return order.stream().map(id -> options.get(id.toString())).toList();
    }

    private Map<String, Object> normalize(Map<String, Object> input, FrozenExamQuestion question) {
        if (input == null || !Set.of("type", "value").containsAll(input.keySet()) || !(input.get("type") instanceof String type)
                || !input.containsKey("value")) throw bad("ANSWER_INVALID", "Use selectedAnswer with type and value");
        String expected = "MULTIPLE_CHOICE".equals(question.type()) ? "multiple_choice" : "single_choice";
        if (!expected.equalsIgnoreCase(type) && !("TRUE_FALSE".equals(question.type()) && "true_false".equalsIgnoreCase(type)))
            throw bad("ANSWER_INVALID", "The submitted answer type does not match the frozen question");
        Object value = input.get("value");
        if (value == null || value instanceof String empty && empty.isBlank()) return null;
        if (!(value instanceof String text)) throw bad("ANSWER_INVALID", "value must contain UUIDs as a string");
        Set<UUID> ids;
        try { ids = OfficialSessionGrader.selectedIds(Map.of("value", text)); }
        catch (ExaminationException failure) { throw bad("ANSWER_INVALID", "The submitted option IDs are invalid or duplicated"); }
        Set<UUID> valid = new HashSet<>(question.options().stream().map(FrozenExamQuestion.Option::id).toList());
        if (!valid.containsAll(ids) || !"MULTIPLE_CHOICE".equals(question.type()) && ids.size() != 1)
            throw bad("ANSWER_INVALID", "An option does not belong to the frozen question");
        return Map.of("type", expected, "value", ids.stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining(",")));
    }

    private void requireAccessible(ExamSession session, FrozenOfficialSession paper, ExamSessionAnswer target, Map<UUID, ExamSessionAnswer> rows) {
        var rule = paper.configuration().rule(); if ("FREE".equals(rule.getNavigationMode())) return;
        int current = integer(session.getMetadata(), "currentPosition"), position = integer(target.getDisplaySnapshot(), "position");
        if (position == current) return;
        var currentRow = rows.values().stream().filter(row -> integer(row.getDisplaySnapshot(), "position") == current).findFirst().orElseThrow();
        if (position > current || "NONE".equals(rule.getReviewMode()) || "CURRENT_SECTION".equals(rule.getReviewMode())
                && !Objects.equals(currentRow.getDisplaySnapshot().get("sectionCode"), target.getDisplaySnapshot().get("sectionCode")))
            throw conflict("NAVIGATION_NOT_ALLOWED", "This question is unavailable under the sequential review policy");
    }

    private List<FrozenExamQuestion> arrange(List<FrozenExamQuestion> input, Map<UUID, String> sections, PublishedExamSnapshot config) {
        var rule = config.rule(); var random = new java.security.SecureRandom();
        List<FrozenExamQuestion> result = new ArrayList<>();
        if (rule.isShuffleQuestionsAcrossSections()) { result.addAll(input); Collections.shuffle(result, random); return result; }
        List<Section> ordered = new ArrayList<>(config.structure().getSections()); ordered.sort(Comparator.comparing(Section::getOrder));
        if (rule.isShuffleSections()) Collections.shuffle(ordered, random);
        for (var section : ordered) {
            List<FrozenExamQuestion> group = new ArrayList<>(input.stream().filter(q -> section.getCode().equals(sections.get(q.id()))).toList());
            if (rule.isShuffleQuestionsWithinSection()) Collections.shuffle(group, random); result.addAll(group);
        }
        return result;
    }

    private boolean released(ExamSession session, FrozenOfficialSession paper) {
        return switch (paper.configuration().rule().getResultReleaseMode()) {
            case "IMMEDIATE", "AFTER_SUBMIT" -> "SUBMITTED".equals(session.getStatus());
            case "AFTER_EXAM_END" -> !clock.instant().isBefore(paper.examEndedAt());
            case "MANUAL" -> session.getMetadata().containsKey("resultReleasedAt");
            default -> false;
        };
    }
    private Instant releaseAt(FrozenOfficialSession paper) { return "AFTER_EXAM_END".equals(paper.configuration().rule().getResultReleaseMode()) ? paper.examEndedAt() : null; }
    private OfficialSessionGrader.Grade grade(ExamSession session) {
        var result = mapper.convertValue(session.getMetadata().get("gradingSnapshot"), OfficialSessionGrader.Grade.class);
        if (result == null || result.score() == null || result.maxScore() == null || result.questions() == null)
            throw conflict("SESSION_SNAPSHOT_INVALID", "The persisted grading snapshot is missing");
        return result;
    }
    private ExamResultSummary summary(OfficialSessionGrader.Grade grade, int total) {
        return ExamResultSummary.builder().score(grade.score()).result(grade.passed() ? "PASS" : "FAIL").correctQuestions(grade.correctCount())
                .totalQuestions(total).answeredQuestions(grade.answeredCount()).wrongQuestions(grade.answeredCount() - grade.correctCount())
                .unansweredQuestions(total - grade.answeredCount()).build();
    }
    private SubmitExamResponse submitted(ExamSession session, FrozenOfficialSession paper) {
        return SubmitExamResponse.builder().sessionId(session.getId()).status(session.getStatus()).submittedAt(session.getSubmittedAt())
                .version(session.getVersion()).resultPublished(released(session, paper)).build();
    }
    private ExamSessionResponse startResponse(ExamSession session, FrozenOfficialSession paper, boolean resumed) {
        return ExamSessionResponse.builder().id(session.getId()).sessionId(session.getId()).examId(paper.examId()).examVersionId(paper.examVersionId())
                .attemptNo(session.getAttemptNo()).status(session.getStatus()).startedAt(session.getStartedAt()).expiredAt(session.getExpiredAt())
                .durationSeconds(session.getDurationSeconds()).examRule(safeRule(paper.configuration().rule())).version(session.getVersion())
                .resumedExisting(resumed).serverNow(clock.instant()).build();
    }
    private PauseSessionResponse pauseResponse(ExamSession session) {
        return PauseSessionResponse.builder().sessionId(session.getId()).status(session.getStatus())
                .pausedAt(Instant.parse((String) session.getMetadata().get("pausedAt")))
                .remainingSeconds(Math.toIntExact(number(session.getMetadata(), "pausedRemainingSeconds"))).build();
    }
    private SessionProgressResponse progress(ExamSession session, List<ExamSessionAnswer> rows) {
        return SessionProgressResponse.builder().answeredQuestions(answered(rows)).totalQuestions(rows.size()).remainingSeconds(Math.toIntExact(remaining(session)))
                .version(session.getVersion()).currentPosition(integer(session.getMetadata(), "currentPosition")).status(session.getStatus()).serverNow(clock.instant())
                .markedQuestionIds(rows.stream().filter(row -> Boolean.TRUE.equals(row.getDisplaySnapshot().get("marked"))).map(ExamSessionAnswer::getQuestionId).toList()).build();
    }
    private ExamRuleResponse safeRule(ExamRule rule) {
        return ExamRuleResponse.builder().id(rule.getId()).title(rule.getTitle()).durationSeconds(rule.getDurationSeconds()).allowRetry(rule.isAllowRetry())
                .maxRetry(rule.getMaxRetry()).retryIntervalSeconds(rule.getRetryIntervalSeconds()).autoSubmit(rule.isAutoSubmit()).gracePeriodSeconds(rule.getGracePeriodSeconds())
                .navigationMode(rule.getNavigationMode()).allowSkip(rule.isAllowSkip()).reviewMode(rule.getReviewMode()).allowPause(rule.isAllowPause())
                .maxPauseCount(rule.getMaxPauseCount()).maxPauseDurationSeconds(rule.getMaxPauseDurationSeconds()).allowResume(rule.isAllowResume())
                .resumeTimeoutSeconds(rule.getResumeTimeoutSeconds()).resultReleaseMode(rule.getResultReleaseMode()).showAnswerAfterSubmit(rule.isShowAnswerAfterSubmit())
                .showExplanationAfterSubmit(rule.isShowExplanationAfterSubmit()).showQuestionScoreAfterSubmit(rule.isShowQuestionScoreAfterSubmit())
                .requireFullscreen(rule.isRequireFullscreen()).preventTabSwitch(rule.isPreventTabSwitch()).maxTabSwitchCount(rule.getMaxTabSwitchCount()).timeZone(rule.getTimeZone()).build();
    }
    private Map<String, Object> publicDisplay(ExamSessionAnswer row) {
        var display = row.getDisplaySnapshot(); return Map.of("position", display.get("position"), "sectionCode", display.get("sectionCode"),
                "sectionTitle", display.get("sectionTitle"), "marked", Boolean.TRUE.equals(display.get("marked")));
    }
    private long remaining(ExamSession session) {
        if ("READY".equals(session.getStatus())) return number(session.getMetadata(), "pausedRemainingSeconds");
        return "IN_PROGRESS".equals(session.getStatus()) ? Math.max(0, Duration.between(clock.instant(), session.getExpiredAt()).getSeconds()) : 0;
    }
    private long answered(List<ExamSessionAnswer> rows) { return rows.stream().filter(row -> row.getSelectedAnswer() != null).count(); }
    private Map<UUID, ExamSessionAnswer> rowMap(List<ExamSessionAnswer> rows) { Map<UUID, ExamSessionAnswer> result = new LinkedHashMap<>(); rows.forEach(row -> result.put(row.getQuestionId(), row)); return result; }
    private Map<String, Object> mutable(ExamSession session) { return new LinkedHashMap<>(session.getMetadata()); }
    private Map<String, Object> json(Object value) {
        return mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
    }
    private String explanation(Map<String, Object> explanation) { Object text = explanation.get("text"); return text instanceof String value ? value : mapper.valueToTree(explanation).toString(); }
    private void advance(ExamSession session) {
        if (Set.of("IN_PROGRESS", "READY").contains(session.getStatus())) {
            var paper = frozen(session); var metadata = mutable(session);
            Instant next = "READY".equals(session.getStatus()) ? session.getExpiredAt()
                    : earlier(session.getExpiredAt().plusSeconds(paper.configuration().rule().getGracePeriodSeconds()), paper.examEndedAt());
            metadata.put("nextTransitionAt", next.toString()); session.setMetadata(metadata);
        }
        session.setVersion(Math.addExact(session.getVersion(), 1)); sessions.save(session);
    }

    public ExamResultSummary historySummary(ExamSession session) {
        if (!"SUBMITTED".equals(session.getStatus())) return null;
        var paper = frozen(session);
        return released(session, paper) ? summary(grade(session), paper.questions().size()) : null;
    }
    private void requireVersion(ExamSession session, Integer expected) {
        if (expected == null || expected < 0) throw bad("VERSION_REQUIRED", "The current session version is required");
        if (!expected.equals(session.getVersion())) throw conflict("VERSION_CONFLICT", "Reload the persisted session before retrying an older save");
    }
    private long number(Map<String, Object> map, String key) {
        if (map == null || !(map.get(key) instanceof Number number)) throw conflict("SESSION_SNAPSHOT_INVALID", "The persisted session state is invalid");
        try { return new BigDecimal(number.toString()).longValueExact(); }
        catch (ArithmeticException failure) { throw conflict("SESSION_SNAPSHOT_INVALID", "The persisted session state is invalid"); }
    }
    private int integer(Map<String, Object> map, String key) { return Math.toIntExact(number(map, key)); }
    private Instant earlier(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
    private ExaminationException bad(String code, String message) { return new ExaminationException(HttpStatus.BAD_REQUEST, code, message); }
    private ExaminationException conflict(String code, String message) { return new ExaminationException(HttpStatus.CONFLICT, code, message); }
    private ExaminationException missing(String code) { return new ExaminationException(HttpStatus.NOT_FOUND, code, "The requested examination resource was not found"); }
}
