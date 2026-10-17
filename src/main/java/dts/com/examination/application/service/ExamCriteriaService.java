package dts.com.examination.application.service;

import dts.com.examination.api.form.CreateExamCriteriaRequest;
import dts.com.examination.api.form.UpdateExamCriteriaRequest;
import dts.com.examination.api.response.ExamCriteriaResponse;
import dts.com.examination.api.response.PageResponse;
import dts.com.examination.application.exception.BusinessValidationException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.domain.entity.ExamCriteria;
import dts.com.examination.domain.entity.json.CriteriaConfig;
import dts.com.examination.domain.repository.ExamCriteriaRepository;
import lombok.RequiredArgsConstructor;
import dts.com.examination.domain.repository.ExamVersionRepository;
import dts.com.examination.application.exception.ExaminationException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamCriteriaService {

    private final ExamVersionRepository examVersionRepository;
    private final ExamCriteriaRepository examCriteriaRepository;

    @Transactional
    public ExamCriteriaResponse createCriteria(CreateExamCriteriaRequest request) {
        validateCriteriaConfig(request.criteria());

        ExamCriteria examCriteria = ExamCriteria.builder()
                .title(request.title())
                .criteria(request.criteria())
                .status("ACTIVE")
                .metadata(request.metadata())
                .build();

        ExamCriteria saved = examCriteriaRepository.save(examCriteria);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamCriteriaResponse> getCriteriaList(int page, int size, String keyword, String status) {
        Page<ExamCriteria> criteriaPage = examCriteriaRepository.searchCriterias(keyword, status, PageRequest.of(page, size));
        
        return PageResponse.<ExamCriteriaResponse>builder()
                .page(criteriaPage.getNumber())
                .size(criteriaPage.getSize())
                .totalElements(criteriaPage.getTotalElements())
                .items(criteriaPage.getContent().stream().map(this::mapToResponse).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public ExamCriteriaResponse getCriteriaDetail(UUID criteriaId) {
        ExamCriteria criteria = examCriteriaRepository.findByIdActive(criteriaId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamCriteria not found"));
        return mapToResponse(criteria);
    }

    @Transactional
    public ExamCriteriaResponse updateCriteria(UUID criteriaId, UpdateExamCriteriaRequest request) {
        ExamCriteria criteria = examCriteriaRepository.findByIdForUpdate(criteriaId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamCriteria not found"));

        if (examVersionRepository.existsByExamCriteriaIdAndStatusIn(criteriaId, java.util.List.of("PUBLISHED", "ARCHIVED"))) {
            throw new ExaminationException(HttpStatus.CONFLICT, "CRITERIA_IN_USE", "Create a new configuration instead of editing a published or archived one");
        }

        if (!"ACTIVE".equals(criteria.getStatus())) {
            throw new BusinessValidationException("Cannot update inactive criteria");
        }


        if (request.criteria() != null) {
            validateCriteriaConfig(request.criteria());
            criteria.setCriteria(request.criteria());
        }
        if (request.title() != null) {
            criteria.setTitle(request.title());
        }
        if (request.metadata() != null) {
            criteria.setMetadata(request.metadata());
        }

        ExamCriteria updated = examCriteriaRepository.save(criteria);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteCriteria(UUID criteriaId) {
        ExamCriteria criteria = examCriteriaRepository.findByIdForUpdate(criteriaId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamCriteria not found"));

        if (examVersionRepository.existsByExamCriteriaId(criteriaId)) {
            throw new ExaminationException(HttpStatus.CONFLICT, "CRITERIA_REFERENCED", "Cannot delete a configuration referenced by any version history");
        }


        criteria.setDeletedAt(Instant.now());
        examCriteriaRepository.save(criteria);
    }

    private void validateCriteriaConfig(CriteriaConfig config) {
        if (config.getPassScore() == null || config.getPassScore() < 0) {
            throw new BusinessValidationException("passScore must be >= 0");
        }
        if (config.getTotalScore() == null || config.getTotalScore() < config.getPassScore()) {
            throw new BusinessValidationException("totalScore must be >= passScore");
        }
        if (config.getGradingMethod() == null) {
            throw new BusinessValidationException("gradingMethod is required");
        }
        if (config.getRounding() != null && config.getRounding().getPrecision() != null && config.getRounding().getPrecision() < 0) {
            throw new BusinessValidationException("rounding precision must be >= 0");
        }
        if (config.getPenalties() != null) {
            boolean invalidPenalty = config.getPenalties().stream()
                    .anyMatch(p -> p.getDeduct() == null || p.getDeduct() < 0);
            if (invalidPenalty) {
                throw new BusinessValidationException("Penalty deduct must be >= 0");
            }
        }
    }

    private ExamCriteriaResponse mapToResponse(ExamCriteria entity) {
        return ExamCriteriaResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .criteria(entity.getCriteria())
                .metadata(entity.getMetadata())
                .build();
    }
}
