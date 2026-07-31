package dts.com.examination.application.service;

import dts.com.examination.api.form.CreateExamStructureRequest;
import dts.com.examination.api.form.UpdateExamStructureRequest;
import dts.com.examination.api.response.ExamStructureResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ExamStructureService {
    ExamStructureResponse create(CreateExamStructureRequest request, UUID currentUserId);
    Page<ExamStructureResponse> getList(String keyword, String status, Pageable pageable);
    ExamStructureResponse getDetail(UUID structureId);
    ExamStructureResponse update(UUID structureId, UpdateExamStructureRequest request, UUID currentUserId);
    void delete(UUID structureId, UUID currentUserId);
}
