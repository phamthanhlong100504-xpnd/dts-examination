-- Table: exam_versions
-- Service: examination
-- Entities mapped: ExamVersion
-- Engine: PostgreSQL
-- Mô tả: Stores specific versions of an examination. Each version represents a snapshot
-- of the exam configuration, structure, and rules at a given time.
--
-- This table tracks the history and evolution of an exam. Multiple versions can exist,
-- but usually only one is active at a time for taking.

CREATE TABLE exam_versions (
    id                  UUID            NOT NULL    DEFAULT gen_random_uuid(),  -- Primary key for the exam version
    exam_id             UUID            NOT NULL,                               -- Reference to the parent exams table
    exam_structure_id   UUID            NOT NULL,                               -- Reference to the exam_structures table for the layout of this version
    exam_rule_id        UUID            NOT NULL,                               -- Reference to the exam_rules table for the rules applied to this version
    exam_criteria_id    UUID            NULL,                                   -- Reference to the exam_criteria table for grading. NULL if not applicable.
    thumbnail_id        UUID            NULL,                                   -- Reference to the media service for the exam thumbnail. NULL means no thumbnail.
    content_id          UUID            NOT NULL,                               -- Reference to the core content entity (Question, Chapter, etc.)
    created_by          UUID            NOT NULL,                               -- Reference to the identity service for the creator user
    updated_by          UUID            NULL,                                   -- Reference to the identity service for the last updater user. NULL if never updated.
    version_no          INT             NOT NULL    DEFAULT 1,                  -- Sequential version number of the exam
    title               VARCHAR(255)    NOT NULL,                               -- Title of this specific version
    exam_type           VARCHAR(50)     NOT NULL,                               -- Type classification of the exam
    configs             JSONB           NOT NULL    DEFAULT '{}'::jsonb,        -- Additional version-specific configurations
    started_at          TIMESTAMPTZ     NULL,                                   -- Timestamp when this version becomes active/valid
    ended_at            TIMESTAMPTZ     NULL,                                   -- Timestamp when this version expires/ends
    content_type        VARCHAR(30)     NOT NULL,                               -- Type of the referenced content (e.g., QUESTION, CHAPTER)
    status              VARCHAR(30)     NOT NULL    DEFAULT 'DRAFT',            -- Current status of the exam version (e.g., DRAFT, PUBLISHED)
    metadata            JSONB           NOT NULL    DEFAULT '{}'::jsonb,        -- Extensible metadata for the exam version
    created_at          TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was created
    updated_at          TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was last updated
    deleted_at          TIMESTAMPTZ     NULL                                    -- Soft delete timestamp. NULL means not deleted.
);

ALTER TABLE exam_versions
    ADD CONSTRAINT pk_exam_versions PRIMARY KEY (id),
    ADD CONSTRAINT ck_exam_versions_version_no CHECK (version_no > 0),
    ADD CONSTRAINT ck_exam_versions_content_type CHECK (content_type IN ('QUESTION', 'QUESTION_BLOCK', 'CHAPTER', 'LEARNING_PROGRAM')),
    ADD CONSTRAINT ck_exam_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

COMMENT ON COLUMN exam_versions.id IS 'Primary key for the exam version';
COMMENT ON COLUMN exam_versions.exam_id IS 'Reference to the parent exams table';
COMMENT ON COLUMN exam_versions.exam_structure_id IS 'Reference to the exam_structures table for the layout of this version';
COMMENT ON COLUMN exam_versions.exam_rule_id IS 'Reference to the exam_rules table for the rules applied to this version';
COMMENT ON COLUMN exam_versions.exam_criteria_id IS 'Reference to the exam_criteria table for grading. NULL if not applicable.';
COMMENT ON COLUMN exam_versions.thumbnail_id IS 'Reference to the media service for the exam thumbnail. NULL means no thumbnail.';
COMMENT ON COLUMN exam_versions.content_id IS 'Reference to the core content entity (Question, Chapter, etc.)';
COMMENT ON COLUMN exam_versions.created_by IS 'Reference to the identity service for the creator user';
COMMENT ON COLUMN exam_versions.updated_by IS 'Reference to the identity service for the last updater user. NULL if never updated.';
COMMENT ON COLUMN exam_versions.version_no IS 'Sequential version number of the exam';
COMMENT ON COLUMN exam_versions.title IS 'Title of this specific version';
COMMENT ON COLUMN exam_versions.exam_type IS 'Type classification of the exam';
COMMENT ON COLUMN exam_versions.configs IS 'Additional version-specific configurations';
COMMENT ON COLUMN exam_versions.started_at IS 'Timestamp when this version becomes active/valid';
COMMENT ON COLUMN exam_versions.ended_at IS 'Timestamp when this version expires/ends';
COMMENT ON COLUMN exam_versions.content_type IS 'Type of the referenced content (e.g., QUESTION, CHAPTER)';
COMMENT ON COLUMN exam_versions.status IS 'Current status of the exam version (e.g., DRAFT, PUBLISHED)';
COMMENT ON COLUMN exam_versions.metadata IS 'Extensible metadata for the exam version';
COMMENT ON COLUMN exam_versions.created_at IS 'Timestamp when the record was created';
COMMENT ON COLUMN exam_versions.updated_at IS 'Timestamp when the record was last updated';
COMMENT ON COLUMN exam_versions.deleted_at IS 'Soft delete timestamp. NULL means not deleted.';

-- Index to quickly find all versions of a specific exam
CREATE INDEX ix_exam_versions_exam_id ON exam_versions (exam_id);

-- Index to support filtering active versions by status
CREATE INDEX ix_exam_versions_status ON exam_versions (status) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_exam_versions_updated_at
    BEFORE UPDATE ON exam_versions
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
