package dts.com.examination.application.service;

import dts.com.examination.api.form.StartExamSessionRequest;
import dts.com.examination.api.response.ExamSessionResponse;
import dts.com.examination.api.response.ExamSessionDetailResponse;

import java.util.UUID;

public interface ExamSessionService {
    
    ExamSessionResponse startSession(StartExamSessionRequest request, UUID userId);

    ExamSessionDetailResponse getSessionDetail(UUID sessionId, UUID userId);
    
    dts.com.examination.api.response.ExamPaperResponse getExamPaper(UUID sessionId, UUID userId);

    dts.com.examination.api.response.SaveAnswersResponse saveAnswers(UUID sessionId, dts.com.examination.api.form.SaveAnswersRequest request, UUID userId);
}
