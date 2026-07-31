package dts.com.examination.api.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ExamRuleResponse {
    private UUID id;
    private String title;
    private boolean allowRetry;
    private int maxRetry;
    private int retryIntervalSeconds;
    private int durationSeconds;
    private int gracePeriodSeconds;
    private boolean autoSubmit;
    private String navigationMode;
    private boolean allowSkip;
    private String reviewMode;
    private boolean allowPause;
    private int maxPauseCount;
    private int maxPauseDurationSeconds;
    private boolean allowResume;
    private int resumeTimeoutSeconds;
    private boolean shuffleSections;
    private boolean shuffleQuestionsWithinSection;
    private boolean shuffleQuestionsAcrossSections;
    private boolean shuffleOptions;
    private String resultReleaseMode;
    private boolean showAnswerAfterSubmit;
    private boolean showExplanationAfterSubmit;
    private boolean showQuestionScoreAfterSubmit;
    private boolean requireFullscreen;
    private boolean preventTabSwitch;
    private int maxTabSwitchCount;
    private String timeZone;
    private String status;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private UUID createdBy;
    private Instant updatedAt;
    private UUID updatedBy;
}
