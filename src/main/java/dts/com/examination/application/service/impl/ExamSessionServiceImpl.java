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
}
