package dts.com.examination.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "exam_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamRule extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String title;

    @Column(name = "allow_retry", nullable = false)
    private boolean allowRetry;

    @Column(name = "max_retry", nullable = false)
    private int maxRetry;

    @Column(name = "retry_interval_seconds", nullable = false)
    private int retryIntervalSeconds;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "grace_period_seconds", nullable = false)
    private int gracePeriodSeconds;

    @Column(name = "auto_submit", nullable = false)
    private boolean autoSubmit;

    @Column(name = "navigation_mode", nullable = false, length = 30)
    private String navigationMode;

    @Column(name = "allow_skip", nullable = false)
    private boolean allowSkip;

    @Column(name = "review_mode", nullable = false, length = 30)
    private String reviewMode;

    @Column(name = "allow_pause", nullable = false)
    private boolean allowPause;

    @Column(name = "max_pause_count", nullable = false)
    private int maxPauseCount;

    @Column(name = "max_pause_duration_seconds", nullable = false)
    private int maxPauseDurationSeconds;

    @Column(name = "allow_resume", nullable = false)
    private boolean allowResume;

    @Column(name = "resume_timeout_seconds", nullable = false)
    private int resumeTimeoutSeconds;

    @Column(name = "shuffle_sections", nullable = false)
    private boolean shuffleSections;

    @Column(name = "shuffle_questions_within_section", nullable = false)
    private boolean shuffleQuestionsWithinSection;

    @Column(name = "shuffle_questions_across_sections", nullable = false)
    private boolean shuffleQuestionsAcrossSections;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    @Column(name = "result_release_mode", nullable = false, length = 30)
    private String resultReleaseMode;

    @Column(name = "show_answer_after_submit", nullable = false)
    private boolean showAnswerAfterSubmit;

    @Column(name = "show_explanation_after_submit", nullable = false)
    private boolean showExplanationAfterSubmit;

    @Column(name = "show_question_score_after_submit", nullable = false)
    private boolean showQuestionScoreAfterSubmit;

    @Column(name = "require_fullscreen", nullable = false)
    private boolean requireFullscreen;

    @Column(name = "prevent_tab_switch", nullable = false)
    private boolean preventTabSwitch;

    @Column(name = "max_tab_switch_count", nullable = false)
    private int maxTabSwitchCount;

    @Column(name = "time_zone", length = 100)
    private String timeZone;

    @Column(nullable = false, length = 30)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
