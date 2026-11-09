package dts.com.examination.application.service;

import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.domain.entity.ExamVersion;
import dts.com.examination.domain.repository.ExamCriteriaRepository;
import dts.com.examination.domain.repository.ExamRuleRepository;
import dts.com.examination.domain.repository.ExamStructureRepository;
import dts.com.examination.domain.repository.ExamVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Uses the actual publication validator but never persists the generated snapshot. */
@Service
@RequiredArgsConstructor
public class AdminOfficialVersionPreview {
    private final ExamVersionRepository versions;
    private final ExamStructureRepository structures;
    private final ExamRuleRepository rules;
    private final ExamCriteriaRepository criteria;
    private final OfficialExamVersionValidator validator;
    private final OfficialVersionReviewService reviews;

    public record Validation(UUID examVersionId, String examType, String bankVersion,
                             String licenseClass, String selectionMode, boolean structurallyValid,
                             List<String> validationErrors, boolean approvalBound,
                             boolean approvalGateIntegrated) { }

    @Transactional(readOnly = true)
    public Validation validate(UUID versionId) {
        ExamVersion v = versions.findByIdAndDeletedAtIsNull(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam version not found"));
        var profile = validator.profile(v.getConfigs());
        String bank = profile == null ? null : profile.bankVersion();
        String license = profile == null ? null : profile.licenseClass();
        String mode = profile == null || profile.selectionMode() == null ? null : profile.selectionMode().name();
        if (!"OFFICIAL".equals(v.getExamType())) {
            return new Validation(versionId, v.getExamType(), bank, license, mode,
                    false, List.of("NOT_OFFICIAL_EXAM_VERSION"), false, false);
        }
        if (!"DRAFT".equals(v.getStatus())) {
            return new Validation(versionId, v.getExamType(), bank, license, mode,
                    false, List.of("VERSION_NOT_DRAFT"), false, false);
        }
        try {
            var structure = structures.findById(v.getExamStructureId())
                    .orElseThrow(() -> new ResourceNotFoundException("Exam structure not found"));
            var rule = rules.findById(v.getExamRuleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Exam rule not found"));
            var criterion = v.getExamCriteriaId() == null ? null : criteria.findById(v.getExamCriteriaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Exam criteria not found"));
            // publishing=true exercises stale MANUAL snapshots, actual source, time window,
            // matrix, structure, rule and criteria; no repository.save is called.
            var verified = validator.validate(v, structure, rule, criterion, profile, true);
            boolean approvalBound = reviews.approvalMatches(v, verified);
            return new Validation(versionId, v.getExamType(), bank, license, mode,
                    true, List.of(), approvalBound, true);
        } catch (ExaminationException e) {
            return new Validation(versionId, v.getExamType(), bank, license, mode,
                    false, List.of(e.getCode()), false, false);
        } catch (ResourceNotFoundException e) {
            return new Validation(versionId, v.getExamType(), bank, license, mode,
                    false, List.of("DEPENDENCY_NOT_FOUND"), false, false);
        }
    }
}
