-- Table: exam_rules
-- Service: examination
-- Entities mapped: ExamRule
-- Engine: PostgreSQL
-- Mô tả: Defines the behavioral rules and settings for taking an exam. This includes
-- configurations like duration, retry policies, shuffling, and result visibility.
--
-- This rule set is referenced by exam_versions to control how candidates
-- interact with the exam.

CREATE TABLE exam_rules (
    id                          UUID            NOT NULL    DEFAULT gen_random_uuid(),  -- Primary key for the exam rule
    created_by                  UUID            NOT NULL,                               -- Reference to the identity service for the creator user
    updated_by                  UUID            NULL,                                   -- Reference to the identity service for the last updater user. NULL if never updated.
    title                       VARCHAR(255)    NOT NULL,                               -- Title of the exam rule configuration
    allow_retry                 BOOLEAN         NOT NULL    DEFAULT FALSE,              -- Flag indicating if the candidate can retry the exam
    max_retry                   INT             NOT NULL    DEFAULT 0,                  -- Maximum number of retries allowed (0 means infinite or not applicable depending on allow_retry)
    shuffle_question            BOOLEAN         NOT NULL    DEFAULT FALSE,              -- Flag indicating if questions should be shuffled for each session
    shuffle_option              BOOLEAN         NOT NULL    DEFAULT FALSE,              -- Flag indicating if options within a question should be shuffled
    allow_review                BOOLEAN         NOT NULL    DEFAULT TRUE,               -- Flag indicating if the candidate can review their answers
    allow_skip                  BOOLEAN         NOT NULL    DEFAULT TRUE,               -- Flag indicating if the candidate can skip questions
    auto_submit                 BOOLEAN         NOT NULL    DEFAULT TRUE,               -- Flag indicating if the exam is automatically submitted when time expires
    negative_marking            BOOLEAN         NOT NULL    DEFAULT FALSE,              -- Flag indicating if negative marking is applied for incorrect answers
    show_result_immediately     BOOLEAN         NOT NULL    DEFAULT TRUE,               -- Flag indicating if results are shown to the candidate immediately upon submission
    show_answer_after_submit    BOOLEAN         NOT NULL    DEFAULT TRUE,               -- Flag indicating if correct answers are revealed after submission
    allow_resume                BOOLEAN         NOT NULL    DEFAULT FALSE,              -- Flag indicating if a candidate can resume an incomplete exam session
    allow_pause                 BOOLEAN         NOT NULL    DEFAULT FALSE,              -- Flag indicating if a candidate can pause the exam timer
    time_zone                   VARCHAR(100)    NULL,                                   -- Time zone applicable for the exam scheduling (if any)
    passing_score               DECIMAL(5,2)    NULL,                                   -- Score required to pass the exam. NULL if not applicable.
    duration_seconds            INT             NOT NULL    DEFAULT 0,                  -- Duration of the exam in seconds (0 means untimed)
    status                      VARCHAR(30)     NOT NULL    DEFAULT 'ACTIVE',           -- Status of the rule configuration (e.g., ACTIVE, INACTIVE)
    metadata                    JSONB           NOT NULL    DEFAULT '{}'::jsonb,        -- Extensible metadata for the exam rule
    created_at                  TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was created
    updated_at                  TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was last updated
    deleted_at                  TIMESTAMPTZ     NULL                                    -- Soft delete timestamp. NULL means not deleted.
);

ALTER TABLE exam_rules
    ADD CONSTRAINT pk_exam_rules PRIMARY KEY (id),
    ADD CONSTRAINT ck_exam_rules_max_retry CHECK (max_retry >= 0),
    ADD CONSTRAINT ck_exam_rules_passing_score CHECK (passing_score >= 0),
    ADD CONSTRAINT ck_exam_rules_duration_seconds CHECK (duration_seconds >= 0),
    ADD CONSTRAINT ck_exam_rules_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

COMMENT ON COLUMN exam_rules.id IS 'Primary key for the exam rule';
COMMENT ON COLUMN exam_rules.created_by IS 'Reference to the identity service for the creator user';
COMMENT ON COLUMN exam_rules.updated_by IS 'Reference to the identity service for the last updater user. NULL if never updated.';
COMMENT ON COLUMN exam_rules.title IS 'Title of the exam rule configuration';
COMMENT ON COLUMN exam_rules.allow_retry IS 'Flag indicating if the candidate can retry the exam';
COMMENT ON COLUMN exam_rules.max_retry IS 'Maximum number of retries allowed (0 means infinite or not applicable depending on allow_retry)';
COMMENT ON COLUMN exam_rules.shuffle_question IS 'Flag indicating if questions should be shuffled for each session';
COMMENT ON COLUMN exam_rules.shuffle_option IS 'Flag indicating if options within a question should be shuffled';
COMMENT ON COLUMN exam_rules.allow_review IS 'Flag indicating if the candidate can review their answers';
COMMENT ON COLUMN exam_rules.allow_skip IS 'Flag indicating if the candidate can skip questions';
COMMENT ON COLUMN exam_rules.auto_submit IS 'Flag indicating if the exam is automatically submitted when time expires';
COMMENT ON COLUMN exam_rules.negative_marking IS 'Flag indicating if negative marking is applied for incorrect answers';
COMMENT ON COLUMN exam_rules.show_result_immediately IS 'Flag indicating if results are shown to the candidate immediately upon submission';
COMMENT ON COLUMN exam_rules.show_answer_after_submit IS 'Flag indicating if correct answers are revealed after submission';
COMMENT ON COLUMN exam_rules.allow_resume IS 'Flag indicating if a candidate can resume an incomplete exam session';
COMMENT ON COLUMN exam_rules.allow_pause IS 'Flag indicating if a candidate can pause the exam timer';
COMMENT ON COLUMN exam_rules.time_zone IS 'Time zone applicable for the exam scheduling (if any)';
COMMENT ON COLUMN exam_rules.passing_score IS 'Score required to pass the exam. NULL if not applicable.';
COMMENT ON COLUMN exam_rules.duration_seconds IS 'Duration of the exam in seconds (0 means untimed)';
COMMENT ON COLUMN exam_rules.status IS 'Status of the rule configuration (e.g., ACTIVE, INACTIVE)';
COMMENT ON COLUMN exam_rules.metadata IS 'Extensible metadata for the exam rule';
COMMENT ON COLUMN exam_rules.created_at IS 'Timestamp when the record was created';
COMMENT ON COLUMN exam_rules.updated_at IS 'Timestamp when the record was last updated';
COMMENT ON COLUMN exam_rules.deleted_at IS 'Soft delete timestamp. NULL means not deleted.';

-- Index to support filtering active rules by status
CREATE INDEX ix_exam_rules_status ON exam_rules (status) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_exam_rules_updated_at
    BEFORE UPDATE ON exam_rules
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
