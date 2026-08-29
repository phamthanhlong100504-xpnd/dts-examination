package dts.com.examination.api.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CreateExamRuleRequest {

    @NotBlank(message = "Title must not be blank")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotNull(message = "allowRetry must not be null")
    private Boolean allowRetry;

    @NotNull(message = "maxRetry must not be null")
    @Min(value = 0, message = "maxRetry must be at least 0")
    private Integer maxRetry;

    @NotNull(message = "retryIntervalSeconds must not be null")
    @Min(value = 0, message = "retryIntervalSeconds must be at least 0")
    private Integer retryIntervalSeconds;

    @NotNull(message = "durationSeconds must not be null")
    @Min(value = 0, message = "durationSeconds must be at least 0")
    private Integer durationSeconds;

    @NotNull(message = "gracePeriodSeconds must not be null")
    @Min(value = 0, message = "gracePeriodSeconds must be at least 0")
    private Integer gracePeriodSeconds;

    @NotNull(message = "autoSubmit must not be null")
    private Boolean autoSubmit;

    @NotNull(message = "navigationMode must not be null")
    @Pattern(regexp = "FREE|SEQUENTIAL", message = "navigationMode must be FREE or SEQUENTIAL")
    private String navigationMode;

    @NotNull(message = "allowSkip must not be null")
    private Boolean allowSkip;

    @NotNull(message = "reviewMode must not be null")
    @Pattern(regexp = "NONE|CURRENT_SECTION|ALL", message = "reviewMode must be NONE, CURRENT_SECTION, or ALL")
    private String reviewMode;

    @NotNull(message = "allowPause must not be null")
    private Boolean allowPause;

    @NotNull(message = "maxPauseCount must not be null")
    @Min(value = 0, message = "maxPauseCount must be at least 0")
    private Integer maxPauseCount;

    @NotNull(message = "maxPauseDurationSeconds must not be null")
    @Min(value = 0, message = "maxPauseDurationSeconds must be at least 0")
    private Integer maxPauseDurationSeconds;

    @NotNull(message = "allowResume must not be null")
    private Boolean allowResume;

    @NotNull(message = "resumeTimeoutSeconds must not be null")
    @Min(value = 0, message = "resumeTimeoutSeconds must be at least 0")
    private Integer resumeTimeoutSeconds;

    @NotNull(message = "shuffleSections must not be null")
    private Boolean shuffleSections;

    @NotNull(message = "shuffleQuestionsWithinSection must not be null")
    private Boolean shuffleQuestionsWithinSection;

    @NotNull(message = "shuffleQuestionsAcrossSections must not be null")
    private Boolean shuffleQuestionsAcrossSections;

    @NotNull(message = "shuffleOptions must not be null")
    private Boolean shuffleOptions;

    @NotNull(message = "resultReleaseMode must not be null")
    @Pattern(regexp = "IMMEDIATE|IMMEDIATELY|AFTER_SUBMIT|AFTER_EXAM_END|MANUAL", message = "resultReleaseMode must be IMMEDIATE, IMMEDIATELY, AFTER_SUBMIT, AFTER_EXAM_END, or MANUAL")
    private String resultReleaseMode;

    @NotNull(message = "showAnswerAfterSubmit must not be null")
    private Boolean showAnswerAfterSubmit;

    @NotNull(message = "showExplanationAfterSubmit must not be null")
    private Boolean showExplanationAfterSubmit;

    @NotNull(message = "showQuestionScoreAfterSubmit must not be null")
    private Boolean showQuestionScoreAfterSubmit;

    @NotNull(message = "requireFullscreen must not be null")
    private Boolean requireFullscreen;

    @NotNull(message = "preventTabSwitch must not be null")
    private Boolean preventTabSwitch;

    @NotNull(message = "maxTabSwitchCount must not be null")
    @Min(value = 0, message = "maxTabSwitchCount must be at least 0")
    private Integer maxTabSwitchCount;

    private String timeZone;

    private Map<String, Object> metadata;
}
