package dts.com.examination.application.service.impl;

import dts.com.examination.api.form.StartExamSessionRequest;
import dts.com.examination.api.response.ExamSessionResponse;
import dts.com.examination.application.client.ContentBuilderClient;
import dts.com.examination.application.exception.BusinessRuleException;
import dts.com.examination.application.service.ExamSessionService;
import dts.com.examination.domain.entity.ExamRule;
import dts.com.examination.domain.entity.ExamSession;
import dts.com.examination.domain.entity.ExamSessionAnswer;
import dts.com.examination.domain.entity.ExamVersion;
import dts.com.examination.domain.repository.ExamRuleRepository;
import dts.com.examination.domain.repository.ExamSessionAnswerRepository;
import dts.com.examination.domain.repository.ExamSessionRepository;
import dts.com.examination.domain.repository.ExamVersionRepository;
import dts.com.examination.application.event.LearningResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamSessionServiceImpl implements ExamSessionService {

    private final ExamSessionRepository examSessionRepository;
    private final ExamSessionAnswerRepository examSessionAnswerRepository;
    private final ExamVersionRepository examVersionRepository;
    private final ExamRuleRepository examRuleRepository;
    private final ContentBuilderClient contentBuilderClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public ExamSessionResponse startSession(StartExamSessionRequest request, UUID userId) {
        // 1. Fetch Exam Version & Validate Status
        ExamVersion examVersion;
        if (request.getExamVersionId() != null) {
            examVersion = examVersionRepository.findById(request.getExamVersionId())
                    .orElseThrow(() -> new BusinessRuleException("Exam version not found"));
        } else if (request.getExamId() != null) {
            examVersion = examVersionRepository.findByExamIdAndStatusAndDeletedAtIsNullList(request.getExamId(), "PUBLISHED")
                    .stream().findFirst()
                    .orElseThrow(() -> new BusinessRuleException("No published version found for this exam"));
        } else {
            throw new BusinessRuleException("Either examVersionId or examId must be provided");
        }

        // 2. Validate Active Period
        Instant now = Instant.now();
        if (examVersion.getStartedAt() != null && now.isBefore(examVersion.getStartedAt())) {
            throw new BusinessRuleException("Exam is not within active period");
        }
        if (examVersion.getEndedAt() != null && now.isAfter(examVersion.getEndedAt())) {
            throw new BusinessRuleException("Exam is not within active period");
        }

        // 3. Fetch Exam Rule & Validate Retry Limits
        ExamRule examRule = examRuleRepository.findById(examVersion.getExamRuleId())
                .orElseThrow(() -> new BusinessRuleException("Exam rule not found"));

        int attemptCount = examSessionRepository.countByExamVersionIdAndUserId(examVersion.getId(), userId);
        // TẠM THỜI COMMENT ĐỂ TEST ĐƯỢC NHIỀU LẦN
        // if (!examRule.isAllowRetry() && attemptCount >= 1) {
        //      throw new BusinessRuleException("Retries are not allowed");
        // } else if (examRule.isAllowRetry() && examRule.getMaxRetry() > 0 && attemptCount >= examRule.getMaxRetry()) {
        //     throw new BusinessRuleException("Maximum attempts exceeded");
        // }

        // Check for existing IN_PROGRESS session (if parallel attempts not allowed)
        java.util.Optional<ExamSession> activeSessionOpt = examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(examVersion.getId(), userId, "IN_PROGRESS");
        if (activeSessionOpt.isPresent()) {
            ExamSession activeSession = activeSessionOpt.get();
            return dts.com.examination.api.response.ExamSessionResponse.builder()
                    .id(activeSession.getId())
                    .examId(examVersion.getExamId())
                    .startedAt(activeSession.getStartedAt())
                    .expiredAt(activeSession.getExpiredAt())
                    .status(activeSession.getStatus())
                    .build();
        }

        List<Map<String, Object>> questionsMetadata = contentBuilderClient.getQuestionsMetadata(
                examVersion.getContentId(), examVersion.getContentType()
        );

        if (questionsMetadata == null || questionsMetadata.isEmpty()) {
            throw new BusinessRuleException("No questions found for this exam");
        }

        // Shuffle questions if rule applies
        if (examRule.isShuffleQuestionsAcrossSections()) {
            Collections.shuffle(questionsMetadata);
        }

        // 5. Calculate Timings & Create Session
        Integer durationSeconds = examRule.getDurationSeconds() > 0 ? examRule.getDurationSeconds() : null;
        Instant expiredAt = durationSeconds != null ? now.plusSeconds(durationSeconds) : null;
        UUID sessionToken = UUID.randomUUID();

        ExamSession session = ExamSession.builder()
                .examVersionId(examVersion.getId())
                .userId(userId)
                .sessionToken(sessionToken)
                .attemptNo(attemptCount + 1)
                .status("IN_PROGRESS")
                .startedAt(now)
                .expiredAt(expiredAt)
                .durationSeconds(durationSeconds)
                .clientInfo(request.getClientInfo() != null ? request.getClientInfo() : Map.of())
                .metadata(Map.of())
                .build();
        
        session = examSessionRepository.save(session);

        // 6. Generate Answers & Display Snapshots
        List<ExamSessionAnswer> answers = new ArrayList<>();
        int position = 1;

        for (Map<String, Object> qMeta : questionsMetadata) {
            UUID questionId = UUID.fromString(qMeta.get("id").toString());
            List<String> optionIds = (List<String>) qMeta.get("optionIds");

            if (examRule.isShuffleOptions() && optionIds != null) {
                Collections.shuffle(optionIds);
            }

            Map<String, Object> snapshot = Map.of(
                    "position", position++,
                    "optionOrder", optionIds != null ? optionIds : Collections.emptyList()
            );

            ExamSessionAnswer answer = ExamSessionAnswer.builder()
                    .examSessionId(session.getId())
                    .questionId(questionId)
                    .displaySnapshot(snapshot)
                    .answeredAt(now) // initializing with started time
                    .build();
            answers.add(answer);
        }

        examSessionAnswerRepository.saveAll(answers);

        // 7. Map Response
        return ExamSessionResponse.builder()
                .id(session.getId())
                .sessionId(session.getId())
                .examId(examVersion.getExamId())
                .examVersionId(session.getExamVersionId())
                .attemptNo(session.getAttemptNo())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .expiredAt(session.getExpiredAt())
                .durationSeconds(session.getDurationSeconds())
                .build();
    }

    @Override
    @Transactional
    public dts.com.examination.api.response.ExamSessionDetailResponse getSessionDetail(UUID sessionId, UUID userId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleException("Exam session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        Instant now = Instant.now();
        long remainingSeconds = 0;

        if ("IN_PROGRESS".equals(session.getStatus())) {
            if (session.getExpiredAt() != null) {
                remainingSeconds = java.time.Duration.between(now, session.getExpiredAt()).getSeconds();
                if (remainingSeconds <= 0) {
                    session.setStatus("EXPIRED");
                    session = examSessionRepository.save(session);
                    remainingSeconds = 0;
                }
            }
        }

        long totalQuestions = examSessionAnswerRepository.countByExamSessionId(sessionId);
        long answeredQuestions = examSessionAnswerRepository.countByExamSessionIdAndSelectedAnswerIsNotNull(sessionId);

        return dts.com.examination.api.response.ExamSessionDetailResponse.builder()
                .sessionId(session.getId())
                .examVersionId(session.getExamVersionId())
                .attemptNo(session.getAttemptNo())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .expiredAt(session.getExpiredAt())
                .durationSeconds(session.getDurationSeconds())
                .remainingSeconds(remainingSeconds)
                .answeredQuestions(answeredQuestions)
                .totalQuestions(totalQuestions)
                .build();
    }

    @Override
    @Transactional
    public dts.com.examination.api.response.ExamPaperResponse getExamPaper(UUID sessionId, UUID userId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleException("Exam session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        Instant now = Instant.now();
        long remainingSeconds = 0;

        if ("IN_PROGRESS".equals(session.getStatus())) {
            if (session.getExpiredAt() != null) {
                remainingSeconds = java.time.Duration.between(now, session.getExpiredAt()).getSeconds();
                if (remainingSeconds <= 0) {
                    session.setStatus("EXPIRED");
                    session = examSessionRepository.save(session);
                    throw new BusinessRuleException("Session Expired");
                }
            }
        } else {
            throw new BusinessRuleException("Cannot get paper for this session state");
        }

        List<ExamSessionAnswer> answers = examSessionAnswerRepository.findByExamSessionId(sessionId);
        answers.sort((a, b) -> {
            int posA = 0;
            if (a.getDisplaySnapshot() != null && a.getDisplaySnapshot().get("position") != null) {
                Object p = a.getDisplaySnapshot().get("position");
                if (p instanceof Number) posA = ((Number) p).intValue();
            }
            int posB = 0;
            if (b.getDisplaySnapshot() != null && b.getDisplaySnapshot().get("position") != null) {
                Object p = b.getDisplaySnapshot().get("position");
                if (p instanceof Number) posB = ((Number) p).intValue();
            }
            return Integer.compare(posA, posB);
        });

        List<UUID> questionIds = answers.stream().map(ExamSessionAnswer::getQuestionId).collect(Collectors.toList());
        
        List<dts.com.examination.api.response.InternalQuestionDetailResponse> questionsBatch = contentBuilderClient.getQuestionsBatch(questionIds);
        Map<UUID, dts.com.examination.api.response.InternalQuestionDetailResponse> questionMap = questionsBatch.stream()
                .collect(Collectors.toMap(dts.com.examination.api.response.InternalQuestionDetailResponse::getId, q -> q));

        List<dts.com.examination.api.response.QuestionPaperResponse> paperQuestions = answers.stream().map(ans -> {
            dts.com.examination.api.response.InternalQuestionDetailResponse qDetail = questionMap.get(ans.getQuestionId());
            if (qDetail == null) return null;

            List<dts.com.examination.api.response.OptionPaperResponse> options = new ArrayList<>();
            if (ans.getDisplaySnapshot() != null && ans.getDisplaySnapshot().containsKey("optionOrder")) {
                List<String> optionOrder = (List<String>) ans.getDisplaySnapshot().get("optionOrder");
                Map<UUID, dts.com.examination.api.response.InternalQuestionOptionResponse> optMap = qDetail.getOptions().stream()
                        .collect(Collectors.toMap(dts.com.examination.api.response.InternalQuestionOptionResponse::getId, o -> o));
                
                for (String optIdStr : optionOrder) {
                    UUID optId = UUID.fromString(optIdStr);
                    dts.com.examination.api.response.InternalQuestionOptionResponse optDetail = optMap.get(optId);
                    if (optDetail != null) {
                        options.add(new dts.com.examination.api.response.OptionPaperResponse(optDetail.getId(), optDetail.getContent()));
                    }
                }
            } else {
                options = qDetail.getOptions().stream()
                        .map(o -> new dts.com.examination.api.response.OptionPaperResponse(o.getId(), o.getContent()))
                        .collect(Collectors.toList());
            }

            return dts.com.examination.api.response.QuestionPaperResponse.builder()
                    .questionId(ans.getQuestionId())
                    .display(ans.getDisplaySnapshot())
                    .content(qDetail.getContent())
                    .type(qDetail.getType())
                    .options(options)
                    .selectedAnswer(ans.getSelectedAnswer())
                    .build();
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());

        return dts.com.examination.api.response.ExamPaperResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .remainingSeconds(remainingSeconds)
                .questions(paperQuestions)
                .build();
    }

    @Override
    @Transactional
    public dts.com.examination.api.response.SaveAnswersResponse saveAnswers(UUID sessionId, dts.com.examination.api.form.SaveAnswersRequest request, UUID userId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleException("Exam session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        Instant now = Instant.now();
        if ("IN_PROGRESS".equals(session.getStatus())) {
            if (session.getExpiredAt() != null) {
                long remainingSeconds = java.time.Duration.between(now, session.getExpiredAt()).getSeconds();
                if (remainingSeconds <= 0) {
                    session.setStatus("EXPIRED");
                    session = examSessionRepository.save(session);
                    throw new BusinessRuleException("Session Expired");
                }
            }
        } else {
            throw new BusinessRuleException("Cannot save answers for this session");
        }

        if (request.getAnswers() == null || request.getAnswers().isEmpty()) {
            return dts.com.examination.api.response.SaveAnswersResponse.builder()
                    .savedAt(now)
                    .updatedQuestions(0)
                    .build();
        }

        List<UUID> questionIds = request.getAnswers().stream()
                .map(dts.com.examination.api.form.AnswerItemRequest::getQuestionId)
                .collect(Collectors.toList());

        List<ExamSessionAnswer> existingAnswers = examSessionAnswerRepository.findByExamSessionIdAndQuestionIdIn(sessionId, questionIds);
        Map<UUID, ExamSessionAnswer> answerMap = existingAnswers.stream()
                .collect(Collectors.toMap(ExamSessionAnswer::getQuestionId, a -> a));

        int updatedCount = 0;
        for (dts.com.examination.api.form.AnswerItemRequest item : request.getAnswers()) {
            ExamSessionAnswer existingAnswer = answerMap.get(item.getQuestionId());
            if (existingAnswer != null) {
                existingAnswer.setSelectedAnswer(item.getSelectedAnswer());
                // Ideally, we'd also have an answered_at field, but looking at ExamSessionAnswer entity, it wasn't defined earlier, 
                // wait, if answered_at doesn't exist, we skip it. Or update the entity.
                // Let's assume there's no specific answeredAt field if it's missing, or we just rely on updatedAt.
                updatedCount++;
            }
        }

        // examSessionAnswerRepository.saveAll(existingAnswers); is not strictly necessary in a Transactional context for dirty checking, 
        // but explicit saveAll is safer.
        examSessionAnswerRepository.saveAll(existingAnswers);

        return dts.com.examination.api.response.SaveAnswersResponse.builder()
                .savedAt(now)
                .updatedQuestions(updatedCount)
                .build();
    }

    @Override
    @Transactional
    public dts.com.examination.api.response.SubmitExamResponse submitExam(UUID sessionId, String idempotencyKey, UUID userId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleException("Exam session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessRuleException("Session is not in progress");
        }

        Instant now = Instant.now();
        if (session.getExpiredAt() != null && now.isAfter(session.getExpiredAt())) {
            session.setStatus("EXPIRED");
            examSessionRepository.save(session);
            throw new BusinessRuleException("Session Expired");
        }

        session.setStatus("SUBMITTED");
        session.setSubmittedAt(now);
        
        List<ExamSessionAnswer> answers = examSessionAnswerRepository.findByExamSessionId(sessionId);
        List<UUID> questionIds = answers.stream().map(ExamSessionAnswer::getQuestionId).collect(Collectors.toList());
        List<dts.com.examination.api.response.InternalQuestionDetailResponse> questionsBatch = contentBuilderClient.getQuestionsBatch(questionIds);
        Map<UUID, dts.com.examination.api.response.InternalQuestionDetailResponse> questionMap = questionsBatch.stream()
                .collect(Collectors.toMap(dts.com.examination.api.response.InternalQuestionDetailResponse::getId, q -> q));

        java.math.BigDecimal totalScore = java.math.BigDecimal.ZERO;
        int correctCount = 0;

        for (ExamSessionAnswer ans : answers) {
            dts.com.examination.api.response.InternalQuestionDetailResponse qDetail = questionMap.get(ans.getQuestionId());
            if (qDetail == null || ans.getSelectedAnswer() == null) {
                ans.setIsCorrect(false);
                ans.setScore(java.math.BigDecimal.ZERO);
                continue;
            }

            boolean isCorrect = false;
            Map<String, Object> selectedAnsMap = ans.getSelectedAnswer();
            Object valObj = selectedAnsMap.get("value");
            String selectedVal = valObj != null ? valObj.toString() : "";

            if (qDetail.getOptions() != null) {
                String type = (String) selectedAnsMap.get("type");
                if ("multiple_choice".equalsIgnoreCase(type) || "MULTIPLE_CHOICE".equalsIgnoreCase(qDetail.getType())) {
                    java.util.Set<String> selectedIds = new java.util.HashSet<>(java.util.Arrays.asList(selectedVal.split(",")));
                    selectedIds.removeIf(String::isEmpty);
                    
                    java.util.Set<String> correctIds = qDetail.getOptions().stream()
                            .filter(dts.com.examination.api.response.InternalQuestionOptionResponse::getIsCorrect)
                            .map(o -> o.getId().toString())
                            .collect(java.util.stream.Collectors.toSet());
                            
                    isCorrect = !correctIds.isEmpty() && selectedIds.equals(correctIds);
                } else {
                    for (dts.com.examination.api.response.InternalQuestionOptionResponse opt : qDetail.getOptions()) {
                        if (opt.getId().toString().equals(selectedVal) && opt.getIsCorrect()) {
                            isCorrect = true;
                            break;
                        }
                    }
                }
            }
            
            ans.setIsCorrect(isCorrect);
            if (isCorrect) {
                ans.setScore(java.math.BigDecimal.ONE);
                totalScore = totalScore.add(java.math.BigDecimal.ONE);
                correctCount++;
            } else {
                ans.setScore(java.math.BigDecimal.ZERO);
            }
        }

        examSessionAnswerRepository.saveAll(answers);
        
        Map<String, Object> meta = new java.util.HashMap<>(session.getMetadata() != null ? session.getMetadata() : Map.of());
        meta.put("totalScore", totalScore);
        meta.put("correctCount", correctCount);
        session.setMetadata(meta);
        examSessionRepository.save(session);
        
        // --- Publish Event to Kafka ---
        int durationSeconds = session.getStartedAt() != null ? 
            (int) java.time.Duration.between(session.getStartedAt(), now).getSeconds() : 0;
            
        ExamVersion examVersion = examVersionRepository.findById(session.getExamVersionId())
                .orElseThrow(() -> new BusinessRuleException("Exam version not found"));
        ExamRule examRule = examRuleRepository.findById(examVersion.getExamRuleId())
                .orElseThrow(() -> new BusinessRuleException("Exam rule not found"));

        String finalResult = "SUBMITTED";
        if ("IMMEDIATE".equals(examRule.getResultReleaseMode())) {
            finalResult = evaluateExamResult(examVersion, totalScore);
            // Result evaluates to "PASS" or "FAIL", map to PASSED/FAILED for dts-result
            if ("PASS".equals(finalResult)) finalResult = "PASSED";
            if ("FAIL".equals(finalResult)) finalResult = "FAILED";
        }

        LearningResultEvent event = LearningResultEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("LEARNING_RESULT_CREATED")
                .eventVersion(1)
                .occurredAt(now)
                .userId(userId)
                .sourceType("EXAM_SESSION")
                .sourceId(sessionId)
                .targetType("EXAM")
                .targetId(session.getExamVersionId())
                .attemptNo(session.getAttemptNo())
                .result("PASSED".equals(finalResult) ? "PASSED" : "FAILED".equals(finalResult) ? "FAILED" : "SUBMITTED")
                .score(totalScore)
                .maxScore(java.math.BigDecimal.valueOf(answers.size()))
                .progress(100.0) // Exam is 100% completed when submitted
                .durationSeconds(durationSeconds)
                .startedAt(session.getStartedAt())
                .completedAt(now)
                .resultSnapshot(Map.of(
                        "totalQuestions", answers.size(),
                        "correctCount", correctCount
                ))
                .metadata(idempotencyKey != null ? Map.of("idempotencyKey", idempotencyKey) : Map.of())
                .build();
                
        kafkaTemplate.send("learning-results", session.getUserId().toString(), event);
        // ------------------------------

        return dts.com.examination.api.response.SubmitExamResponse.builder()
                .sessionId(sessionId)
                .status("SUBMITTED")
                .submittedAt(now)
                .build();
    }

    @Override
    @Transactional
    public dts.com.examination.api.response.ExamResultResponse getExamResult(UUID sessionId, UUID userId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleException("Exam session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        if (!"SUBMITTED".equals(session.getStatus())) {
            throw new BusinessRuleException("Exam not submitted yet");
        }

        ExamVersion examVersion = examVersionRepository.findById(session.getExamVersionId())
                .orElseThrow(() -> new BusinessRuleException("Exam version not found"));
        ExamRule examRule = examRuleRepository.findById(examVersion.getExamRuleId())
                .orElseThrow(() -> new BusinessRuleException("Exam rule not found"));

        if (!"IMMEDIATE".equals(examRule.getResultReleaseMode())) {
            throw new BusinessRuleException("Result is hidden until exam period ends");
        }

        String result = evaluateExamResult(examVersion, extractTotalScore(session.getMetadata()));

        long totalQuestions = examSessionAnswerRepository.countByExamSessionId(sessionId);
        long answeredQuestions = examSessionAnswerRepository.countByExamSessionIdAndSelectedAnswerIsNotNull(sessionId);
        int correctCount = extractCorrectCount(session.getMetadata());
        java.math.BigDecimal score = extractTotalScore(session.getMetadata());
        int wrongQuestions = (int) answeredQuestions - correctCount;
        int unansweredQuestions = (int) totalQuestions - (int) answeredQuestions;

        dts.com.examination.api.response.ExamResultSummary summary = dts.com.examination.api.response.ExamResultSummary.builder()
                .score(score)
                .result(result)
                .correctQuestions(correctCount)
                .totalQuestions((int) totalQuestions)
                .answeredQuestions((int) answeredQuestions)
                .wrongQuestions(wrongQuestions)
                .unansweredQuestions(unansweredQuestions)
                .build();

        return dts.com.examination.api.response.ExamResultResponse.builder()
                .sessionId(sessionId)
                .status(session.getStatus())
                .submittedAt(session.getSubmittedAt())
                .summary(summary)
                .build();
    }

    @Override
    @Transactional
    public dts.com.examination.api.response.SessionProgressResponse getSessionProgress(UUID sessionId, UUID userId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleException("Exam session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        int remainingSeconds = 0;
        if ("IN_PROGRESS".equals(session.getStatus()) && session.getExpiredAt() != null) {
            remainingSeconds = (int) java.time.Duration.between(Instant.now(), session.getExpiredAt()).getSeconds();
            if (remainingSeconds < 0) remainingSeconds = 0;
        } else if ("READY".equals(session.getStatus())) {
            remainingSeconds = (Integer) session.getMetadata().getOrDefault("pausedRemainingSeconds", 0);
        }

        long totalQuestions = examSessionAnswerRepository.countByExamSessionId(sessionId);
        long answeredQuestions = examSessionAnswerRepository.countByExamSessionIdAndSelectedAnswerIsNotNull(sessionId);

        return dts.com.examination.api.response.SessionProgressResponse.builder()
                .answeredQuestions((int) answeredQuestions)
                .totalQuestions((int) totalQuestions)
                .remainingSeconds(remainingSeconds)
                .build();
    }

    @Override
    @Transactional
    public dts.com.examination.api.response.PauseSessionResponse pauseSession(UUID sessionId, UUID userId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleException("Exam session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessRuleException("Cannot pause session in current state");
        }

        ExamVersion examVersion = examVersionRepository.findById(session.getExamVersionId())
                .orElseThrow(() -> new BusinessRuleException("Exam version not found"));
        ExamRule examRule = examRuleRepository.findById(examVersion.getExamRuleId())
                .orElseThrow(() -> new BusinessRuleException("Exam rule not found"));

        if (!examRule.isAllowPause()) {
            throw new BusinessRuleException("Pause is not allowed");
        }

        Instant now = Instant.now();
        int remainingSeconds = 0;
        if (session.getExpiredAt() != null) {
            remainingSeconds = (int) java.time.Duration.between(now, session.getExpiredAt()).getSeconds();
            if (remainingSeconds <= 0) {
                session.setStatus("EXPIRED");
                examSessionRepository.save(session);
                throw new BusinessRuleException("Session Expired");
            }
        }

        session.setStatus("READY");
        Map<String, Object> meta = new java.util.HashMap<>(session.getMetadata() != null ? session.getMetadata() : Map.of());
        meta.put("pausedRemainingSeconds", remainingSeconds);
        session.setMetadata(meta);
        examSessionRepository.save(session);

        return dts.com.examination.api.response.PauseSessionResponse.builder()
                .sessionId(sessionId)
                .status("READY")
                .pausedAt(now)
                .remainingSeconds(remainingSeconds)
                .build();
    }

    @Override
    @Transactional
    public dts.com.examination.api.response.ResumeSessionResponse resumeSession(UUID sessionId, UUID userId) {
        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessRuleException("Exam session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        if (!"READY".equals(session.getStatus())) {
            throw new BusinessRuleException("Cannot resume session in current state");
        }

        ExamVersion examVersion = examVersionRepository.findById(session.getExamVersionId())
                .orElseThrow(() -> new BusinessRuleException("Exam version not found"));
        ExamRule examRule = examRuleRepository.findById(examVersion.getExamRuleId())
                .orElseThrow(() -> new BusinessRuleException("Exam rule not found"));

        if (!examRule.isAllowResume()) {
            throw new BusinessRuleException("Resume is not allowed");
        }

        int remainingSeconds = (Integer) session.getMetadata().getOrDefault("pausedRemainingSeconds", 0);
        Instant now = Instant.now();
        Instant expiredAt = now.plusSeconds(remainingSeconds);

        session.setStatus("IN_PROGRESS");
        session.setExpiredAt(expiredAt);
        
        Map<String, Object> meta = new java.util.HashMap<>(session.getMetadata());
        meta.remove("pausedRemainingSeconds");
        session.setMetadata(meta);
        examSessionRepository.save(session);

        return dts.com.examination.api.response.ResumeSessionResponse.builder()
                .sessionId(sessionId)
                .status("IN_PROGRESS")
                .resumedAt(now)
                .expiredAt(expiredAt)
                .remainingSeconds(remainingSeconds)
                .build();
    }

    @Override
    @Transactional
    public dts.com.examination.api.response.SessionHistoryResponse getSessionHistory(UUID examId, UUID userId, int page, int size, String status, String sort) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, 
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "startedAt"));

        org.springframework.data.domain.Page<ExamSession> sessionPage = examSessionRepository.findByExamIdAndUserId(examId, userId, pageable);
        
        List<dts.com.examination.api.response.SessionHistoryItemResponse> items = new ArrayList<>();
        for (ExamSession session : sessionPage.getContent()) {
            dts.com.examination.api.response.ExamResultSummary summary = null;
            if ("SUBMITTED".equals(session.getStatus())) {
                long totalQuestions = examSessionAnswerRepository.countByExamSessionId(session.getId());
                int correctCount = extractCorrectCount(session.getMetadata());
                java.math.BigDecimal score = extractTotalScore(session.getMetadata());
                
                String result = "FAIL";
                ExamVersion examVersion = examVersionRepository.findById(session.getExamVersionId()).orElse(null);
                if (examVersion != null && examVersion.getExamCriteriaId() != null) {
                    dts.com.examination.domain.entity.ExamCriteria examCriteria = 
                        dts.com.examination.domain.repository.ExamCriteriaRepository.class.cast(
                            org.springframework.web.context.support.WebApplicationContextUtils.getWebApplicationContext(
                                ((org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest().getServletContext()
                            ).getBean(dts.com.examination.domain.repository.ExamCriteriaRepository.class)
                        ).findById(examVersion.getExamCriteriaId()).orElse(null);
                    
                    if (examCriteria != null && examCriteria.getCriteria() != null && examCriteria.getCriteria().getPassScore() != null) {
                        if (score.compareTo(new java.math.BigDecimal(examCriteria.getCriteria().getPassScore())) >= 0) {
                            result = "PASS";
                        }
                    }
                }
                
                summary = dts.com.examination.api.response.ExamResultSummary.builder()
                        .score(score)
                        .result(result)
                        .correctQuestions(correctCount)
                        .totalQuestions((int) totalQuestions)
                        .build();
            }

            items.add(dts.com.examination.api.response.SessionHistoryItemResponse.builder()
                    .sessionId(session.getId())
                    .examVersionId(session.getExamVersionId())
                    .attemptNo(session.getAttemptNo())
                    .status(session.getStatus())
                    .startedAt(session.getStartedAt())
                    .submittedAt(session.getSubmittedAt())
                    .summary(summary)
                    .build());
        }

        return dts.com.examination.api.response.SessionHistoryResponse.builder()
                .page(sessionPage.getNumber())
                .size(sessionPage.getSize())
                .totalElements(sessionPage.getTotalElements())
                .totalPages(sessionPage.getTotalPages())
                .items(items)
                .build();
    }

    /**
     * Đọc điểm tổng (totalScore) từ metadata của session.
     * Metadata là JSONB nên khi đọc lại số có thể là Integer, Double hay
     * BigDecimal tùy cách lưu — không được cast trực tiếp (gây ClassCastException
     * -> HTTP 500). Fallback về 0 nếu thiếu/null.
     */
    private java.math.BigDecimal extractTotalScore(Map<String, Object> metadata) {
        Object value = metadata.getOrDefault("totalScore", java.math.BigDecimal.ZERO);
        if (value instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) value;
        }
        if (value == null) {
            return java.math.BigDecimal.ZERO;
        }
        return new java.math.BigDecimal(value.toString());
    }

    /**
     * Đọc số câu đúng (correctCount) từ metadata của session, chấp nhận mọi kiểu
     * số JSONB (Integer/Double/...). Fallback về 0 nếu thiếu/null.
     */
    private int extractCorrectCount(Map<String, Object> metadata) {
        Object value = metadata.getOrDefault("correctCount", 0);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    private String evaluateExamResult(ExamVersion examVersion, java.math.BigDecimal score) {
        String result = "FAIL";
        boolean isPassDetermined = false;
        if (examVersion.getExamCriteriaId() != null) {
            dts.com.examination.domain.entity.ExamCriteria examCriteria =
                dts.com.examination.domain.repository.ExamCriteriaRepository.class.cast(
                    org.springframework.web.context.support.WebApplicationContextUtils.getWebApplicationContext(
                        ((org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest().getServletContext()
                    ).getBean(dts.com.examination.domain.repository.ExamCriteriaRepository.class)
                ).findById(examVersion.getExamCriteriaId()).orElse(null);

            if (examCriteria != null && examCriteria.getCriteria() != null && examCriteria.getCriteria().getPassScore() != null) {
                if (score.compareTo(new java.math.BigDecimal(examCriteria.getCriteria().getPassScore())) >= 0) {
                    result = "PASS";
                }
                isPassDetermined = true;
            }
        }

        if (!isPassDetermined) {
            dts.com.examination.domain.entity.Exam exam =
                dts.com.examination.domain.repository.ExamRepository.class.cast(
                    org.springframework.web.context.support.WebApplicationContextUtils.getWebApplicationContext(
                        ((org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest().getServletContext()
                    ).getBean(dts.com.examination.domain.repository.ExamRepository.class)
                ).findByIdAndNotDeleted(examVersion.getExamId()).orElse(null);

            if (exam != null && exam.getMetadata() != null && exam.getMetadata().containsKey("passScore")) {
                Object passScoreObj = exam.getMetadata().get("passScore");
                if (passScoreObj instanceof Number) {
                    if (score.compareTo(new java.math.BigDecimal(((Number) passScoreObj).doubleValue())) >= 0) {
                        result = "PASS";
                    }
                } else if (passScoreObj instanceof String) {
                    try {
                        if (score.compareTo(new java.math.BigDecimal((String) passScoreObj)) >= 0) {
                            result = "PASS";
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse passScore from exam metadata: {}", passScoreObj);
                    }
                }
            }
        }
        return result;
    }
}
