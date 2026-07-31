package dts.com.examination.api.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateExamRuleRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private Boolean allowRetry;

    @Min(value = 0, message = "maxRetry must be at least 0")
    private Integer maxRetry;

    @Min(value = 0, message = "retryIntervalSeconds must be at least 0")
    private Integer retryIntervalSeconds;

    @Min(value = 0, message = "durationSeconds must be at least 0")
    private Integer durationSeconds;

    @Min(value = 0, message = "gracePeriodSeconds must be at least 0")
    private Integer gracePeriodSeconds;

    private Boolean autoSubmit;

    @Pattern(regexp = "FREE|SEQUENTIAL", message = "navigationMode must be FREE or SEQUENTIAL")
    private String navigationMode;

    private Boolean allowSkip;

    @Pattern(regexp = "NONE|CURRENT_SECTION|ALL", message = "reviewMode must be NONE, CURRENT_SECTION, or ALL")
    private String reviewMode;

    private Boolean allowPause;

    @Min(value = 0, message = "maxPauseCount must be at least 0")
    private Integer maxPauseCount;

    @Min(value = 0, message = "maxPauseDurationSeconds must be at least 0")
    private Integer maxPauseDurationSeconds;

    private Boolean allowResume;

    @Min(value = 0, message = "resumeTimeoutSeconds must be at least 0")
    private Integer resumeTimeoutSeconds;

    private Boolean shuffleSections;

    private Boolean shuffleQuestionsWithinSection;

    private Boolean shuffleQuestionsAcrossSections;

    private Boolean shuffleOptions;

    @Pattern(regexp = "IMMEDIATE|AFTER_SUBMIT|AFTER_EXAM_END|MANUAL", message = "resultReleaseMode must be IMMEDIATE, AFTER_SUBMIT, AFTER_EXAM_END, or MANUAL")
    private String resultReleaseMode;

    private Boolean showAnswerAfterSubmit;

    private Boolean showExplanationAfterSubmit;

    private Boolean showQuestionScoreAfterSubmit;

    private Boolean requireFullscreen;

    private Boolean preventTabSwitch;

    @Min(value = 0, message = "maxTabSwitchCount must be at least 0")
    private Integer maxTabSwitchCount;

    private String timeZone;

    private Map<String, Object> metadata;
}
