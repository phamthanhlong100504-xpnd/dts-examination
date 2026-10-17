package dts.com.examination.application.service.impl;

import dts.com.examination.api.form.CreateExamStructureRequest;
import dts.com.examination.api.form.UpdateExamStructureRequest;
import dts.com.examination.api.response.ExamStructureResponse;
import dts.com.examination.application.dto.SectionDto;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.application.service.ExamStructureService;
import dts.com.examination.application.service.ExamStructureValidator;
import dts.com.examination.domain.entity.ExamStructure;
import dts.com.examination.domain.entity.Section;
import dts.com.examination.domain.repository.ExamStructureRepository;
import dts.com.examination.domain.repository.ExamVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamStructureServiceImpl implements ExamStructureService {

    private final ExamStructureRepository examStructureRepository;
    private final ExamVersionRepository examVersionRepository;
    private final ExamStructureValidator validator;

    @Override
    @Transactional
    public ExamStructureResponse create(CreateExamStructureRequest request, UUID currentUserId) {
        requireActor(currentUserId);
        String title = validator.text(request.title(), 255, "Structure title");
        List<Section> sections = validator.normalize(request.sections());
        ExamStructure entity = ExamStructure.builder().title(title).sections(sections)
                .metadata(copyMetadata(request.metadata())).createdBy(currentUserId)
                .updatedBy(currentUserId).build();
        return mapToResponse(examStructureRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExamStructureResponse> getList(String keyword, String status, Pageable pageable) {
        return examStructureRepository.findByFilters(keyword, status, pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamStructureResponse getDetail(UUID structureId) {
        ExamStructure entity = examStructureRepository.findByIdAndNotDeleted(structureId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam structure not found"));
        return mapToResponse(entity);
    }

    @Override
    @Transactional
    public ExamStructureResponse update(UUID structureId, UpdateExamStructureRequest request, UUID currentUserId) {
        requireActor(currentUserId);
        ExamStructure entity = getForUpdate(structureId);
        if (examVersionRepository.existsByExamStructureIdAndStatusIn(
                structureId, List.of("PUBLISHED", "ARCHIVED"))) {
            throw new ExaminationException(HttpStatus.CONFLICT, "STRUCTURE_IN_USE",
                    "This structure belongs to a published or archived version. Create a new structure instead.");
        }

        // Validate all changed values before mutating the managed entity.
        String title = request.title() == null ? entity.getTitle()
                : validator.text(request.title(), 255, "Structure title");
        List<Section> sections = request.sections() == null ? entity.getSections()
                : validator.normalize(request.sections());
        validator.totals(sections);
        String status = request.status() == null ? entity.getStatus()
                : validator.text(request.status(), 30, "Structure status");
        Map<String, Object> metadata = request.metadata() == null ? entity.getMetadata()
                : copyMetadata(request.metadata());

        entity.setTitle(title);
        entity.setSections(sections);
        entity.setStatus(status);
        entity.setMetadata(metadata);
        entity.setUpdatedBy(currentUserId);
        entity.setUpdatedAt(OffsetDateTime.now());
        return mapToResponse(examStructureRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(UUID structureId, UUID currentUserId) {
        requireActor(currentUserId);
        ExamStructure entity = getForUpdate(structureId);
        if (examVersionRepository.existsByExamStructureId(structureId)) {
            throw new ExaminationException(HttpStatus.CONFLICT, "STRUCTURE_REFERENCED",
                    "Cannot delete a structure referenced by an exam version");
        }
        OffsetDateTime now = OffsetDateTime.now();
        entity.setDeletedAt(now);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(currentUserId);
        examStructureRepository.save(entity);
    }

    private ExamStructure getForUpdate(UUID structureId) {
        return examStructureRepository.findByIdForUpdate(structureId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam structure not found"));
    }

    private void requireActor(UUID actor) {
        if (actor == null) {
            throw new AccessDeniedException("Authenticated actor is required");
        }
    }

    private Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        return metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }

    private ExamStructureResponse mapToResponse(ExamStructure entity) {
        ExamStructureValidator.Totals totals = validator.totals(entity.getSections());
        List<SectionDto> sections = entity.getSections().stream()
                .sorted(Comparator.comparing(Section::getOrder))
                .map(section -> SectionDto.builder().code(section.getCode()).title(section.getTitle())
                        .questionCount(section.getQuestionCount()).score(section.getScore()).order(section.getOrder())
                        .chapterIds(section.getChapterIds() == null ? List.of() : List.copyOf(section.getChapterIds()))
                        .criticalQuestionCount(section.getCriticalQuestionCount()).build()).toList();
        return ExamStructureResponse.builder().id(entity.getId()).title(entity.getTitle())
                .status(entity.getStatus()).sections(sections).metadata(entity.getMetadata())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .totalQuestions(totals.totalQuestions()).maxScore(totals.maxScore()).build();
    }
}
