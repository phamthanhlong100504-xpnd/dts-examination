package dts.com.examination.application.service.impl;

import dts.com.examination.api.form.CreateExamVersionRequest;
import dts.com.examination.api.form.UpdateExamVersionRequest;
import dts.com.examination.api.response.ExamVersionResponse;
import dts.com.examination.application.exception.BusinessRuleException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.application.service.ExamVersionService;
import dts.com.examination.domain.entity.Exam;
import dts.com.examination.domain.entity.ExamVersion;
import dts.com.examination.domain.repository.ExamRepository;
import dts.com.examination.domain.repository.ExamVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamVersionServiceImpl implements ExamVersionService {

    private final ExamVersionRepository examVersionRepository;
    private final ExamRepository examRepository;

    @Override
    @Transactional
    public ExamVersionResponse create(UUID examId, CreateExamVersionRequest request) {
        verifyExamExists(examId);
        
        if (request.startedAt() != null && request.endedAt() != null) {
            if (!request.startedAt().isBefore(request.endedAt())) {
                throw new BusinessRuleException("startedAt must be before endedAt");
            }
        }

        int maxVersionNo = examVersionRepository.findMaxVersionNoByExamId(examId);

        ExamVersion version = ExamVersion.builder()
                .examId(examId)
                .versionNo(maxVersionNo + 1)
                .title(request.title())
                .examType(request.examType())
                .thumbnailId(request.thumbnailId())
                .examStructureId(request.examStructureId())
                .examRuleId(request.examRuleId())
                .examCriteriaId(request.examCriteriaId())
                .contentType(request.contentType())
                .contentId(request.contentId())
                .startedAt(request.startedAt())
                .endedAt(request.endedAt())
                .metadata(request.metadata() != null ? request.metadata() : java.util.Map.of())
                .status("DRAFT")
                .build();

        ExamVersion saved = examVersionRepository.save(version);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExamVersionResponse> getList(UUID examId, String status, Pageable pageable) {
        verifyExamExists(examId);
        return examVersionRepository.findByExamIdAndStatus(examId, status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamVersionResponse getDetail(UUID versionId) {
        ExamVersion version = getVersionOrThrow(versionId);
        return mapToResponse(version);
    }

    @Override
    @Transactional
    public ExamVersionResponse update(UUID versionId, UpdateExamVersionRequest request) {
        ExamVersion version = getVersionOrThrow(versionId);

        if ("PUBLISHED".equals(version.getStatus()) || "ARCHIVED".equals(version.getStatus())) {
            throw new BusinessRuleException("Cannot update a PUBLISHED or ARCHIVED version.");
        }

        if (request.startedAt() != null && request.endedAt() != null) {
            if (!request.startedAt().isBefore(request.endedAt())) {
                throw new BusinessRuleException("startedAt must be before endedAt");
            }
        }

        if (request.title() != null) {
            version.setTitle(request.title());
        }
        if (request.thumbnailId() != null) {
            version.setThumbnailId(request.thumbnailId());
        }
        if (request.examStructureId() != null) {
            version.setExamStructureId(request.examStructureId());
        }
        if (request.examRuleId() != null) {
            version.setExamRuleId(request.examRuleId());
        }
        if (request.examCriteriaId() != null) {
            version.setExamCriteriaId(request.examCriteriaId());
        }
        if (request.startedAt() != null) {
            version.setStartedAt(request.startedAt());
        }
        if (request.endedAt() != null) {
            version.setEndedAt(request.endedAt());
        }
        if (request.metadata() != null) {
            version.setMetadata(request.metadata());
        }

        ExamVersion saved = examVersionRepository.save(version);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID versionId) {
        ExamVersion version = getVersionOrThrow(versionId);

        if ("PUBLISHED".equals(version.getStatus())) {
            throw new BusinessRuleException("Cannot delete a PUBLISHED version.");
        }

        // Exam Session check is currently omitted as per base implementation

        version.setDeletedAt(Instant.now());
        examVersionRepository.save(version);
    }

    @Override
    @Transactional
    public ExamVersionResponse publish(UUID versionId) {
        ExamVersion version = getVersionOrThrow(versionId);

        if (!"DRAFT".equals(version.getStatus())) {
            throw new BusinessRuleException("Only DRAFT versions can be published.");
        }

        // Archive other published versions
        List<ExamVersion> publishedVersions = examVersionRepository.findByExamIdAndStatusAndDeletedAtIsNullList(version.getExamId(), "PUBLISHED");
        for (ExamVersion pubVer : publishedVersions) {
            pubVer.setStatus("ARCHIVED");
            examVersionRepository.save(pubVer);
        }

        version.setStatus("PUBLISHED");
        ExamVersion saved = examVersionRepository.save(version);

        // Update exam status
        Exam exam = examRepository.findByIdAndNotDeleted(version.getExamId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        exam.setStatus("PUBLISHED");
        examRepository.save(exam);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ExamVersionResponse archive(UUID versionId) {
        ExamVersion version = getVersionOrThrow(versionId);

        if (!"PUBLISHED".equals(version.getStatus())) {
            throw new BusinessRuleException("Only PUBLISHED versions can be explicitly archived.");
        }

        version.setStatus("ARCHIVED");
        ExamVersion saved = examVersionRepository.save(version);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public ExamVersionResponse clone(UUID versionId) {
        ExamVersion source = getVersionOrThrow(versionId);

        int maxVersionNo = examVersionRepository.findMaxVersionNoByExamId(source.getExamId());

        ExamVersion newVersion = ExamVersion.builder()
                .examId(source.getExamId())
                .versionNo(maxVersionNo + 1)
                .title("[Clone] " + source.getTitle())
                .examType(source.getExamType())
                .thumbnailId(source.getThumbnailId())
                .examStructureId(source.getExamStructureId())
                .examRuleId(source.getExamRuleId())
                .examCriteriaId(source.getExamCriteriaId())
                .contentType(source.getContentType())
                .contentId(source.getContentId())
                .startedAt(source.getStartedAt())
                .endedAt(source.getEndedAt())
                .metadata(source.getMetadata())
                .status("DRAFT")
                .build();

        ExamVersion saved = examVersionRepository.save(newVersion);
        return mapToResponse(saved);
    }

    private void verifyExamExists(UUID examId) {
        if (!examRepository.existsById(examId)) {
            throw new ResourceNotFoundException("Exam not found");
        }
    }

    private ExamVersion getVersionOrThrow(UUID versionId) {
        return examVersionRepository.findByIdAndDeletedAtIsNull(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam version not found"));
    }

    private ExamVersionResponse mapToResponse(ExamVersion version) {
        return ExamVersionResponse.builder()
                .id(version.getId())
                .examId(version.getExamId())
                .versionNo(version.getVersionNo())
                .title(version.getTitle())
                .examType(version.getExamType())
                .thumbnailId(version.getThumbnailId())
                .examStructureId(version.getExamStructureId())
                .examRuleId(version.getExamRuleId())
                .examCriteriaId(version.getExamCriteriaId())
                .contentType(version.getContentType())
                .contentId(version.getContentId())
                .startedAt(version.getStartedAt())
                .endedAt(version.getEndedAt())
                .status(version.getStatus())
                .metadata(version.getMetadata())
                .createdAt(version.getCreatedAt())
                .updatedAt(version.getUpdatedAt())
                .build();
    }
}
