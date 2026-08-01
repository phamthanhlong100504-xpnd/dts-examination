package dts.com.examination.application.service;

import dts.com.examination.api.form.CreateExamVersionRequest;
import dts.com.examination.api.form.UpdateExamVersionRequest;
import dts.com.examination.api.response.ExamVersionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ExamVersionService {

    ExamVersionResponse create(UUID examId, CreateExamVersionRequest request);

    Page<ExamVersionResponse> getList(UUID examId, String status, Pageable pageable);

    ExamVersionResponse getDetail(UUID versionId);

    ExamVersionResponse update(UUID versionId, UpdateExamVersionRequest request);

    void delete(UUID versionId);

    ExamVersionResponse publish(UUID versionId);

    ExamVersionResponse archive(UUID versionId);

    ExamVersionResponse clone(UUID versionId);

}
