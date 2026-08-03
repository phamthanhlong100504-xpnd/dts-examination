package dts.com.examination.application.service;

import dts.com.examination.api.form.StartExamSessionRequest;
import dts.com.examination.api.response.ExamSessionResponse;

import java.util.UUID;

public interface ExamSessionService {
    
    ExamSessionResponse startSession(StartExamSessionRequest request, UUID userId);
    
}
