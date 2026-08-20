package dts.com.examination.application.service.impl;

import dts.com.examination.api.form.StartExamSessionRequest;
import dts.com.examination.api.response.ExamSessionResponse;
import dts.com.examination.application.client.ContentBuilderClient;
import dts.com.examination.application.exception.BusinessRuleException;
import dts.com.examination.domain.entity.ExamRule;
import dts.com.examination.domain.entity.ExamSession;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamSessionServiceImplTest {

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
    private ExamVersion examVersion;
    private ExamRule examRule;
    private StartExamSessionRequest request;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        examVersionId = UUID.randomUUID();
        examId = UUID.randomUUID();

        examVersion = new ExamVersion();
        examVersion.setId(examVersionId);
        examVersion.setExamId(examId);
        examVersion.setExamRuleId(UUID.randomUUID());
        examVersion.setContentId(UUID.randomUUID());
        examVersion.setContentType("PROGRAM");
        examVersion.setStartedAt(Instant.now().minusSeconds(3600));
        examVersion.setEndedAt(Instant.now().plusSeconds(3600));

        examRule = new ExamRule();
        examRule.setId(examVersion.getExamRuleId());
        examRule.setAllowRetry(true);
        examRule.setMaxRetry(3);

        request = new StartExamSessionRequest();
        request.setExamVersionId(examVersionId);
    }

    @Test
    @DisplayName("Path 1: Happy Case - Start by ExamVersionId successfully")
    void testStartSession_ByVersionId_Success() {
        // Arrange
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(examVersionId, userId, "IN_PROGRESS"))
                .thenReturn(Optional.empty());
        when(contentBuilderClient.getQuestionsMetadata(examVersion.getContentId(), examVersion.getContentType()))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
        
        ExamSession savedSession = new ExamSession();
        savedSession.setId(UUID.randomUUID());
        savedSession.setStartedAt(Instant.now());
        savedSession.setStatus("IN_PROGRESS");
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(savedSession);

        // Act
        ExamSessionResponse response = examSessionService.startSession(request, userId);

        // Assert
        assertNotNull(response);
        assertEquals(savedSession.getId(), response.getId());
        assertEquals("IN_PROGRESS", response.getStatus());
        verify(examSessionRepository, times(1)).save(any(ExamSession.class));
    }

    @Test
    @DisplayName("Path 2: Edge Case - Start by ExamId successfully")
    void testStartSession_ByExamId_Success() {
        // Arrange
        request.setExamVersionId(null);
        request.setExamId(examId);

        when(examVersionRepository.findByExamIdAndStatusAndDeletedAtIsNullList(examId, "PUBLISHED"))
                .thenReturn(List.of(examVersion));
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(examVersionId, userId, "IN_PROGRESS"))
                .thenReturn(Optional.empty());
        when(contentBuilderClient.getQuestionsMetadata(examVersion.getContentId(), examVersion.getContentType()))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID())));
        
        ExamSession savedSession = new ExamSession();
        savedSession.setId(UUID.randomUUID());
        savedSession.setStatus("IN_PROGRESS");
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(savedSession);

        // Act
        ExamSessionResponse response = examSessionService.startSession(request, userId);

        // Assert
        assertNotNull(response);
        assertEquals("IN_PROGRESS", response.getStatus());
        verify(examVersionRepository, times(1)).findByExamIdAndStatusAndDeletedAtIsNullList(examId, "PUBLISHED");
    }

    @Test
    @DisplayName("Path 3: Negative Case - Missing both examVersionId and examId")
    void testStartSession_MissingBothIds_ThrowsException() {
        // Arrange
        request.setExamVersionId(null);
        request.setExamId(null);

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.startSession(request, userId));
        assertEquals("Either examVersionId or examId must be provided", exception.getMessage());
        verifyNoInteractions(examRuleRepository, contentBuilderClient);
    }

    @Test
    @DisplayName("Path 4: Negative Case - Not in active period (Started in future)")
    void testStartSession_EarlyBeforeStart_ThrowsException() {
        // Arrange
        examVersion.setStartedAt(Instant.now().plusSeconds(3600)); // Future
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.startSession(request, userId));
        assertEquals("Exam is not within active period", exception.getMessage());
    }

    @Test
    @DisplayName("Path 5: Negative Case - Not in active period (Ended in past)")
    void testStartSession_LateAfterEnd_ThrowsException() {
        // Arrange
        examVersion.setStartedAt(Instant.now().minusSeconds(7200));
        examVersion.setEndedAt(Instant.now().minusSeconds(3600)); // Past
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.startSession(request, userId));
        assertEquals("Exam is not within active period", exception.getMessage());
    }

    @Test
    @DisplayName("Path 6: Edge Case - Returns existing IN_PROGRESS session")
    void testStartSession_HasActiveSession_ReturnsExisting() {
        // Arrange
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        
        ExamSession existingSession = new ExamSession();
        existingSession.setId(UUID.randomUUID());
        existingSession.setStatus("IN_PROGRESS");
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(examVersionId, userId, "IN_PROGRESS"))
                .thenReturn(Optional.of(existingSession));

        // Act
        ExamSessionResponse response = examSessionService.startSession(request, userId);

        // Assert
        assertNotNull(response);
        assertEquals(existingSession.getId(), response.getId());
        assertEquals("IN_PROGRESS", response.getStatus());
        verify(examSessionRepository, never()).save(any(ExamSession.class)); // Ensures new session is not saved
    }

    @Test
    @DisplayName("Path 7: Negative Case - Content has no questions")
    void testStartSession_EmptyContent_ThrowsException() {
        // Arrange
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(examRule));
        when(examSessionRepository.countByExamVersionIdAndUserId(examVersionId, userId)).thenReturn(0);
        when(examSessionRepository.findFirstByExamVersionIdAndUserIdAndStatus(examVersionId, userId, "IN_PROGRESS"))
                .thenReturn(Optional.empty());
        when(contentBuilderClient.getQuestionsMetadata(examVersion.getContentId(), examVersion.getContentType()))
                .thenReturn(Collections.emptyList()); // Empty list

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.startSession(request, userId));
        assertEquals("No questions found for this exam", exception.getMessage());
        verify(examSessionRepository, never()).save(any(ExamSession.class));
    }
    // ---------------------------------------------------------
    // Phase 2: Tests for submitExam (V(G) = 6)
    // ---------------------------------------------------------

    @Test
    @DisplayName("submitExam - Path 1: Happy Case - Submit successfully")
    void testSubmitExam_HappyCase_Success() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        String idempotencyKey = "key-123";
        
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExpiredAt(Instant.now().plusSeconds(3600)); // Future
        session.setExamVersionId(examVersionId);
        session.setStartedAt(Instant.now().minusSeconds(100));
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examSessionAnswerRepository.findByExamSessionId(sessionId)).thenReturn(java.util.Collections.emptyList());
        when(contentBuilderClient.getQuestionsBatch(any())).thenReturn(java.util.Collections.emptyList());
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(examRule));
        
        // Act
        dts.com.examination.api.response.SubmitExamResponse response = examSessionService.submitExam(sessionId, idempotencyKey, userId);

        // Assert
        assertNotNull(response);
        assertEquals("SUBMITTED", response.getStatus());
        assertEquals(sessionId, response.getSessionId());
        verify(examSessionRepository, times(1)).save(session); // Bug detection point!
    }

    @Test
    @DisplayName("submitExam - Path 2: Negative Case - Session not found")
    void testSubmitExam_SessionNotFound_ThrowsException() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        
        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.submitExam(sessionId, "key", userId));
        assertEquals("Exam session not found", exception.getMessage());
    }

    @Test
    @DisplayName("submitExam - Path 3: Negative Case - Access Denied (Wrong User)")
    void testSubmitExam_WrongUser_ThrowsException() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(UUID.randomUUID()); // Different user
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        
        // Act & Assert
        org.springframework.security.access.AccessDeniedException exception = assertThrows(
                org.springframework.security.access.AccessDeniedException.class, 
                () -> examSessionService.submitExam(sessionId, "key", userId));
        assertEquals("Access denied", exception.getMessage());
    }

    @Test
    @DisplayName("submitExam - Path 4: Negative Case - Not IN_PROGRESS")
    void testSubmitExam_NotInProgress_ThrowsException() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("SUBMITTED"); // Already submitted
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        
        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.submitExam(sessionId, "key", userId));
        assertEquals("Session is not in progress", exception.getMessage());
    }

    @Test
    @DisplayName("submitExam - Path 5: Edge Case - Null ExpiredAt")
    void testSubmitExam_NullExpiredAt_Success() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExpiredAt(null); // No expiration
        session.setExamVersionId(examVersionId);
        session.setStartedAt(Instant.now().minusSeconds(100));
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examSessionAnswerRepository.findByExamSessionId(sessionId)).thenReturn(java.util.Collections.emptyList());
        when(contentBuilderClient.getQuestionsBatch(any())).thenReturn(java.util.Collections.emptyList());
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(examRule));
        
        // Act
        dts.com.examination.api.response.SubmitExamResponse response = examSessionService.submitExam(sessionId, "key", userId);

        // Assert
        assertEquals("SUBMITTED", response.getStatus());
        verify(examSessionRepository, times(1)).save(session);
    }

    @Test
    @DisplayName("submitExam - Path 6: Edge Case - Past ExpiredAt (Session Expired)")
    void testSubmitExam_PastExpiredAt_ThrowsException() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExpiredAt(Instant.now().minusSeconds(10)); // Past
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        
        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.submitExam(sessionId, "key", userId));
        assertEquals("Session Expired", exception.getMessage());
        assertEquals("EXPIRED", session.getStatus());
        verify(examSessionRepository, times(1)).save(session); // EXPIRED state is saved
    }

    // ---------------------------------------------------------
    // Phase 2: Tests for getExamResult (V(G) = 7)
    // ---------------------------------------------------------

    @Test
    @DisplayName("getExamResult - Path 1: Happy Case - Result retrieved successfully")
    void testGetExamResult_HappyCase_Success() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("SUBMITTED");
        session.setExamVersionId(examVersionId);
        session.setMetadata(java.util.Map.of("totalScore", 5.0, "correctCount", 5));
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        
        ExamRule rule = new ExamRule();
        rule.setId(UUID.randomUUID());
        rule.setResultReleaseMode("IMMEDIATE");
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(rule));
        
        when(examSessionAnswerRepository.countByExamSessionId(sessionId)).thenReturn(10L);
        when(examSessionAnswerRepository.countByExamSessionIdAndSelectedAnswerIsNotNull(sessionId)).thenReturn(8L);

        // Act
        dts.com.examination.api.response.ExamResultResponse response = examSessionService.getExamResult(sessionId, userId);

        // Assert
        assertNotNull(response);
        assertEquals("SUBMITTED", response.getStatus());
        assertNotNull(response.getSummary());
        assertEquals(10, response.getSummary().getTotalQuestions());
        assertEquals(8, response.getSummary().getAnsweredQuestions());
    }

    @Test
    @DisplayName("getExamResult - Path 2: Negative Case - Session not found")
    void testGetExamResult_SessionNotFound_ThrowsException() {
        UUID sessionId = UUID.randomUUID();
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.getExamResult(sessionId, userId));
        assertEquals("Exam session not found", exception.getMessage());
    }

    @Test
    @DisplayName("getExamResult - Path 3: Negative Case - Access Denied (Wrong User)")
    void testGetExamResult_WrongUser_ThrowsException() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(UUID.randomUUID()); // Different user
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        
        org.springframework.security.access.AccessDeniedException exception = assertThrows(
                org.springframework.security.access.AccessDeniedException.class, 
                () -> examSessionService.getExamResult(sessionId, userId));
        assertEquals("Access denied", exception.getMessage());
    }

    @Test
    @DisplayName("getExamResult - Path 4: Negative Case - Not SUBMITTED")
    void testGetExamResult_NotSubmitted_ThrowsException() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.getExamResult(sessionId, userId));
        assertEquals("Exam not submitted yet", exception.getMessage());
    }

    @Test
    @DisplayName("getExamResult - Path 5: Negative Case - Exam Version not found")
    void testGetExamResult_VersionNotFound_ThrowsException() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("SUBMITTED");
        session.setExamVersionId(examVersionId);
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.empty());
        
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.getExamResult(sessionId, userId));
        assertEquals("Exam version not found", exception.getMessage());
    }

    @Test
    @DisplayName("getExamResult - Path 6: Negative Case - Exam Rule not found")
    void testGetExamResult_RuleNotFound_ThrowsException() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("SUBMITTED");
        session.setExamVersionId(examVersionId);
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.empty());
        
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.getExamResult(sessionId, userId));
        assertEquals("Exam rule not found", exception.getMessage());
    }

    @Test
    @DisplayName("getExamResult - Path 7: Negative Case - Result hidden (Not IMMEDIATE)")
    void testGetExamResult_ResultHidden_ThrowsException() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("SUBMITTED");
        session.setExamVersionId(examVersionId);
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        
        ExamRule rule = new ExamRule();
        rule.setId(UUID.randomUUID());
        rule.setResultReleaseMode("AFTER_EXAM_PERIOD"); // Not IMMEDIATE
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(rule));
        
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.getExamResult(sessionId, userId));
        assertEquals("Result is hidden until exam period ends", exception.getMessage());
    }

    // ---------------------------------------------------------
    // Phase 3: Tests for remaining methods
    // ---------------------------------------------------------

    @Test
    @DisplayName("getSessionDetail - Happy Case")
    void testGetSessionDetail_Success() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setAttemptNo(1);
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        
        dts.com.examination.api.response.ExamSessionDetailResponse response = examSessionService.getSessionDetail(sessionId, userId);
        assertNotNull(response);
        assertEquals("IN_PROGRESS", response.getStatus());
    }

    @Test
    @DisplayName("getSessionProgress - Happy Case")
    void testGetSessionProgress_Success() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examSessionAnswerRepository.countByExamSessionId(sessionId)).thenReturn(10L);
        when(examSessionAnswerRepository.countByExamSessionIdAndSelectedAnswerIsNotNull(sessionId)).thenReturn(5L);
        
        dts.com.examination.api.response.SessionProgressResponse response = examSessionService.getSessionProgress(sessionId, userId);
        assertNotNull(response);
        assertEquals(10, response.getTotalQuestions());
        assertEquals(5, response.getAnsweredQuestions());
    }

    @Test
    @DisplayName("pauseSession - Happy Case")
    void testPauseSession_Success() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExamVersionId(examVersionId);
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        
        ExamRule rule = new ExamRule();
        rule.setId(UUID.randomUUID());
        rule.setAllowPause(true);
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(rule));
        
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(session);
        
        dts.com.examination.api.response.PauseSessionResponse response = examSessionService.pauseSession(sessionId, userId);
        assertNotNull(response);
        assertEquals("READY", response.getStatus());
    }

    @Test
    @DisplayName("pauseSession - Negative Case: Not allowed")
    void testPauseSession_NotAllowed_ThrowsException() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExamVersionId(examVersionId);
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(examVersionRepository.findById(examVersionId)).thenReturn(Optional.of(examVersion));
        
        ExamRule rule = new ExamRule();
        rule.setId(UUID.randomUUID());
        rule.setAllowPause(false); // Pause not allowed
        when(examRuleRepository.findById(examVersion.getExamRuleId())).thenReturn(Optional.of(rule));
        
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, 
                () -> examSessionService.pauseSession(sessionId, userId));
        assertEquals("Pause is not allowed", exception.getMessage());
    }

    @Test
    @DisplayName("resumeSession - Happy Case")
    void testResumeSession_Success() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("READY"); // Paused status
        session.setMetadata(java.util.Map.of("pausedRemainingSeconds", 1800));
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        
        ExamVersion version = new ExamVersion();
        version.setId(examVersionId);
        version.setExamRuleId(UUID.randomUUID());
        when(examVersionRepository.findById(any())).thenReturn(Optional.of(version));
        
        ExamRule rule = new ExamRule();
        rule.setId(version.getExamRuleId());
        rule.setAllowResume(true);
        when(examRuleRepository.findById(any())).thenReturn(Optional.of(rule));
        
        when(examSessionRepository.save(any(ExamSession.class))).thenReturn(session);
        
        dts.com.examination.api.response.ResumeSessionResponse response = examSessionService.resumeSession(sessionId, userId);
        assertNotNull(response);
        assertEquals("IN_PROGRESS", response.getStatus());
    }

    @Test
    @DisplayName("saveAnswers - Happy Case")
    void testSaveAnswers_Success() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExamVersionId(examVersionId);
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        
        dts.com.examination.api.form.SaveAnswersRequest request = new dts.com.examination.api.form.SaveAnswersRequest();
        dts.com.examination.api.form.AnswerItemRequest item = new dts.com.examination.api.form.AnswerItemRequest();
        item.setQuestionId(UUID.randomUUID());
        item.setSelectedAnswer(java.util.Map.of("optionId", "A"));
        request.setAnswers(List.of(item));
        
        when(examSessionAnswerRepository.findByExamSessionIdAndQuestionIdIn(any(), any())).thenReturn(Collections.emptyList());
        
        dts.com.examination.api.response.SaveAnswersResponse response = examSessionService.saveAnswers(sessionId, request, userId);
        assertNotNull(response);
        verify(examSessionAnswerRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("getExamPaper - Happy Case")
    void testGetExamPaper_Success() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("IN_PROGRESS");
        session.setExamVersionId(examVersionId);
        session.setMetadata(java.util.Map.of("randomSeed", 12345)); // Has random seed
        
        when(examSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        
        when(examSessionAnswerRepository.findByExamSessionId(sessionId)).thenReturn(Collections.emptyList());
        
        dts.com.examination.api.response.ExamPaperResponse response = examSessionService.getExamPaper(sessionId, userId);
        assertNotNull(response);
    }

    @Test
    @DisplayName("getSessionHistory - Happy Case")
    void testGetSessionHistory_Success() {
        UUID examId = UUID.randomUUID();
        when(examSessionRepository.findByExamIdAndUserId(any(), any(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(Collections.emptyList()));
        
        dts.com.examination.api.response.SessionHistoryResponse response = examSessionService.getSessionHistory(examId, userId, 0, 10, null, null);
        assertNotNull(response);
    }
}
