-- Table: exam_criteria
-- Service: examination
-- Entities mapped: ExamCriteria
-- Engine: PostgreSQL
-- Mô tả: Defines the evaluation and grading criteria for an exam. It describes how
-- raw scores are mapped to grades or pass/fail outcomes.
--
-- This criteria set is referenced by exam_versions to establish the grading logic
-- for that specific version.

CREATE TABLE exam_criteria (
    id              UUID            NOT NULL    DEFAULT gen_random_uuid(),  -- Primary key for the exam criteria
    created_by      UUID            NOT NULL,                               -- Reference to the identity service for the creator user
    updated_by      UUID            NULL,                                   -- Reference to the identity service for the last updater user. NULL if never updated.
    title           VARCHAR(255)    NOT NULL,                               -- Title of the exam grading criteria
    criteria        JSONB           NOT NULL    DEFAULT '{}'::jsonb,        -- JSON object defining the specific grading logic and thresholds
    status          VARCHAR(30)     NOT NULL    DEFAULT 'ACTIVE',           -- Status of the criteria (e.g., ACTIVE, INACTIVE)
    metadata        JSONB           NOT NULL    DEFAULT '{}'::jsonb,        -- Extensible metadata for the exam criteria
    created_at      TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was created
    updated_at      TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was last updated
    deleted_at      TIMESTAMPTZ     NULL                                    -- Soft delete timestamp. NULL means not deleted.
);

ALTER TABLE exam_criteria
    ADD CONSTRAINT pk_exam_criteria PRIMARY KEY (id),
    ADD CONSTRAINT ck_exam_criteria_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

COMMENT ON COLUMN exam_criteria.id IS 'Primary key for the exam criteria';
COMMENT ON COLUMN exam_criteria.created_by IS 'Reference to the identity service for the creator user';
COMMENT ON COLUMN exam_criteria.updated_by IS 'Reference to the identity service for the last updater user. NULL if never updated.';
COMMENT ON COLUMN exam_criteria.title IS 'Title of the exam grading criteria';
COMMENT ON COLUMN exam_criteria.criteria IS 'JSON object defining the specific grading logic and thresholds';
COMMENT ON COLUMN exam_criteria.status IS 'Status of the criteria (e.g., ACTIVE, INACTIVE)';
COMMENT ON COLUMN exam_criteria.metadata IS 'Extensible metadata for the exam criteria';
COMMENT ON COLUMN exam_criteria.created_at IS 'Timestamp when the record was created';
COMMENT ON COLUMN exam_criteria.updated_at IS 'Timestamp when the record was last updated';
COMMENT ON COLUMN exam_criteria.deleted_at IS 'Soft delete timestamp. NULL means not deleted.';

-- Index to support filtering active criteria by status
CREATE INDEX ix_exam_criteria_status ON exam_criteria (status) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_exam_criteria_updated_at
    BEFORE UPDATE ON exam_criteria
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
