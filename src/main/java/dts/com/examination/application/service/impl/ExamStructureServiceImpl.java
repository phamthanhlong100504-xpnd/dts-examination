package dts.com.examination.application.service.impl;

import dts.com.examination.api.form.CreateExamStructureRequest;
import dts.com.examination.api.form.UpdateExamStructureRequest;
import dts.com.examination.api.response.ExamStructureResponse;
import dts.com.examination.application.dto.SectionDto;
import dts.com.examination.application.exception.BusinessRuleException;
import dts.com.examination.application.exception.BusinessValidationException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.domain.entity.ExamStructure;
import dts.com.examination.domain.entity.Section;
import dts.com.examination.domain.repository.ExamStructureRepository;
import dts.com.examination.domain.repository.ExamVersionRepository;
import dts.com.examination.application.service.ExamStructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamStructureServiceImpl implements ExamStructureService {

    private final ExamStructureRepository examStructureRepository;
    private final ExamVersionRepository examVersionRepository;

    @Override
    @Transactional
    public ExamStructureResponse create(CreateExamStructureRequest request, UUID currentUserId) {
        validateSectionOrders(request.sections());

        ExamStructure entity = ExamStructure.builder()
                .title(request.title())
                .sections(mapToSections(request.sections()))
                .metadata(request.metadata() != null ? request.metadata() : Map.of())
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        ExamStructure saved = examStructureRepository.save(entity);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExamStructureResponse> getList(String keyword, String status, Pageable pageable) {
        return examStructureRepository.findByFilters(keyword, status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamStructureResponse getDetail(UUID structureId) {
        ExamStructure entity = getExamStructureOrThrow(structureId);
        return mapToResponse(entity);
    }

    @Override
    @Transactional
    public ExamStructureResponse update(UUID structureId, UpdateExamStructureRequest request, UUID currentUserId) {
        ExamStructure entity = getExamStructureOrThrow(structureId);

        if (!entity.getCreatedBy().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have permission to modify this exam structure.");
        }

        if (examVersionRepository.existsByExamStructureIdAndStatus(structureId, "PUBLISHED")) {
            throw new BusinessRuleException("Cannot modify an exam structure that is in use by a PUBLISHED exam version.");
        }

        if (request.title() != null) {
            entity.setTitle(request.title());
        }
        if (request.sections() != null) {
            validateSectionOrders(request.sections());
            entity.setSections(mapToSections(request.sections()));
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.metadata() != null) {
            entity.setMetadata(request.metadata());
        }
        
        entity.setUpdatedBy(currentUserId);
        entity.setUpdatedAt(OffsetDateTime.now());

        ExamStructure saved = examStructureRepository.save(entity);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID structureId, UUID currentUserId) {
        ExamStructure entity = getExamStructureOrThrow(structureId);

        if (!entity.getCreatedBy().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have permission to delete this exam structure.");
        }

        if (examVersionRepository.existsByExamStructureIdAndDeletedAtIsNull(structureId)) {
            throw new BusinessRuleException("Cannot delete an exam structure that is referenced by exam versions.");
        }

        entity.setDeletedAt(OffsetDateTime.now());
        entity.setUpdatedBy(currentUserId);
        examStructureRepository.save(entity);
    }

    private ExamStructure getExamStructureOrThrow(UUID structureId) {
        return examStructureRepository.findByIdAndNotDeleted(structureId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam structure not found"));
    }

    private void validateSectionOrders(List<SectionDto> sections) {
        if (sections == null || sections.isEmpty()) return;
        
        Set<Integer> orders = sections.stream()
                .map(SectionDto::order)
                .collect(Collectors.toSet());
                
        if (orders.size() != sections.size()) {
            throw new BusinessValidationException("Section orders must be unique within the structure.");
        }
    }

    private List<Section> mapToSections(List<SectionDto> dtos) {
        return dtos.stream().map(dto -> Section.builder()
                .code(dto.code())
                .title(dto.title())
                .questionCount(dto.questionCount())
                .score(dto.score())
                .order(dto.order())
                .build()).toList();
    }

    private ExamStructureResponse mapToResponse(ExamStructure entity) {
        List<SectionDto> sectionDtos = entity.getSections().stream()
                .map(s -> SectionDto.builder()
                        .code(s.getCode())
                        .title(s.getTitle())
                        .questionCount(s.getQuestionCount())
                        .score(s.getScore())
                        .order(s.getOrder())
                        .build()).toList();

        return ExamStructureResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .sections(sectionDtos)
                .metadata(entity.getMetadata())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
