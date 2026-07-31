package dts.com.examination.domain.entity.json;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriteriaConfig {
    private Integer passScore;
    private Integer totalScore;
    private GradingMethod gradingMethod;
    private RoundingConfig rounding;
    private List<MandatoryRule> mandatoryRules;
    private List<SectionRule> sectionRules;
    private List<PenaltyConfig> penalties;

    public enum GradingMethod {
        SUM, WEIGHTED, PERCENTAGE, BEST_OF, AVERAGE
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoundingConfig {
        private String mode; // e.g., HALF_UP
        private Integer precision;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MandatoryRule {
        private RuleType type;
        private List<String> questionIds;

        public enum RuleType {
            MUST_CORRECT, MUST_ATTEMPT, AT_LEAST_ONE, MAX_WRONG
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SectionRule {
        private String sectionId;
        private Integer minScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PenaltyConfig {
        private PenaltyType type;
        private Double deduct;

        public enum PenaltyType {
            UNANSWERED, WRONG_ANSWER
        }
    }
}
