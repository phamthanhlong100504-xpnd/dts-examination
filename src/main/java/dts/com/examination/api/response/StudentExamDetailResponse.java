package dts.com.examination.api.response;

import java.time.Instant;

public record StudentExamDetailResponse(StudentExamSummaryResponse exam, String instructions,
        StudentExamRulesResponse rules, GradingSummary grading, Instant serverTime) {
    public record GradingSummary(int totalScore, int passScore, String gradingMethod,
            int passingCorrectAnswers, int criticalQuestionCount) {}
}
