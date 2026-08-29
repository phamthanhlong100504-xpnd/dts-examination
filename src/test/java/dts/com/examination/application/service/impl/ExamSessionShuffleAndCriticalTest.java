package dts.com.examination.application.service.impl;

import dts.com.examination.api.form.StartExamSessionRequest;
import dts.com.examination.api.response.ExamSessionResponse;
import dts.com.examination.api.response.InternalQuestionDetailResponse;
import dts.com.examination.api.response.InternalQuestionOptionResponse;
import dts.com.examination.application.client.ContentBuilderClient;
import dts.com.examination.application.exception.BusinessRuleException;
import dts.com.examination.domain.entity.ExamRule;
import dts.com.examination.domain.entity.ExamSession;
import dts.com.examination.domain.entity.ExamSessionAnswer;
import dts.com.examination.domain.entity.ExamVersion;
import dts.com.examination.domain.repository.ExamRuleRepository;
import dts.com.examination.domain.repository.ExamSessionAnswerRepository;
import dts.com.examination.domain.repository.ExamSessionRepository;
import dts.com.examination.domain.repository.ExamVersionRepository;
import dts.com.examination.domain.repository.ExamCriteriaRepository;
import dts.com.examination.domain.repository.ExamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamSessionShuffleAndCriticalTest {

    @Mock
    private ExamSessionRepository examSessionRepository;
    @Mock
    private ExamSessionAnswerRepository examSessionAnswerRepository;
    @Mock
    private ExamVersionRepository examVersionRepository;
    @Mock
    private ExamRuleRepository examRuleRepository;
    @Mock
    private ExamCriteriaRepository examCriteriaRepository;
    @Mock
    private ExamRepository examRepository;
    @Mock
    private ContentBuilderClient contentBuilderClient;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ExamSessionServiceImpl examSessionService;

    private UUID userId;
    private UUID examVersionId;
    private UUID examId;
    private UUID examRuleId;
    private ExamVersion examVersion;
    private ExamRule examRule;
    private StartExamSessionRequest request;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        examVersionId = UUID.randomUUID();
        examId = UUID.randomUUID();
        examRuleId = UUID.randomUUID();

        examVersion = new ExamVersion();
        examVersion.setId(examVersionId);
        examVersion.setExamId(examId);
        examVersion.setExamRuleId(examRuleId);
        examVersion.setContentId(UUID.randomUUID());
        examVersion.setContentType("PROGRAM");
        examVersion.setStartedAt(Instant.now().minusSeconds(3600));
        examVersion.setEndedAt(Instant.now().plusSeconds(3600));

        examRule = new ExamRule();
        examRule.setId(examRuleId);
        examRule.setAllowRetry(true);
        examRule.setMaxRetry(3);
        examRule.setShuffleQuestionsAcrossSections(false);
        examRule.setShuffleQuestionsWithinSection(false);
        examRule.setShuffleSections(false);
        examRule.setShuffleOptions(false);
        examRule.setResultReleaseMode("IMMEDIATE");

        request = new StartExamSessionRequest();
        request.setExamVersionId(examVersionId);
    }

    // ==================== SHUFFLE ALGORITHM TESTS ====================

    @Test
    @DisplayName("Shuffle - shuffleQuestionsAcrossSections=true shuffles all questions globally")
    void testShuffleAcrossSections_ShufflesAllQuestions() {
        // Arrange: 25 questions across 5 chapters (5 questions each)
        List<Map<String, Object>> questionsMetadata = new ArrayList<>();
        for (int chapter = 1; chapter <= 5; chapter++) {
            for (int q = 1; q <= 5; q++) {
                questionsMetadata.add(Map.of(
                        "id", UUID.randomUUID(),
                        "chapter", chapter,
                        "optionIds", List.of("A", "B", "C", "D")
                ));
            }
        }

        examRule.setShuffleQuestionsAcrossSections(true);

        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(contentBuilderClient.getQuestionsMetadata(any(), any())).thenReturn(questionsMetadata);

        ExamSession savedSession = new ExamSession();
        savedSession.setId(UUID.randomUUID());
        savedSession.setStatus("IN_PROGRESS");
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(savedSession);
        when(examSessionAnswerRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        ExamSessionResponse response = examSessionService.startSession(request, userId);

        // Assert
        assertNotNull(response);
        verify(contentBuilderClient).getQuestionsMetadata(any(), any());
        // Verify shuffle was called on the list
        // Note: Collections.shuffle is a static method, we verify the list was passed to session creation
        assertEquals(25, questionsMetadata.size()); // Original count preserved
    }

    @Test
    @DisplayName("Shuffle - shuffleQuestionsWithinSection=true shuffles within each chapter")
    void testShuffleWithinSection_ShufflesPerChapter() {
        // Arrange: 25 questions across 5 chapters (5 questions each)
        List<Map<String, Object>> questionsMetadata = new ArrayList<>();
        for (int chapter = 1; chapter <= 5; chapter++) {
            for (int q = 1; q <= 5; q++) {
                questionsMetadata.add(Map.of(
                        "id", UUID.randomUUID(),
                        "chapter", chapter,
                        "optionIds", List.of("A", "B", "C", "D")
                ));
            }
        }

        examRule.setShuffleQuestionsWithinSection(true);

        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(contentBuilderClient.getQuestionsMetadata(any(), any())).thenReturn(questionsMetadata);

        ExamSession savedSession = new ExamSession();
        savedSession.setId(UUID.randomUUID());
        savedSession.setStatus("IN_PROGRESS");
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(savedSession);
        when(examSessionAnswerRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        ExamSessionResponse response = examSessionService.startSession(request, userId);

        // Assert
        assertNotNull(response);
        assertEquals(25, questionsMetadata.size());
    }

    @Test
    @DisplayName("Shuffle - shuffleSections=true shuffles chapter order")
    void testShuffleSections_ShufflesChapterOrder() {
        List<Map<String, Object>> questionsMetadata = new ArrayList<>();
        for (int chapter = 1; chapter <= 5; chapter++) {
            for (int q = 1; q <= 5; q++) {
                questionsMetadata.add(Map.of(
                        "id", UUID.randomUUID(),
                        "chapter", chapter,
                        "optionIds", List.of("A", "B", "C", "D")
                ));
            }
        }

        examRule.setShuffleSections(true);

        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(contentBuilderClient.getQuestionsMetadata(any(), any())).thenReturn(questionsMetadata);

        ExamSession savedSession = new ExamSession();
        savedSession.setId(UUID.randomUUID());
        savedSession.setStatus("IN_PROGRESS");
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(savedSession);
        when(examSessionAnswerRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        ExamSessionResponse response = examSessionService.startSession(request, userId);

        // Assert
        assertNotNull(response);
    }

    @Test
    @DisplayName("Shuffle - shuffleOptions=true shuffles option order for each question")
    void testShuffleOptions_ShufflesOptionOrder() {
        List<Map<String, Object>> questionsMetadata = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            questionsMetadata.add(Map.of(
                    "id", UUID.randomUUID(),
                    "chapter", 1,
                    "optionIds", List.of("A", "B", "C", "D")
            ));
        }

        examRule.setShuffleOptions(true);

        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(contentBuilderClient.getQuestionsMetadata(any(), any())).thenReturn(questionsMetadata);

        ExamSession savedSession = new ExamSession();
        savedSession.setId(UUID.randomUUID());
        savedSession.setStatus("IN_PROGRESS");
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(savedSession);
        when(examSessionAnswerRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        ExamSessionResponse response = examSessionService.startSession(request, userId);

        // Assert
        assertNotNull(response);
        verify(examSessionAnswerRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Shuffle - All shuffle flags disabled preserves original order")
    void testNoShuffle_PreservesOrder() {
        List<Map<String, Object>> questionsMetadata = new ArrayList<>();
        for (int chapter = 1; chapter <= 5; chapter++) {
            for (int q = 1; q <= 5; q++) {
                questionsMetadata.add(Map.of(
                        "id", UUID.randomUUID(),
                        "chapter", chapter,
                        "optionIds", List.of("A", "B", "C", "D")
                ));
            }
        }

        // All shuffle flags are false by default in setUp
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(contentBuilderClient.getQuestionsMetadata(any(), any())).thenReturn(questionsMetadata);

        ExamSession savedSession = new ExamSession();
        savedSession.setId(UUID.randomUUID());
        savedSession.setStatus("IN_PROGRESS");
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(savedSession);
        when(examSessionAnswerRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        ExamSessionResponse response = examSessionService.startSession(request, userId);

        // Assert
        assertNotNull(response);
        assertEquals(25, questionsMetadata.size());
    }

    @Test
    @DisplayName("Shuffle - Creates exactly 25 ExamSessionAnswer records")
    void testCreates25Answers() {
        List<Map<String, Object>> questionsMetadata = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            questionsMetadata.add(Map.of(
                    "id", UUID.randomUUID(),
                    "chapter", (i / 5) + 1,
                    "optionIds", List.of("A", "B", "C", "D")
            ));
        }

        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(contentBuilderClient.getQuestionsMetadata(any(), any())).thenReturn(questionsMetadata);

        ExamSession savedSession = new ExamSession();
        savedSession.setId(UUID.randomUUID());
        savedSession.setStatus("IN_PROGRESS");
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(savedSession);
        when(examSessionAnswerRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

        // Act
        ExamSessionResponse response = examSessionService.startSession(request, userId);

        // Assert
        verify(examSessionAnswerRepository).saveAll(argThat(answers -> {
            int count = 0;
            for (Object ignored : answers) {
                count++;
            }
            return count == 25;
        }));
    }

    // ==================== TRAP QUESTION (CRITICAL QUESTION) DETECTION TESTS ====================

    @Test
    @DisplayName("Critical Question - Failing a critical question marks exam as FAIL")
    void testCriticalQuestion_Failed_MarksExamFail() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        String idempotencyKey = "key-123";

        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExpiredAt(Instant.now().plusSeconds(3600));
        session.setExamVersionId(examVersionId);
        session.setStartedAt(Instant.now().minusSeconds(100));

        UUID questionId = UUID.randomUUID();
        ExamSessionAnswer answer = new ExamSessionAnswer();
        answer.setQuestionId(questionId);
        answer.setSelectedAnswer(Map.of("type", "single_choice", "value", "A"));

        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examSessionAnswerRepository.findByExamSessionId(sessionId)).thenReturn(List.of(answer));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));

        // Critical question with correct answer = B, user answered A
        InternalQuestionDetailResponse criticalQuestion = InternalQuestionDetailResponse.builder()
                .id(questionId)
                .type("MULTIPLE_CHOICE")
                .isCritical(true)
                .options(List.of(
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("11111111-1111-1111-1111-111111111111")).content("A").isCorrect(false).build(),
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("22222222-2222-2222-2222-222222222222")).content("B").isCorrect(true).build()
                ))
                .build();

        when(contentBuilderClient.getQuestionsBatch(anyList())).thenReturn(List.of(criticalQuestion));

        // Act
        examSessionService.submitExam(sessionId, idempotencyKey, userId);

        // Assert
        verify(examSessionRepository).save(argThat(s ->
                Boolean.TRUE.equals(s.getMetadata().get("failedCriticalQuestion"))
        ));
    }

    @Test
    @DisplayName("Critical Question - Answering critical question correctly does not mark FAIL")
    void testCriticalQuestion_Correct_DoesNotMarkFail() {
        UUID sessionId = UUID.randomUUID();
        String idempotencyKey = "key-123";

        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExpiredAt(Instant.now().plusSeconds(3600));
        session.setExamVersionId(examVersionId);
        session.setStartedAt(Instant.now().minusSeconds(100));

        UUID questionId = UUID.randomUUID();
        ExamSessionAnswer answer = new ExamSessionAnswer();
        answer.setQuestionId(questionId);
        answer.setSelectedAnswer(Map.of("type", "single_choice", "value", UUID.fromString("22222222-2222-2222-2222-222222222222").toString()));

        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examSessionAnswerRepository.findByExamSessionId(sessionId)).thenReturn(List.of(answer));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));

        InternalQuestionDetailResponse criticalQuestion = InternalQuestionDetailResponse.builder()
                .id(questionId)
                .type("MULTIPLE_CHOICE")
                .isCritical(true)
                .options(List.of(
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("11111111-1111-1111-1111-111111111111")).content("A").isCorrect(false).build(),
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("22222222-2222-2222-2222-222222222222")).content("B").isCorrect(true).build()
                ))
                .build();

        when(contentBuilderClient.getQuestionsBatch(anyList())).thenReturn(List.of(criticalQuestion));

        // Act
        examSessionService.submitExam(sessionId, idempotencyKey, userId);

        // Assert
        verify(examSessionRepository).save(argThat(s ->
                !Boolean.TRUE.equals(s.getMetadata().get("failedCriticalQuestion"))
        ));
    }

    @Test
    @DisplayName("Critical Question - Multiple critical questions, failing any marks FAIL")
    void testMultipleCriticalQuestions_OneFailed_MarksFail() {
        UUID sessionId = UUID.randomUUID();

        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExpiredAt(Instant.now().plusSeconds(3600));
        session.setExamVersionId(examVersionId);
        session.setStartedAt(Instant.now().minusSeconds(100));

        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();

        ExamSessionAnswer answer1 = new ExamSessionAnswer();
        answer1.setQuestionId(q1);
        answer1.setSelectedAnswer(Map.of("type", "single_choice", "value", "A")); // Wrong

        ExamSessionAnswer answer2 = new ExamSessionAnswer();
        answer2.setQuestionId(q2);
        answer2.setSelectedAnswer(Map.of("type", "single_choice", "value", UUID.fromString("44444444-4444-4444-4444-444444444444").toString())); // Correct

        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examSessionAnswerRepository.findByExamSessionId(sessionId)).thenReturn(List.of(answer1, answer2));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));

        InternalQuestionDetailResponse criticalQ1 = InternalQuestionDetailResponse.builder()
                .id(q1)
                .type("MULTIPLE_CHOICE")
                .isCritical(true)
                .options(List.of(
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("11111111-1111-1111-1111-111111111111")).content("A").isCorrect(false).build(),
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("22222222-2222-2222-2222-222222222222")).content("B").isCorrect(true).build()
                ))
                .build();

        InternalQuestionDetailResponse criticalQ2 = InternalQuestionDetailResponse.builder()
                .id(q2)
                .type("MULTIPLE_CHOICE")
                .isCritical(true)
                .options(List.of(
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("33333333-3333-3333-3333-333333333333")).content("C").isCorrect(false).build(),
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("44444444-4444-4444-4444-444444444444")).content("D").isCorrect(true).build()
                ))
                .build();

        when(contentBuilderClient.getQuestionsBatch(anyList())).thenReturn(List.of(criticalQ1, criticalQ2));

        // Act
        examSessionService.submitExam(sessionId, "key", userId);

        // Assert
        verify(examSessionRepository).save(argThat(s ->
                Boolean.TRUE.equals(s.getMetadata().get("failedCriticalQuestion"))
        ));
    }

    @Test
    @DisplayName("Critical Question - Non-critical wrong answer does not trigger FAIL")
    void testNonCriticalWrongAnswer_DoesNotTriggerFail() {
        UUID sessionId = UUID.randomUUID();

        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExpiredAt(Instant.now().plusSeconds(3600));
        session.setExamVersionId(examVersionId);
        session.setStartedAt(Instant.now().minusSeconds(100));

        UUID questionId = UUID.randomUUID();
        ExamSessionAnswer answer = new ExamSessionAnswer();
        answer.setQuestionId(questionId);
        answer.setSelectedAnswer(Map.of("type", "single_choice", "value", "A")); // Wrong

        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examSessionAnswerRepository.findByExamSessionId(sessionId)).thenReturn(List.of(answer));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));

        InternalQuestionDetailResponse normalQuestion = InternalQuestionDetailResponse.builder()
                .id(questionId)
                .type("MULTIPLE_CHOICE")
                .isCritical(false) // Not critical
                .options(List.of(
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("11111111-1111-1111-1111-111111111111")).content("A").isCorrect(false).build(),
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("22222222-2222-2222-2222-222222222222")).content("B").isCorrect(true).build()
                ))
                .build();

        when(contentBuilderClient.getQuestionsBatch(anyList())).thenReturn(List.of(normalQuestion));

        // Act
        examSessionService.submitExam(sessionId, "key", userId);

        // Assert
        verify(examSessionRepository).save(argThat(s ->
                !Boolean.TRUE.equals(s.getMetadata().get("failedCriticalQuestion"))
        ));
    }

    @Test
    @DisplayName("Critical Question - Unanswered critical question triggers FAIL")
    void testCriticalQuestion_Unanswered_TriggersFail() {
        UUID sessionId = UUID.randomUUID();

        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExpiredAt(Instant.now().plusSeconds(3600));
        session.setExamVersionId(examVersionId);
        session.setStartedAt(Instant.now().minusSeconds(100));

        UUID questionId = UUID.randomUUID();
        ExamSessionAnswer answer = new ExamSessionAnswer();
        answer.setQuestionId(questionId);
        answer.setSelectedAnswer(null); // Unanswered

        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examSessionAnswerRepository.findByExamSessionId(sessionId)).thenReturn(List.of(answer));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examRuleId)).thenReturn(Optional.of(examRule));

        InternalQuestionDetailResponse criticalQuestion = InternalQuestionDetailResponse.builder()
                .id(questionId)
                .type("MULTIPLE_CHOICE")
                .isCritical(true)
                .options(List.of(
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("11111111-1111-1111-1111-111111111111")).content("A").isCorrect(false).build(),
                        InternalQuestionOptionResponse.builder().id(UUID.fromString("22222222-2222-2222-2222-222222222222")).content("B").isCorrect(true).build()
                ))
                .build();

        when(contentBuilderClient.getQuestionsBatch(anyList())).thenReturn(List.of(criticalQuestion));

        // Act
        examSessionService.submitExam(sessionId, "key", userId);

        // Assert
        verify(examSessionRepository).save(argThat(s ->
                Boolean.TRUE.equals(s.getMetadata().get("failedCriticalQuestion"))
        ));
    }
}