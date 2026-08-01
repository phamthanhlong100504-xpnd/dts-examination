package dts.com.examination.application.service;

import dts.com.examination.api.form.ChangeExamStatusRequest;
import dts.com.examination.api.form.CreateExamRequest;
import dts.com.examination.api.form.UpdateExamRequest;
import dts.com.examination.api.response.ExamResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ExamService {
    ExamResponse create(CreateExamRequest request);
    Page<ExamResponse> getList(String keyword, String status, UUID createdBy, Pageable pageable);
    ExamResponse getDetail(UUID examId);
    ExamResponse update(UUID examId, UpdateExamRequest request);
    void delete(UUID examId);
    ExamResponse changeStatus(UUID examId, ChangeExamStatusRequest request);
}
