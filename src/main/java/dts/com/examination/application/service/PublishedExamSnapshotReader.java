package dts.com.examination.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.selection.*;
import dts.com.examination.domain.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PublishedExamSnapshotReader {
    private final ObjectMapper mapper;
    private final OfficialExamVersionValidator versions;
    private final ExamStructureValidator structures;

    public PublishedExamSnapshot read(ExamVersion version) {
        try {
            if (version.getDeletedAt() != null || !"OFFICIAL".equals(version.getExamType())
                    || !"BANK_VERSION".equals(version.getContentType()) || version.getStatus() == null
                    || !Set.of("PUBLISHED", "ARCHIVED").contains(version.getStatus())
                    || version.getStartedAt() == null || version.getEndedAt() == null
                    || !version.getStartedAt().isBefore(version.getEndedAt())) throw new IllegalArgumentException();
            var config = version.getConfigs();
            if (config == null || number(config.get("officialConfigVersion")) != 1
                    || config.get("publishedAt") == null) throw new IllegalArgumentException();
            var profile = versions.profile(config);
            var structure = mapper.convertValue(config.get("structureSnapshot"), ExamStructure.class);
            var rule = mapper.convertValue(config.get("ruleSnapshot"), ExamRule.class);
            var criteria = mapper.convertValue(config.get("criteriaSnapshot"), ExamCriteria.class);
            var policy = mapper.convertValue(config.get("matrixPolicySnapshot"), ExamMatrixPolicy.class);
            if (profile == null || structure == null || rule == null || criteria == null || policy == null
                    || version.getExamStructureId() == null || version.getExamRuleId() == null || version.getExamCriteriaId() == null
                    || !Objects.equals(version.getExamStructureId(), structure.getId())
                    || !Objects.equals(version.getExamRuleId(), rule.getId())
                    || !Objects.equals(version.getExamCriteriaId(), criteria.getId())
                    || !Objects.equals(profile.licenseClass(), policy.licenseClass())
                    || !Objects.equals(profile.bankVersion(), policy.bankVersion())
                    || !Objects.equals(config.get("matrixPolicyId"), policy.id())
                    || policy.totalQuestions() < 1 || policy.durationMinutes() < 1
                    || policy.passingCorrectAnswers() < 1 || policy.passingCorrectAnswers() > policy.totalQuestions()
                    || policy.criticalQuestionCount() < 0 || policy.criticalQuestionCount() > policy.totalQuestions()
                    || structures.totals(structure.getSections()).totalQuestions() != policy.totalQuestions()
                    || number(config.get("totalQuestions")) != policy.totalQuestions()
                    || number(config.get("durationSeconds")) != rule.getDurationSeconds()
                    || rule.getDurationSeconds() != policy.durationMinutes() * 60
                    || number(config.get("passingCorrectAnswers")) != policy.passingCorrectAnswers()
                    || number(config.get("criticalQuestionCount")) != policy.criticalQuestionCount()
                    || rule.getMaxRetry() < 0 || rule.getRetryIntervalSeconds() < 0
                    || criteria.getCriteria() == null || criteria.getCriteria().getTotalScore() == null
                    || criteria.getCriteria().getPassScore() == null || criteria.getCriteria().getGradingMethod() == null
                    || criteria.getCriteria().getPassScore() < 0
                    || criteria.getCriteria().getPassScore() > criteria.getCriteria().getTotalScore()) throw new IllegalArgumentException();
            if (profile.selectionMode() == ExamSelectionMode.AUTO_MATRIX && !profile.manualQuestionIds().isEmpty()) throw new IllegalArgumentException();
            if (profile.selectionMode() == ExamSelectionMode.MANUAL
                    && (profile.manualQuestionIds().size() != policy.totalQuestions()
                    || new java.util.HashSet<>(profile.manualQuestionIds()).size() != policy.totalQuestions()
                    || !(config.get("manualQuestionsSnapshot") instanceof java.util.List<?> questions) || questions.size() != policy.totalQuestions()
                    || !(config.get("manualSectionAssignments") instanceof java.util.Map<?, ?> assignments) || assignments.size() != policy.totalQuestions())) throw new IllegalArgumentException();
            return new PublishedExamSnapshot(profile, policy, structure, rule, criteria);
        } catch (RuntimeException failure) {
            throw new ExaminationException(HttpStatus.CONFLICT, "VERSION_NOT_READY", "The published examination snapshot is missing or inconsistent");
        }
    }

    private int number(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException();
        return new java.math.BigDecimal(number.toString()).intValueExact();
    }
}
