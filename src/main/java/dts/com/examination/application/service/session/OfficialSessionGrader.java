package dts.com.examination.application.service.session;

import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.selection.FrozenExamQuestion;
import dts.com.examination.domain.entity.ExamSessionAnswer;
import dts.com.examination.domain.entity.json.CriteriaConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class OfficialSessionGrader {
    public record QuestionGrade(UUID questionId, boolean correct, BigDecimal score) {}
    public record Grade(BigDecimal score, BigDecimal maxScore, int correctCount, int answeredCount,
            boolean failedCritical, boolean passed, List<QuestionGrade> questions) {}

    /** Grades only the private session snapshot. Does not read Content Builder or mutable admin configuration. */
    public Grade grade(FrozenOfficialSession paper, List<ExamSessionAnswer> answers, boolean violated) {
        var config = paper.configuration().criteria().getCriteria();
        validateRounding(paper.configuration().structure().getSections(), config);
        Map<UUID, ExamSessionAnswer> rows = new HashMap<>();
        answers.forEach(row -> {
            if (row.getQuestionId() == null || rows.putIfAbsent(row.getQuestionId(), row) != null)
                throw invalid("The persisted answer rows are inconsistent");
        });
        if (rows.size() != paper.questions().size()) throw invalid("The persisted paper is incomplete");
        Map<String, BigDecimal> weights = new HashMap<>(), sectionScores = new HashMap<>();
        paper.configuration().structure().getSections().forEach(section ->
                weights.put(section.getCode(), BigDecimal.valueOf(section.getScore())));
        BigDecimal maximum = BigDecimal.ZERO, raw = BigDecimal.ZERO;
        int correct = 0, answered = 0;
        boolean criticalFailed = false;
        Map<UUID, Boolean> correctness = new HashMap<>();
        List<QuestionGrade> grades = new ArrayList<>();
        for (var question : paper.questions()) {
            var row = rows.get(question.id());
            if (row == null) throw invalid("An answer row is missing");
            String section = paper.sectionAssignments().get(question.id());
            BigDecimal weight = weights.get(section);
            if (weight == null) throw invalid("An examination section is missing");
            maximum = maximum.add(weight);
            Set<UUID> selected = selectedIds(row.getSelectedAnswer());
            Set<UUID> valid = new HashSet<>(), key = new HashSet<>();
            question.options().forEach(option -> { valid.add(option.id()); if (option.isCorrect()) key.add(option.id()); });
            if (!valid.containsAll(selected) || key.isEmpty()
                    || !"MULTIPLE_CHOICE".equals(question.type()) && selected.size() > 1)
                throw invalid("A persisted answer is outside the frozen question");
            boolean attempted = !selected.isEmpty();
            boolean right = attempted && selected.equals(key);
            if (attempted) answered++;
            if (right) correct++;
            if (question.isCritical() && !right) criticalFailed = true;
            BigDecimal points = right ? weight : BigDecimal.ZERO;
            correctness.put(question.id(), right);
            grades.add(new QuestionGrade(question.id(), right, points));
            BigDecimal contribution = points;
            if (config.getPenalties() != null) for (var penalty : config.getPenalties()) {
                boolean applies = penalty.getType() == CriteriaConfig.PenaltyConfig.PenaltyType.UNANSWERED && !attempted
                        || penalty.getType() == CriteriaConfig.PenaltyConfig.PenaltyType.WRONG_ANSWER && attempted && !right;
                if (applies) contribution = contribution.subtract(BigDecimal.valueOf(penalty.getDeduct()));
            }
            raw = raw.add(contribution);
            sectionScores.merge(section, contribution, BigDecimal::add);
        }
        raw = raw.max(BigDecimal.ZERO);
        BigDecimal score = raw, maxScore = maximum;
        if (config.getGradingMethod() == CriteriaConfig.GradingMethod.PERCENTAGE) {
            score = raw.multiply(BigDecimal.valueOf(100)).divide(maximum, 12, RoundingMode.HALF_UP);
            maxScore = BigDecimal.valueOf(100);
        } else if (config.getGradingMethod() != CriteriaConfig.GradingMethod.SUM
                && config.getGradingMethod() != CriteriaConfig.GradingMethod.WEIGHTED) {
            throw invalid("The grading method is unsupported");
        }
        if (config.getTotalScore() == null || config.getPassScore() == null
                || maxScore.compareTo(BigDecimal.valueOf(config.getTotalScore())) != 0
                || config.getPassScore() < 0 || config.getPassScore() > config.getTotalScore())
            throw invalid("The frozen criteria do not match the actual score maximum");
        int precision = config.getRounding() == null ? 2 : config.getRounding().getPrecision();
        RoundingMode rounding = config.getRounding() == null ? RoundingMode.HALF_UP
                : RoundingMode.valueOf(config.getRounding().getMode());
        try { score = score.setScale(precision, rounding); }
        catch (ArithmeticException failure) { throw invalid("The configured rounding cannot represent this score"); }
        boolean mandatory = true;
        if (config.getMandatoryRules() != null) for (var rule : config.getMandatoryRules()) {
            List<UUID> ids = rule.getQuestionIds().stream().map(UUID::fromString).toList();
            if (!rows.keySet().containsAll(ids)) throw invalid("A mandatory question is absent from the frozen paper");
            mandatory &= switch (rule.getType()) {
                case MUST_CORRECT -> ids.stream().allMatch(id -> Boolean.TRUE.equals(correctness.get(id)));
                case MUST_ATTEMPT -> ids.stream().allMatch(id -> !selectedIds(rows.get(id).getSelectedAnswer()).isEmpty());
                case AT_LEAST_ONE -> ids.stream().anyMatch(id -> Boolean.TRUE.equals(correctness.get(id)));
                case MAX_WRONG -> throw invalid("MAX_WRONG has no configured limit");
            };
        }
        if (config.getSectionRules() != null) for (var rule : config.getSectionRules()) {
            mandatory &= sectionScores.getOrDefault(rule.getSectionId(), BigDecimal.ZERO)
                    .max(BigDecimal.ZERO).compareTo(BigDecimal.valueOf(rule.getMinScore())) >= 0;
        }
        boolean passed = !violated && !criticalFailed && mandatory
                && correct >= paper.configuration().policy().passingCorrectAnswers()
                && score.compareTo(BigDecimal.valueOf(config.getPassScore())) >= 0;
        return new Grade(score, maxScore, correct, answered, criticalFailed, passed, List.copyOf(grades));
    }

    public static Set<UUID> selectedIds(Map<String, Object> answer) {
        if (answer == null) return Set.of();
        Object value = answer.get("value");
        if (!(value instanceof String text)) throw invalid("A persisted answer must contain option IDs as a string");
        if (text.isBlank()) return Set.of();
        try {
            Set<UUID> result = new LinkedHashSet<>();
            for (String id : text.split(",", -1)) {
                if (!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                        || !result.add(UUID.fromString(id))) throw new IllegalArgumentException();
            }
            return result;
        } catch (RuntimeException failure) { throw invalid("A persisted answer contains invalid option IDs"); }
    }

    /** UNNECESSARY is usable only when every possible score contribution is exactly representable. */
    public static void validateRounding(List<dts.com.examination.domain.entity.Section> sections, CriteriaConfig config) {
        if (config == null) throw invalid("The grading criteria are missing");
        if (config.getRounding() == null) return;
        try {
            RoundingMode mode = RoundingMode.valueOf(config.getRounding().getMode());
            Integer precision = config.getRounding().getPrecision();
            if (precision == null || precision < 0 || precision > 2) throw new IllegalArgumentException();
            if (mode != RoundingMode.UNNECESSARY) return;
            BigDecimal maximum = sections.stream().map(s -> BigDecimal.valueOf(s.getQuestionCount()).multiply(BigDecimal.valueOf(s.getScore())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<BigDecimal> contributions = new ArrayList<>(sections.stream().map(s -> BigDecimal.valueOf(s.getScore())).toList());
            if (config.getPenalties() != null) config.getPenalties().forEach(p -> contributions.add(BigDecimal.valueOf(p.getDeduct())));
            for (BigDecimal amount : contributions) {
                if (config.getGradingMethod() == CriteriaConfig.GradingMethod.PERCENTAGE)
                    amount.multiply(BigDecimal.valueOf(100)).divide(maximum, precision, RoundingMode.UNNECESSARY);
                else amount.setScale(precision, RoundingMode.UNNECESSARY);
            }
        } catch (RuntimeException failure) { throw invalid("The configured rounding cannot represent every possible score"); }
    }

    private static ExaminationException invalid(String message) {
        return new ExaminationException(HttpStatus.CONFLICT, "SESSION_SNAPSHOT_INVALID", message);
    }
}
