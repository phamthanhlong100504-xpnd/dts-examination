package dts.com.examination.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.selection.*;
import dts.com.examination.domain.entity.*;
import dts.com.examination.domain.entity.json.CriteriaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OfficialExamVersionValidator {
    private final ExamMatrixPolicyCatalog catalog;
    private final ExamQuestionSelectionService selector;
    private final ExamQuestionSourceReader source;
    private final ExamStructureValidator structures;
    private final ObjectMapper mapper;

    public Map<String, Object> validate(ExamVersion version, ExamStructure structure, ExamRule rule,
            ExamCriteria criteria, OfficialExamVersionConfig requested, boolean publishing) {
        if (version.getStartedAt() != null && version.getEndedAt() != null
                && !version.getStartedAt().isBefore(version.getEndedAt())) {
            throw bad("VERSION_INVALID", "startedAt must be before endedAt");
        }
        if (structure.getDeletedAt() != null || !contains(Set.of("DRAFT", "ACTIVE", "PUBLISHED"), structure.getStatus())) {
            throw conflict("STRUCTURE_NOT_READY", "The selected structure is unavailable");
        }
        structures.totals(structure.getSections());
        if (rule.getDeletedAt() != null || !"ACTIVE".equals(rule.getStatus())) {
            throw conflict("RULE_NOT_READY", "The selected rule is not ACTIVE");
        }
        if (criteria != null && (criteria.getDeletedAt() != null || !"ACTIVE".equals(criteria.getStatus()))) {
            throw conflict("CRITERIA_NOT_READY", "The selected criteria is not ACTIVE");
        }
        if (!"OFFICIAL".equals(version.getExamType())) {
            return new LinkedHashMap<>();
        }
        if (!"BANK_VERSION".equals(version.getContentType()) || version.getContentId() == null) {
            throw bad("VERSION_INVALID", "OFFICIAL uses BANK_VERSION with a source-owned chapter anchor contentId");
        }
        if (version.getStartedAt() == null || version.getEndedAt() == null || criteria == null) {
            throw bad("VERSION_INVALID", "OFFICIAL requires startedAt, endedAt and examCriteriaId");
        }
        if (requested == null || requested.licenseClass() == null || requested.bankVersion() == null
                || requested.selectionMode() == null) {
            throw bad("VERSION_INVALID", "OFFICIAL requires explicit licenseClass, bankVersion and selectionMode");
        }
        String license = requested.licenseClass().trim().toUpperCase(Locale.ROOT);
        ExamMatrixPolicy policy = catalog.require(license, version.getStartedAt());
        catalog.require(license, version.getEndedAt().minusNanos(1));
        if (!policy.bankVersion().equals(requested.bankVersion())) {
            throw bad("VERSION_INVALID", "The source bank does not match the verified licensed policy");
        }
        if (publishing && !Instant.now().isBefore(version.getEndedAt())) {
            throw conflict("VERSION_WINDOW_CLOSED", "An ended examination window cannot be published");
        }
        validateRule(rule, policy);
        ExamQuestionPool pool = source.loadBank(policy.bankVersion(), license);
        source.requireBankAnchor(pool, version.getContentId());
        List<UUID> manualIds = requested.manualQuestionIds();
        ExamQuestionSelection manual = null;
        if (requested.selectionMode() == ExamSelectionMode.AUTO_MATRIX) {
            if (!manualIds.isEmpty()) throw bad("VERSION_INVALID", "AUTO_MATRIX must not contain manual question IDs");
            selector.validateAutomaticReadiness(license, version.getStartedAt(), pool, structure.getSections());
        } else {
            manual = selector.select(license, version.getStartedAt(), pool, structure.getSections(),
                    ExamSelectionMode.MANUAL, manualIds);
        }
        validateCriteria(criteria.getCriteria(), structure.getSections(), requested.selectionMode(), manualIds);
        List<FrozenExamQuestion> content = List.of();
        if (manual != null) {
            content = source.readVerifiedContent(pool, manualIds);
        } else if (publishing) {
            // Check the whole AUTO source, without fixing a paper at publication time.
            source.readVerifiedContent(pool, pool.questions().stream().map(ExamSourceQuestionMetadata::id).toList());
        }
        Map<String, String> revisions = new LinkedHashMap<>();
        content.forEach(question -> revisions.put(question.id().toString(), question.contentVersion()));
        if (publishing && manual != null && !Objects.equals(
                version.getConfigs().get("draftSourceRevisions"), revisions)) {
            throw conflict("SOURCE_CHANGED", "Manual source changed since the last save. Review and save the draft again.");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("officialConfigVersion", 1);
        config.put("licenseClass", license);
        config.put("bankVersion", policy.bankVersion());
        config.put("selectionMode", requested.selectionMode().name());
        config.put("manualQuestionIds", manualIds.stream().map(UUID::toString).toList());
        config.put("matrixPolicyId", policy.id());
        config.put("totalQuestions", policy.totalQuestions());
        config.put("durationSeconds", rule.getDurationSeconds());
        config.put("passingCorrectAnswers", policy.passingCorrectAnswers());
        config.put("criticalQuestionCount", policy.criticalQuestionCount());
        config.put("draftSourceRevisions", revisions);
        if (publishing) {
            config.put("publishedAt", Instant.now().toString());
            config.put("structureSnapshot", json(structure));
            config.put("ruleSnapshot", json(rule));
            config.put("criteriaSnapshot", json(criteria));
            config.put("matrixPolicySnapshot", json(policy));
            if (manual != null) {
                config.put("manualQuestionsSnapshot", mapper.convertValue(content, new TypeReference<List<Map<String, Object>>>() {}));
                config.put("manualSectionAssignments", manual.sectionCodeByQuestionId().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue)));
            }
        }
        return config;
    }

    public OfficialExamVersionConfig profile(Map<String, Object> config) {
        if (config == null || !config.containsKey("selectionMode")) return null;
        try {
            List<UUID> ids = new ArrayList<>();
            Object rawIds = config.get("manualQuestionIds");
            if (!(rawIds instanceof List<?> raw)) throw new IllegalArgumentException();
            for (Object id : raw) ids.add(UUID.fromString((String) id));
            return new OfficialExamVersionConfig((String) config.get("licenseClass"), (String) config.get("bankVersion"),
                    ExamSelectionMode.valueOf((String) config.get("selectionMode")), ids);
        } catch (IllegalArgumentException | ClassCastException | NullPointerException error) {
            throw conflict("VERSION_CONFIG_INVALID", "Stored official configuration is invalid");
        }
    }

    public Map<String, Object> json(Object value) {
        return mapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private void validateRule(ExamRule rule, ExamMatrixPolicy policy) {
        if (rule.getDurationSeconds() != policy.durationMinutes() * 60) {
            throw conflict("RULE_POLICY_MISMATCH", "Rule duration must match the licensed official policy");
        }
        if (rule.getMaxRetry() < 0 || rule.getRetryIntervalSeconds() < 0 || rule.getGracePeriodSeconds() < 0
                || rule.getMaxPauseCount() < 0 || rule.getMaxPauseDurationSeconds() < 0
                || rule.getResumeTimeoutSeconds() < 0 || rule.getMaxTabSwitchCount() < 0
                || (!rule.isAllowRetry() && rule.getMaxRetry() != 0)
                || (rule.isAllowPause() && (!rule.isAllowResume() || rule.getMaxPauseCount() < 1
                        || rule.getMaxPauseDurationSeconds() < 1))
                || (rule.isAllowResume() && rule.getResumeTimeoutSeconds() < 1)
                || (!rule.isPreventTabSwitch() && rule.getMaxTabSwitchCount() != 0)
                || (rule.isShuffleQuestionsAcrossSections()
                        && (rule.isShuffleSections() || rule.isShuffleQuestionsWithinSection()))) {
            throw conflict("RULE_INVALID", "The rule contains incompatible limits or policies");
        }
        if (!contains(Set.of("FREE", "SEQUENTIAL"), rule.getNavigationMode())
                || !contains(Set.of("NONE", "CURRENT_SECTION", "ALL"), rule.getReviewMode())
                || !contains(Set.of("IMMEDIATE", "AFTER_SUBMIT", "AFTER_EXAM_END", "MANUAL"), rule.getResultReleaseMode())) {
            throw conflict("RULE_INVALID", "The rule contains an unsupported navigation, review or release mode");
        }
        if (rule.getTimeZone() != null) {
            try { ZoneId.of(rule.getTimeZone()); }
            catch (java.time.DateTimeException error) { throw conflict("RULE_INVALID", "Rule time zone is invalid"); }
        }
    }

    private boolean contains(Set<String> values, String value) { return value != null && values.contains(value); }

    private void validateCriteria(CriteriaConfig config, List<Section> sections, ExamSelectionMode mode,
            List<UUID> manualIds) {
        int maxScore = structures.totals(sections).maxScore();
        if (config == null || config.getGradingMethod() == null || config.getTotalScore() == null
                || config.getTotalScore() < 1 || config.getPassScore() == null || config.getPassScore() < 0
                || config.getPassScore() > config.getTotalScore() || maxScore < 1) {
            throw conflict("CRITERIA_INVALID", "Criteria score bounds and method must be valid");
        }
        if (Set.of(CriteriaConfig.GradingMethod.BEST_OF, CriteriaConfig.GradingMethod.AVERAGE).contains(config.getGradingMethod())) {
            throw conflict("CRITERIA_UNSUPPORTED", "The selected grading method has no defined official attempt aggregation rule");
        }
        int expectedMax = config.getGradingMethod() == CriteriaConfig.GradingMethod.PERCENTAGE ? 100 : maxScore;
        if (config.getTotalScore() != expectedMax) {
            throw conflict("CRITERIA_STRUCTURE_MISMATCH", "Criteria totalScore does not match structure weights and grading method");
        }
        if (sections.stream().anyMatch(section -> section.getScore() > 999)) {
            throw conflict("CRITERIA_INVALID", "A question weight exceeds the supported score storage range");
        }
        if (config.getRounding() != null) {
            var rounding = config.getRounding();
            try {
                RoundingMode.valueOf(rounding.getMode());
                if (rounding.getPrecision() == null || rounding.getPrecision() < 0 || rounding.getPrecision() > 2) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException | NullPointerException error) {
                throw conflict("CRITERIA_INVALID", "Rounding requires a supported mode and precision from zero to two");
            }
        }
        Map<String, Integer> sectionMax = new HashMap<>();
        sections.forEach(section -> sectionMax.put(section.getCode(), section.getQuestionCount() * section.getScore()));
        if (config.getSectionRules() != null) {
            Set<String> unique = new HashSet<>();
            for (var rule : config.getSectionRules()) {
                if (rule == null || !sectionMax.containsKey(rule.getSectionId()) || !unique.add(rule.getSectionId())
                        || rule.getMinScore() == null || rule.getMinScore() < 0 || rule.getMinScore() > sectionMax.get(rule.getSectionId())) {
                    throw conflict("CRITERIA_STRUCTURE_MISMATCH", "Section minimum scores must reference existing section codes and weights");
                }
            }
        }
        if (config.getPenalties() != null) {
            for (var penalty : config.getPenalties()) {
                if (penalty == null || penalty.getType() == null || penalty.getDeduct() == null
                        || !Double.isFinite(penalty.getDeduct()) || penalty.getDeduct() < 0 || penalty.getDeduct() > 999.99) {
                    throw conflict("CRITERIA_INVALID", "Penalty deductions must be finite and within the supported score range");
                }
            }
        }
        if (config.getMandatoryRules() != null) {
            for (var rule : config.getMandatoryRules()) {
                if (rule == null || rule.getType() == null || rule.getQuestionIds() == null || rule.getQuestionIds().isEmpty()) {
                    throw conflict("CRITERIA_INVALID", "Mandatory rules require a type and question IDs");
                }
                if (rule.getType() == CriteriaConfig.MandatoryRule.RuleType.MAX_WRONG || mode == ExamSelectionMode.AUTO_MATRIX) {
                    throw conflict("CRITERIA_UNSUPPORTED", "MAX_WRONG needs a limit; fixed mandatory IDs need a MANUAL paper");
                }
                Set<UUID> unique = new HashSet<>();
                for (String id : rule.getQuestionIds()) {
                    UUID questionId;
                    try {
                        if (id == null || !id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                            throw new IllegalArgumentException();
                        }
                        questionId = UUID.fromString(id);
                    } catch (IllegalArgumentException error) { throw conflict("CRITERIA_INVALID", "Mandatory question IDs must be UUIDs"); }
                    if (!manualIds.contains(questionId) || !unique.add(questionId)) {
                        throw conflict("CRITERIA_STRUCTURE_MISMATCH", "Mandatory question IDs must be unique and belong to the manual paper");
                    }
                }
            }
        }
        try {
            dts.com.examination.application.service.session.OfficialSessionGrader.validateRounding(sections, config);
        } catch (ExaminationException failure) {
            throw conflict("CRITERIA_INVALID", failure.getMessage());
        }
    }

    private ExaminationException bad(String code, String message) { return new ExaminationException(HttpStatus.BAD_REQUEST, code, message); }
    private ExaminationException conflict(String code, String message) { return new ExaminationException(HttpStatus.CONFLICT, code, message); }
}
