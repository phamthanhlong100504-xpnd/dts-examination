-- Table: exams
-- Service: examination
-- Entities mapped: Exam
-- Engine: PostgreSQL
-- Mô tả: Stores the main records for examinations. This table represents the core entity
-- of an exam, containing metadata like title, thumbnail, and overall status.
--
-- The exams table is the root aggregate for an examination.
-- It tracks the lifecycle status (DRAFT, PUBLISHED, ARCHIVED, HIDDEN).

CREATE TABLE exams (
    id              UUID            NOT NULL    DEFAULT gen_random_uuid(),  -- Primary key for the exam record
    thumbnail_id    UUID            NULL,                                   -- Reference to the media service for the exam thumbnail. NULL means no thumbnail.
    created_by      UUID            NOT NULL,                               -- Reference to the identity service for the creator user
    updated_by      UUID            NULL,                                   -- Reference to the identity service for the last updater user. NULL if never updated.
    title           VARCHAR(100)    NOT NULL,                               -- Unique title for the examination
    status          VARCHAR(30)     NOT NULL    DEFAULT 'DRAFT',            -- Current status of the exam (e.g., DRAFT, PUBLISHED)
    metadata        JSONB           NOT NULL    DEFAULT '{}'::jsonb,        -- Extensible metadata for the exam
    created_at      TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was created
    updated_at      TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was last updated
    deleted_at      TIMESTAMPTZ     NULL                                    -- Soft delete timestamp. NULL means not deleted.
);

ALTER TABLE exams
    ADD CONSTRAINT pk_exams PRIMARY KEY (id),
    ADD CONSTRAINT uq_exams_title UNIQUE (title),
    ADD CONSTRAINT ck_exams_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

COMMENT ON COLUMN exams.id IS 'Primary key for the exam record';
COMMENT ON COLUMN exams.thumbnail_id IS 'Reference to the media service for the exam thumbnail. NULL means no thumbnail.';
COMMENT ON COLUMN exams.created_by IS 'Reference to the identity service for the creator user';
COMMENT ON COLUMN exams.updated_by IS 'Reference to the identity service for the last updater user. NULL if never updated.';
COMMENT ON COLUMN exams.title IS 'Unique title for the examination';
COMMENT ON COLUMN exams.status IS 'Current status of the exam (e.g., DRAFT, PUBLISHED)';
COMMENT ON COLUMN exams.metadata IS 'Extensible metadata for the exam';
COMMENT ON COLUMN exams.created_at IS 'Timestamp when the record was created';
COMMENT ON COLUMN exams.updated_at IS 'Timestamp when the record was last updated';
COMMENT ON COLUMN exams.deleted_at IS 'Soft delete timestamp. NULL means not deleted.';

-- Index to support filtering by status (partial index to ignore deleted records)
CREATE INDEX ix_exams_status ON exams (status) WHERE deleted_at IS NULL;

-- Index to support looking up records created by a specific user
CREATE INDEX ix_exams_created_by ON exams (created_by);

-- Trigger: auto-update updated_at on row modification
CREATE OR REPLACE FUNCTION trigger_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_exams_updated_at
    BEFORE UPDATE ON exams
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
