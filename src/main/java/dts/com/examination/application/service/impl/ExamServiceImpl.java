package dts.com.examination.application.service.impl;

import dts.com.examination.api.form.ChangeExamStatusRequest;
import dts.com.examination.api.form.CreateExamRequest;
import dts.com.examination.api.form.UpdateExamRequest;
import dts.com.examination.api.response.ExamResponse;
import dts.com.examination.application.exception.BusinessRuleException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.application.service.ExamService;
import dts.com.examination.domain.entity.Exam;
import dts.com.examination.domain.repository.ExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamRepository examRepository;

    @Override
    @Transactional
    public ExamResponse create(CreateExamRequest request) {
        if (examRepository.existsByTitleAndDeletedAtIsNull(request.title())) {
            throw new BusinessRuleException("Exam title already exists.");
        }

        Exam exam = Exam.builder()
                .title(request.title())
                .thumbnailId(request.thumbnailId())
                .metadata(request.metadata() != null ? request.metadata() : Map.of())
                .status("DRAFT")
                .build();

        Exam saved = examRepository.save(exam);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExamResponse> getList(String keyword, String status, UUID createdBy, Pageable pageable) {
        return examRepository.findByFilters(keyword, status, createdBy, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamResponse getDetail(UUID examId) {
        Exam exam = getExamOrThrow(examId);
        return mapToResponse(exam);
    }

    @Override
    @Transactional
    public ExamResponse update(UUID examId, UpdateExamRequest request) {
        Exam exam = getExamOrThrow(examId);

        if ("ARCHIVED".equals(exam.getStatus())) {
            throw new BusinessRuleException("Cannot update an ARCHIVED exam.");
        }

        if (request.title() != null) {
            if (examRepository.existsByTitleAndIdNotAndDeletedAtIsNull(request.title(), examId)) {
                throw new BusinessRuleException("Exam title already exists.");
            }
            exam.setTitle(request.title());
        }

        if (request.thumbnailId() != null) {
            exam.setThumbnailId(request.thumbnailId());
        }

        if (request.metadata() != null) {
            exam.setMetadata(request.metadata());
        }

        Exam saved = examRepository.save(exam);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID examId) {
        Exam exam = getExamOrThrow(examId);
        
        // TODO: Check if there are any child ExamVersion records in PUBLISHED state. Throw BusinessRuleException if found.
        // examVersionRepository.existsByExamIdAndStatus(examId, "PUBLISHED") -> throw new BusinessRuleException(...)

        exam.setDeletedAt(Instant.now());
        examRepository.save(exam);
    }

    @Override
    @Transactional
    public ExamResponse changeStatus(UUID examId, ChangeExamStatusRequest request) {
        Exam exam = getExamOrThrow(examId);
        String currentStatus = exam.getStatus();
        String targetStatus = request.status();

        if (currentStatus.equals(targetStatus)) {
            throw new BusinessRuleException("Exam is already in the requested status.");
        }

        if ("ARCHIVED".equals(currentStatus)) {
            throw new BusinessRuleException("Cannot transition status from ARCHIVED.");
        }

        boolean validTransition = false;
        if ("DRAFT".equals(currentStatus) && ("PUBLISHED".equals(targetStatus) || "HIDDEN".equals(targetStatus))) {
            validTransition = true;
        } else if ("PUBLISHED".equals(currentStatus) && ("ARCHIVED".equals(targetStatus) || "HIDDEN".equals(targetStatus))) {
            validTransition = true;
        } else if ("HIDDEN".equals(currentStatus) && ("DRAFT".equals(targetStatus) || "PUBLISHED".equals(targetStatus))) {
            validTransition = true;
        }

        if (!validTransition) {
            throw new BusinessRuleException("Invalid status transition from " + currentStatus + " to " + targetStatus);
        }

        exam.setStatus(targetStatus);
        Exam saved = examRepository.save(exam);
        return mapToResponse(saved);
    }

    private Exam getExamOrThrow(UUID examId) {
        return examRepository.findByIdAndNotDeleted(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
    }

    private ExamResponse mapToResponse(Exam exam) {
        return ExamResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .thumbnailId(exam.getThumbnailId())
                .status(exam.getStatus())
                .metadata(exam.getMetadata())
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .build();
    }
}
