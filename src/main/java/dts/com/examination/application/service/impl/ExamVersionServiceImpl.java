package dts.com.examination.application.service.impl;

import dts.com.examination.api.form.CreateExamVersionRequest;
import dts.com.examination.api.form.UpdateExamVersionRequest;
import dts.com.examination.api.response.ExamVersionResponse;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.application.service.ExamVersionService;
import dts.com.examination.application.service.OfficialExamVersionValidator;
import dts.com.examination.application.service.selection.OfficialExamVersionConfig;
import dts.com.examination.domain.entity.*;
import dts.com.examination.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExamVersionServiceImpl implements ExamVersionService {
    private final ExamVersionRepository versions;
    private final ExamRepository exams;
    private final ExamStructureRepository structures;
    private final ExamRuleRepository rules;
    private final ExamCriteriaRepository criterias;
    private final ExamSessionRepository sessions;
    private final OfficialExamVersionValidator validator;
    private final dts.com.examination.application.service.OfficialVersionReviewService review;

    @Override
    @Transactional
    public ExamVersionResponse create(UUID examId, CreateExamVersionRequest request) {
        Exam parent = lockExam(examId);
        ExamVersion candidate = ExamVersion.builder().examId(examId).title(request.title()).examType(request.examType())
                .thumbnailId(request.thumbnailId()).examStructureId(request.examStructureId()).examRuleId(request.examRuleId())
                .examCriteriaId(request.examCriteriaId()).contentType(request.contentType()).contentId(request.contentId())
                .startedAt(request.startedAt()).endedAt(request.endedAt()).metadata(copy(request.metadata())).status("DRAFT").build();
        var profile = new OfficialExamVersionConfig(request.licenseClass(), request.bankVersion(),
                request.selectionMode(), request.manualQuestionIds());
        candidate.setConfigs(validate(candidate, profile, false));
        candidate.setVersionNo(nextVersionNo(parent.getId()));
        return response(versions.save(candidate));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExamVersionResponse> getList(UUID examId, String status, Pageable pageable) {
        requireExam(examId);
        return versions.findByExamIdAndStatus(examId, status, pageable).map(this::response);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamVersionResponse getDetail(UUID versionId) {
        ExamVersion version = requireVersion(versionId);
        requireExam(version.getExamId());
        return response(version);
    }

    @Override
    @Transactional
    public ExamVersionResponse update(UUID versionId, UpdateExamVersionRequest request) {
        ExamVersion current = lockVersion(versionId);
        requireDraftWithoutHistory(current);
        if ("OFFICIAL".equals(current.getExamType())) review.requireEditable(current.getId());
        ExamVersion candidate = copyVersion(current);
        if (request.title() != null) candidate.setTitle(request.title());
        if (request.examType() != null) candidate.setExamType(request.examType());
        if (request.thumbnailId() != null) candidate.setThumbnailId(request.thumbnailId());
        if (request.examStructureId() != null) candidate.setExamStructureId(request.examStructureId());
        if (request.examRuleId() != null) candidate.setExamRuleId(request.examRuleId());
        if (request.examCriteriaId() != null) candidate.setExamCriteriaId(request.examCriteriaId());
        if (request.contentType() != null) candidate.setContentType(request.contentType());
        if (request.contentId() != null) candidate.setContentId(request.contentId());
        if (request.startedAt() != null) candidate.setStartedAt(request.startedAt());
        if (request.endedAt() != null) candidate.setEndedAt(request.endedAt());
        if (request.metadata() != null) candidate.setMetadata(copy(request.metadata()));
        var old = validator.profile(current.getConfigs());
        var profile = new OfficialExamVersionConfig(
                request.licenseClass() != null ? request.licenseClass() : old == null ? null : old.licenseClass(),
                request.bankVersion() != null ? request.bankVersion() : old == null ? null : old.bankVersion(),
                request.selectionMode() != null ? request.selectionMode() : old == null ? null : old.selectionMode(),
                request.manualQuestionIds() != null ? request.manualQuestionIds() : old == null ? List.of() : old.manualQuestionIds());
        Map<String, Object> config = validate(candidate, profile, false);
        if ("OFFICIAL".equals(current.getExamType())) review.recordEditor(current.getId());
        // Only mutate the managed row after merged fields and the real source have passed validation.
        apply(current, candidate);
        current.setConfigs(config);
        return response(versions.save(current));
    }

    @Override
    @Transactional
    public void delete(UUID versionId) {
        ExamVersion version = lockVersion(versionId);
        requireDraftWithoutHistory(version);
        if ("OFFICIAL".equals(version.getExamType())) review.requireEditable(version.getId());
        version.setDeletedAt(Instant.now());
        versions.save(version);
    }

    @Override
    @Transactional
    public ExamVersionResponse publish(UUID versionId) {
        ExamVersion version = lockVersion(versionId);
        requireDraftWithoutHistory(version);
        Exam parent = requireExam(version.getExamId()); // Already locked by lockVersion.
        if (parent.getStatus() == null || !Set.of("DRAFT", "PUBLISHED", "HIDDEN").contains(parent.getStatus())) {
            throw conflict("EXAM_NOT_EDITABLE", "The parent examination cannot be published in its current state");
        }
        ExamVersion candidate = copyVersion(version);
        Map<String, Object> config = validate(candidate, validator.profile(version.getConfigs()), true);
        // Approval is tied to this exact verified snapshot. Never archive the old version first.
        review.requireApproved(version, config);
        // Source/config validation completes before replacing any current published version.
        List<ExamVersion> previous = versions.findByExamIdAndStatusAndDeletedAtIsNullList(version.getExamId(), "PUBLISHED");
        for (ExamVersion old : previous) {
            old.setStatus("ARCHIVED");
            Map<String, Object> oldConfig = copy(old.getConfigs());
            oldConfig.put("archivedAt", Instant.now().toString());
            old.setConfigs(oldConfig);
            versions.save(old);
        }
        apply(version, candidate);
        version.setConfigs(config);
        version.setStatus("PUBLISHED");
        versions.save(version);
        if (!"HIDDEN".equals(parent.getStatus())) {
            parent.setStatus("PUBLISHED");
            exams.save(parent);
        }
        return response(version);
    }

    @Override
    @Transactional
    public ExamVersionResponse archive(UUID versionId) {
        ExamVersion version = lockVersion(versionId);
        if (!"PUBLISHED".equals(version.getStatus())) throw conflict("VERSION_STATE_INVALID", "Only PUBLISHED versions can be archived");
        Map<String, Object> config = copy(version.getConfigs());
        config.put("archivedAt", Instant.now().toString());
        version.setConfigs(config);
        version.setStatus("ARCHIVED");
        return response(versions.save(version));
    }

    @Override
    @Transactional
    public ExamVersionResponse clone(UUID versionId) {
        ExamVersion source = lockVersion(versionId);
        ExamVersion candidate = copyVersion(source);
        candidate.setId(null);
        candidate.setStatus("DRAFT");
        String title = "[Clone] " + source.getTitle();
        candidate.setTitle(title.substring(0, Math.min(title.length(), 255)));
        candidate.setConfigs(validate(candidate, validator.profile(source.getConfigs()), false));
        candidate.setVersionNo(nextVersionNo(source.getExamId()));
        return response(versions.save(candidate));
    }

    private Map<String, Object> validate(ExamVersion version, OfficialExamVersionConfig profile, boolean publishing) {
        version.setTitle(text(version.getTitle(), 255, "title"));
        version.setExamType(text(version.getExamType(), 50, "examType").toUpperCase(Locale.ROOT));
        version.setContentType(text(version.getContentType(), 30, "contentType").toUpperCase(Locale.ROOT));
        if (!Set.of("OFFICIAL", "PRACTICE").contains(version.getExamType())) throw bad("examType must be OFFICIAL or PRACTICE");
        if (version.getContentId() == null || version.getExamStructureId() == null || version.getExamRuleId() == null) {
            throw bad("contentId, examStructureId and examRuleId are required");
        }
        if ("PRACTICE".equals(version.getExamType()) && !Set.of("CHAPTER", "LEARNING_PROGRAM").contains(version.getContentType())) {
            throw bad("PRACTICE retains CHAPTER or LEARNING_PROGRAM source contracts");
        }
        // Stable lock order: parent -> version -> structure -> rule -> criteria.
        ExamStructure structure = structures.findByIdForUpdate(version.getExamStructureId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam structure not found"));
        ExamRule rule = rules.findByIdForUpdate(version.getExamRuleId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam rule not found"));
        ExamCriteria criteria = version.getExamCriteriaId() == null ? null
                : criterias.findByIdForUpdate(version.getExamCriteriaId())
                        .orElseThrow(() -> new ResourceNotFoundException("Exam criteria not found"));
        return validator.validate(version, structure, rule, criteria, profile, publishing);
    }

    private Exam lockExam(UUID id) {
        return exams.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
    }

    private ExamVersion lockVersion(UUID id) {
        UUID examId = versions.findExamIdByVersionId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam version not found"));
        lockExam(examId);
        return versions.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Exam version not found"));
    }

    private Exam requireExam(UUID id) {
        return exams.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
    }

    private ExamVersion requireVersion(UUID id) {
        return versions.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Exam version not found"));
    }

    private void requireDraftWithoutHistory(ExamVersion version) {
        if (!"DRAFT".equals(version.getStatus())) throw conflict("VERSION_IMMUTABLE", "Published and archived versions must be cloned instead");
        if (sessions.existsByExamVersionId(version.getId())) throw conflict("VERSION_HAS_SESSIONS", "Version attempt history cannot be rewritten or deleted");
    }

    private int nextVersionNo(UUID examId) {
        Integer highest = versions.findMaxVersionNoByExamId(examId);
        if (highest == null || highest < 0 || highest == Integer.MAX_VALUE) throw conflict("VERSION_NUMBER_EXHAUSTED", "Cannot allocate a version number");
        return highest + 1;
    }

    private ExamVersion copyVersion(ExamVersion version) {
        return ExamVersion.builder().id(version.getId()).examId(version.getExamId()).versionNo(version.getVersionNo())
                .title(version.getTitle()).examType(version.getExamType()).thumbnailId(version.getThumbnailId())
                .examStructureId(version.getExamStructureId()).examRuleId(version.getExamRuleId()).examCriteriaId(version.getExamCriteriaId())
                .contentType(version.getContentType()).contentId(version.getContentId()).startedAt(version.getStartedAt())
                .endedAt(version.getEndedAt()).status(version.getStatus()).configs(copy(version.getConfigs()))
                .metadata(copy(version.getMetadata())).build();
    }

    private void apply(ExamVersion target, ExamVersion source) {
        target.setTitle(source.getTitle()); target.setExamType(source.getExamType()); target.setThumbnailId(source.getThumbnailId());
        target.setExamStructureId(source.getExamStructureId()); target.setExamRuleId(source.getExamRuleId()); target.setExamCriteriaId(source.getExamCriteriaId());
        target.setContentType(source.getContentType()); target.setContentId(source.getContentId());
        target.setStartedAt(source.getStartedAt()); target.setEndedAt(source.getEndedAt()); target.setMetadata(source.getMetadata());
    }

    private Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : validator.json(value);
    }

    private String text(String value, int limit, String field) {
        if (value == null || value.isBlank() || value.length() > limit) throw bad(field + " is required and must fit its declared length");
        return value.trim();
    }

    private ExamVersionResponse response(ExamVersion version) {
        var profile = validator.profile(version.getConfigs());
        Map<String, Object> config = version.getConfigs() == null ? Map.of() : version.getConfigs();
        return ExamVersionResponse.builder().id(version.getId()).examId(version.getExamId()).versionNo(version.getVersionNo())
                .title(version.getTitle()).examType(version.getExamType()).thumbnailId(version.getThumbnailId())
                .examStructureId(version.getExamStructureId()).examRuleId(version.getExamRuleId()).examCriteriaId(version.getExamCriteriaId())
                .contentType(version.getContentType()).contentId(version.getContentId()).startedAt(version.getStartedAt()).endedAt(version.getEndedAt())
                .status(version.getStatus()).metadata(version.getMetadata()).createdAt(version.getCreatedAt()).updatedAt(version.getUpdatedAt())
                .licenseClass(profile == null ? null : profile.licenseClass()).bankVersion(profile == null ? null : profile.bankVersion())
                .selectionMode(profile == null ? null : profile.selectionMode()).manualQuestionIds(profile == null ? null : profile.manualQuestionIds())
                .matrixPolicyId((String) config.get("matrixPolicyId")).totalQuestions(number(config, "totalQuestions"))
                .durationSeconds(number(config, "durationSeconds")).passingCorrectAnswers(number(config, "passingCorrectAnswers"))
                .criticalQuestionCount(number(config, "criticalQuestionCount")).build();
    }

    private Integer number(Map<String, Object> config, String name) { return config.get(name) instanceof Number value ? value.intValue() : null; }
    private ExaminationException bad(String message) { return new ExaminationException(HttpStatus.BAD_REQUEST, "VERSION_INVALID", message); }
    private ExaminationException conflict(String code, String message) { return new ExaminationException(HttpStatus.CONFLICT, code, message); }
}
