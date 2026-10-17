package dts.com.examination.api.response;

import dts.com.examination.domain.entity.ExamRule;

/** Only participation/display policies; no internal metadata or scoring answer keys. */
public record StudentExamRulesResponse(boolean allowRetry, int maxRetry, int retryIntervalSeconds,
        int durationSeconds, int gracePeriodSeconds, boolean autoSubmit, String navigationMode,
        boolean allowSkip, String reviewMode, boolean allowPause, int maxPauseCount,
        int maxPauseDurationSeconds, boolean allowResume, int resumeTimeoutSeconds,
        boolean shuffleSections, boolean shuffleQuestionsWithinSection, boolean shuffleQuestionsAcrossSections,
        boolean shuffleOptions, String resultReleaseMode, boolean showAnswerAfterSubmit,
        boolean showExplanationAfterSubmit, boolean showQuestionScoreAfterSubmit,
        boolean requireFullscreen, boolean preventTabSwitch, int maxTabSwitchCount, String timeZone) {
    public static StudentExamRulesResponse from(ExamRule rule) {
        return new StudentExamRulesResponse(rule.isAllowRetry(), rule.getMaxRetry(), rule.getRetryIntervalSeconds(),
                rule.getDurationSeconds(), rule.getGracePeriodSeconds(), rule.isAutoSubmit(), rule.getNavigationMode(),
                rule.isAllowSkip(), rule.getReviewMode(), rule.isAllowPause(), rule.getMaxPauseCount(),
                rule.getMaxPauseDurationSeconds(), rule.isAllowResume(), rule.getResumeTimeoutSeconds(),
                rule.isShuffleSections(), rule.isShuffleQuestionsWithinSection(), rule.isShuffleQuestionsAcrossSections(),
                rule.isShuffleOptions(), rule.getResultReleaseMode(), rule.isShowAnswerAfterSubmit(),
                rule.isShowExplanationAfterSubmit(), rule.isShowQuestionScoreAfterSubmit(),
                rule.isRequireFullscreen(), rule.isPreventTabSwitch(), rule.getMaxTabSwitchCount(), rule.getTimeZone());
    }
}
