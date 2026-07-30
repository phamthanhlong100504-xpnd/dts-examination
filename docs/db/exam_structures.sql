-- Table: exam_structures
-- Service: examination
-- Entities mapped: ExamStructure
-- Engine: PostgreSQL
-- Mô tả: Defines the layout and sections of an exam. It describes how questions
-- or blocks of content are organized within the exam (e.g., Part 1, Part 2).
--
-- This structure is referenced by exam_versions to apply a specific layout
-- to a given version of an exam.

CREATE TABLE exam_structures (
    id              UUID            NOT NULL    DEFAULT gen_random_uuid(),  -- Primary key for the exam structure
    created_by      UUID            NOT NULL,                               -- Reference to the identity service for the creator user
    updated_by      UUID            NULL,                                   -- Reference to the identity service for the last updater user. NULL if never updated.
    title           VARCHAR(255)    NOT NULL,                               -- Title of the exam structure
    sections        JSONB           NOT NULL    DEFAULT '[]'::jsonb,        -- JSON array defining the sections and layout of the exam
    status          VARCHAR(30)     NOT NULL    DEFAULT 'ACTIVE',           -- Status of the structure (e.g., ACTIVE, INACTIVE)
    metadata        JSONB           NOT NULL    DEFAULT '{}'::jsonb,        -- Extensible metadata for the exam structure
    created_at      TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was created
    updated_at      TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was last updated
    deleted_at      TIMESTAMPTZ     NULL                                    -- Soft delete timestamp. NULL means not deleted.
);

ALTER TABLE exam_structures
    ADD CONSTRAINT pk_exam_structures PRIMARY KEY (id),
    ADD CONSTRAINT ck_exam_structures_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

COMMENT ON COLUMN exam_structures.id IS 'Primary key for the exam structure';
COMMENT ON COLUMN exam_structures.created_by IS 'Reference to the identity service for the creator user';
COMMENT ON COLUMN exam_structures.updated_by IS 'Reference to the identity service for the last updater user. NULL if never updated.';
COMMENT ON COLUMN exam_structures.title IS 'Title of the exam structure';
COMMENT ON COLUMN exam_structures.sections IS 'JSON array defining the sections and layout of the exam';
COMMENT ON COLUMN exam_structures.status IS 'Status of the structure (e.g., ACTIVE, INACTIVE)';
COMMENT ON COLUMN exam_structures.metadata IS 'Extensible metadata for the exam structure';
COMMENT ON COLUMN exam_structures.created_at IS 'Timestamp when the record was created';
COMMENT ON COLUMN exam_structures.updated_at IS 'Timestamp when the record was last updated';
COMMENT ON COLUMN exam_structures.deleted_at IS 'Soft delete timestamp. NULL means not deleted.';

-- Index to support filtering active structures by status
CREATE INDEX ix_exam_structures_status ON exam_structures (status) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_exam_structures_updated_at
    BEFORE UPDATE ON exam_structures
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
