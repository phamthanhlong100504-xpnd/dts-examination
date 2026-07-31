-- Table: exam_rules
-- Service: examination
-- Entities mapped: ExamRule
-- Engine: PostgreSQL
-- Mô tả: Defines the complete behavior and configuration for an exam session.
--
-- ExamRule controls all aspects of an exam: retry policy, time limits, navigation,
-- question shuffling, result display, proctoring settings, and pause/resume behavior.
-- Each ExamVersion references one ExamRule to establish the exam session behavior.

CREATE TABLE exam_rules (
    id                                  UUID            NOT NULL    DEFAULT gen_random_uuid(),  -- Primary key for the exam rule
    created_by                          UUID            NOT NULL,                               -- Reference to the identity service for the creator user
    updated_by                          UUID            NULL,                                   -- Reference to the identity service for the last updater user. NULL if never updated.
    title                               VARCHAR(255)    NOT NULL,                               -- Unique title for the exam rule
    allow_retry                         BOOLEAN         NOT NULL    DEFAULT false,              -- Whether retaking the exam is allowed
    max_retry                           INT             NOT NULL    DEFAULT 0,                  -- Maximum number of retry attempts
    retry_interval_seconds              INT             NOT NULL    DEFAULT 0,                  -- Minimum wait time (seconds) between retries
    duration_seconds                    INT             NOT NULL    DEFAULT 0,                  -- Total exam duration in seconds
    grace_period_seconds                INT             NOT NULL    DEFAULT 0,                  -- Grace period (seconds) before auto-submit after time expires
    auto_submit                         BOOLEAN         NOT NULL    DEFAULT true,               -- Whether to automatically submit when time expires
    navigation_mode                     VARCHAR(30)     NOT NULL    DEFAULT 'FREE',             -- Navigation mode: FREE or SEQUENTIAL
    allow_skip                          BOOLEAN         NOT NULL    DEFAULT true,               -- Whether skipping questions is allowed
    review_mode                         VARCHAR(30)     NOT NULL    DEFAULT 'ALL',              -- Review mode: NONE, CURRENT_SECTION, ALL
    allow_pause                         BOOLEAN         NOT NULL    DEFAULT false,              -- Whether pausing the exam is allowed
    max_pause_count                     INT             NOT NULL    DEFAULT 0,                  -- Maximum number of pauses allowed
    max_pause_duration_seconds          INT             NOT NULL    DEFAULT 0,                  -- Maximum total pause duration in seconds
    allow_resume                        BOOLEAN         NOT NULL    DEFAULT false,              -- Whether resuming a paused exam is allowed
    resume_timeout_seconds              INT             NOT NULL    DEFAULT 0,                  -- Maximum time (seconds) allowed to resume before auto-forfeit
    shuffle_sections                    BOOLEAN         NOT NULL    DEFAULT false,              -- Whether to shuffle section order
    shuffle_questions_within_section    BOOLEAN         NOT NULL    DEFAULT false,              -- Whether to shuffle questions within each section
    shuffle_questions_across_sections   BOOLEAN         NOT NULL    DEFAULT false,              -- Whether to shuffle questions across all sections
    shuffle_options                     BOOLEAN         NOT NULL    DEFAULT false,              -- Whether to shuffle answer options for each question
    result_release_mode                 VARCHAR(30)     NOT NULL    DEFAULT 'IMMEDIATE',        -- When to release results: IMMEDIATE, AFTER_SUBMIT, AFTER_EXAM_END, MANUAL
    show_answer_after_submit            BOOLEAN         NOT NULL    DEFAULT true,               -- Whether to show correct answers after submission
    show_explanation_after_submit       BOOLEAN         NOT NULL    DEFAULT false,              -- Whether to show explanations after submission
    show_question_score_after_submit    BOOLEAN         NOT NULL    DEFAULT false,              -- Whether to show score per question after submission
    require_fullscreen                  BOOLEAN         NOT NULL    DEFAULT false,              -- Whether fullscreen mode is required
    prevent_tab_switch                  BOOLEAN         NOT NULL    DEFAULT false,              -- Whether tab switching is detected and restricted
    max_tab_switch_count                INT             NOT NULL    DEFAULT 0,                  -- Maximum allowed tab switches before action is taken
    time_zone                           VARCHAR(100)    NULL,                                   -- IANA time zone for the exam. NULL means server default.
    status                              VARCHAR(30)     NOT NULL    DEFAULT 'ACTIVE',           -- Status of the rule: ACTIVE, INACTIVE
    metadata                            JSONB           NOT NULL    DEFAULT '{}'::jsonb,        -- Extensible metadata for the exam rule
    created_at                          TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was created
    updated_at                          TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was last updated
    deleted_at                          TIMESTAMPTZ     NULL                                    -- Soft delete timestamp. NULL means not deleted.
);

ALTER TABLE exam_rules
    ADD CONSTRAINT pk_exam_rules PRIMARY KEY (id),
    ADD CONSTRAINT uq_exam_rules_title UNIQUE (title),
    ADD CONSTRAINT ck_exam_rules_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    ADD CONSTRAINT ck_exam_rules_navigation_mode CHECK (navigation_mode IN ('FREE', 'SEQUENTIAL')),
    ADD CONSTRAINT ck_exam_rules_review_mode CHECK (review_mode IN ('NONE', 'CURRENT_SECTION', 'ALL')),
    ADD CONSTRAINT ck_exam_rules_result_release_mode CHECK (result_release_mode IN ('IMMEDIATE', 'AFTER_SUBMIT', 'AFTER_EXAM_END', 'MANUAL')),
    ADD CONSTRAINT ck_exam_rules_max_retry CHECK (max_retry >= 0),
    ADD CONSTRAINT ck_exam_rules_retry_interval CHECK (retry_interval_seconds >= 0),
    ADD CONSTRAINT ck_exam_rules_duration CHECK (duration_seconds >= 0),
    ADD CONSTRAINT ck_exam_rules_grace_period CHECK (grace_period_seconds >= 0),
    ADD CONSTRAINT ck_exam_rules_max_pause_count CHECK (max_pause_count >= 0),
    ADD CONSTRAINT ck_exam_rules_max_pause_duration CHECK (max_pause_duration_seconds >= 0),
    ADD CONSTRAINT ck_exam_rules_resume_timeout CHECK (resume_timeout_seconds >= 0),
    ADD CONSTRAINT ck_exam_rules_max_tab_switch CHECK (max_tab_switch_count >= 0);

COMMENT ON COLUMN exam_rules.id IS 'Primary key for the exam rule';
COMMENT ON COLUMN exam_rules.created_by IS 'Reference to the identity service for the creator user';
COMMENT ON COLUMN exam_rules.updated_by IS 'Reference to the identity service for the last updater user. NULL if never updated.';
COMMENT ON COLUMN exam_rules.title IS 'Unique title for the exam rule';
COMMENT ON COLUMN exam_rules.allow_retry IS 'Whether retaking the exam is allowed';
COMMENT ON COLUMN exam_rules.max_retry IS 'Maximum number of retry attempts';
COMMENT ON COLUMN exam_rules.retry_interval_seconds IS 'Minimum wait time (seconds) between retries';
COMMENT ON COLUMN exam_rules.duration_seconds IS 'Total exam duration in seconds';
COMMENT ON COLUMN exam_rules.grace_period_seconds IS 'Grace period (seconds) before auto-submit after time expires';
COMMENT ON COLUMN exam_rules.auto_submit IS 'Whether to automatically submit when time expires';
COMMENT ON COLUMN exam_rules.navigation_mode IS 'Navigation mode: FREE (jump freely) or SEQUENTIAL (follow order)';
COMMENT ON COLUMN exam_rules.allow_skip IS 'Whether skipping questions is allowed';
COMMENT ON COLUMN exam_rules.review_mode IS 'Review mode: NONE, CURRENT_SECTION, or ALL';
COMMENT ON COLUMN exam_rules.allow_pause IS 'Whether pausing the exam is allowed';
COMMENT ON COLUMN exam_rules.max_pause_count IS 'Maximum number of pauses allowed';
COMMENT ON COLUMN exam_rules.max_pause_duration_seconds IS 'Maximum total pause duration in seconds';
COMMENT ON COLUMN exam_rules.allow_resume IS 'Whether resuming a paused exam is allowed';
COMMENT ON COLUMN exam_rules.resume_timeout_seconds IS 'Maximum time (seconds) allowed to resume before auto-forfeit';
COMMENT ON COLUMN exam_rules.shuffle_sections IS 'Whether to shuffle section order';
COMMENT ON COLUMN exam_rules.shuffle_questions_within_section IS 'Whether to shuffle questions within each section';
COMMENT ON COLUMN exam_rules.shuffle_questions_across_sections IS 'Whether to shuffle questions across all sections';
COMMENT ON COLUMN exam_rules.shuffle_options IS 'Whether to shuffle answer options for each question';
COMMENT ON COLUMN exam_rules.result_release_mode IS 'When to release results: IMMEDIATE, AFTER_SUBMIT, AFTER_EXAM_END, MANUAL';
COMMENT ON COLUMN exam_rules.show_answer_after_submit IS 'Whether to show correct answers after submission';
COMMENT ON COLUMN exam_rules.show_explanation_after_submit IS 'Whether to show explanations after submission';
COMMENT ON COLUMN exam_rules.show_question_score_after_submit IS 'Whether to show score per question after submission';
COMMENT ON COLUMN exam_rules.require_fullscreen IS 'Whether fullscreen mode is required';
COMMENT ON COLUMN exam_rules.prevent_tab_switch IS 'Whether tab switching is detected and restricted';
COMMENT ON COLUMN exam_rules.max_tab_switch_count IS 'Maximum allowed tab switches before action is taken';
COMMENT ON COLUMN exam_rules.time_zone IS 'IANA time zone for the exam. NULL means server default.';
COMMENT ON COLUMN exam_rules.status IS 'Status of the rule: ACTIVE or INACTIVE';
COMMENT ON COLUMN exam_rules.metadata IS 'Extensible metadata for the exam rule';
COMMENT ON COLUMN exam_rules.created_at IS 'Timestamp when the record was created';
COMMENT ON COLUMN exam_rules.updated_at IS 'Timestamp when the record was last updated';
COMMENT ON COLUMN exam_rules.deleted_at IS 'Soft delete timestamp. NULL means not deleted.';

-- Index to support filtering active rules by status
CREATE INDEX ix_exam_rules_status ON exam_rules (status) WHERE deleted_at IS NULL;

-- Index to support searching by title
CREATE INDEX ix_exam_rules_title ON exam_rules (title) WHERE deleted_at IS NULL;

-- Trigger: auto-update updated_at on row modification
CREATE TRIGGER trg_exam_rules_updated_at
    BEFORE UPDATE ON exam_rules
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
