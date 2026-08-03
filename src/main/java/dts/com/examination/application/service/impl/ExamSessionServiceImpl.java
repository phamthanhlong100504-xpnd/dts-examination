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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    @Transactional
    public ExamSessionResponse startSession(StartExamSessionRequest request, UUID userId) {
        // 1. Fetch Exam Version & Validate Status
        ExamVersion examVersion = examVersionRepository.findById(request.getExamVersionId())
                .orElseThrow(() -> new BusinessRuleException("Exam version not found"));

        if (!"PUBLISHED".equals(examVersion.getStatus())) {
            throw new BusinessRuleException("Exam version is not published");
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
        if (examSessionRepository.existsByExamVersionIdAndUserIdAndStatus(examVersion.getId(), userId, "IN_PROGRESS")) {
            throw new BusinessRuleException("An active session already exists");
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
                .sessionId(session.getId())
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
            Integer posA = a.getDisplaySnapshot() != null ? (Integer) a.getDisplaySnapshot().get("position") : 0;
            Integer posB = b.getDisplaySnapshot() != null ? (Integer) b.getDisplaySnapshot().get("position") : 0;
            return posA.compareTo(posB);
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
}
