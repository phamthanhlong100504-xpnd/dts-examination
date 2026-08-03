package dts.com.examination.application.service;

import dts.com.examination.api.form.StartExamSessionRequest;
import dts.com.examination.api.response.ExamSessionResponse;
import dts.com.examination.api.response.ExamSessionDetailResponse;
import dts.com.examination.api.response.PauseSessionResponse;
import dts.com.examination.api.response.ResumeSessionResponse;
import dts.com.examination.api.response.SessionHistoryResponse;
import dts.com.examination.api.response.SessionProgressResponse;

import java.util.UUID;

public interface ExamSessionService {
    
    ExamSessionResponse startSession(StartExamSessionRequest request, UUID userId);

    ExamSessionDetailResponse getSessionDetail(UUID sessionId, UUID userId);
    
    dts.com.examination.api.response.ExamPaperResponse getExamPaper(UUID sessionId, UUID userId);

    dts.com.examination.api.response.SaveAnswersResponse saveAnswers(UUID sessionId, dts.com.examination.api.form.SaveAnswersRequest request, UUID userId);

    dts.com.examination.api.response.SubmitExamResponse submitExam(UUID sessionId, String idempotencyKey, UUID userId);

    dts.com.examination.api.response.ExamResultResponse getExamResult(UUID sessionId, UUID userId);

    SessionProgressResponse getSessionProgress(UUID sessionId, UUID userId);

    PauseSessionResponse pauseSession(UUID sessionId, UUID userId);

    ResumeSessionResponse resumeSession(UUID sessionId, UUID userId);

    SessionHistoryResponse getSessionHistory(UUID examId, UUID userId, int page, int size, String status, String sort);
}
