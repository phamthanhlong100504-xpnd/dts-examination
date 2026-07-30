-- Table: exam_sessions
-- Service: examination
-- Entities mapped: ExamSession
-- Engine: PostgreSQL
-- Mô tả: Tracks an individual candidate's attempt at taking a specific exam version.
-- It records the progress, timing, and current status of the examination session.
--
-- This table is critical for managing active exams, preventing duplicate attempts,
-- and calculating time spent.

CREATE TABLE exam_sessions (
    id                  UUID            NOT NULL    DEFAULT gen_random_uuid(),  -- Primary key for the exam session
    exam_version_id     UUID            NOT NULL,                               -- Reference to the exam_versions table indicating which exam version is being taken
    user_id             UUID            NOT NULL,                               -- Reference to the identity service for the candidate taking the exam
    device_id           UUID            NULL,                                   -- Optional reference to the device service or a unique device identifier for tracking
    session_token       UUID            NOT NULL,                               -- Unique secure token used by the client for this specific session
    created_by          UUID            NOT NULL,                               -- Reference to the identity service for the creator user
    updated_by          UUID            NULL,                                   -- Reference to the identity service for the last updater user. NULL if never updated.
    attempt_no          INT             NOT NULL    DEFAULT 1,                  -- The attempt number for this user and exam
    duration_seconds    INT             NULL,                                   -- Actual time spent on the exam in seconds (populated upon completion)
    client_info         JSONB           NULL,                                   -- Information about the client browser, IP, or environment for security/audit
    started_at          TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the candidate officially started the exam
    submitted_at        TIMESTAMPTZ     NULL,                                   -- Timestamp when the candidate submitted the exam
    expired_at          TIMESTAMPTZ     NULL,                                   -- Timestamp when the session is forcefully expired (due to time limit)
    status              VARCHAR(30)     NOT NULL    DEFAULT 'READY',            -- Status of the session (e.g., READY, IN_PROGRESS, SUBMITTED, EXPIRED, CANCELLED)
    metadata            JSONB           NOT NULL    DEFAULT '{}'::jsonb,        -- Extensible metadata for the exam session
    created_at          TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was created
    updated_at          TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the record was last updated
    deleted_at          TIMESTAMPTZ     NULL                                    -- Soft delete timestamp. NULL means not deleted.
);

ALTER TABLE exam_sessions
    ADD CONSTRAINT pk_exam_sessions PRIMARY KEY (id),
    ADD CONSTRAINT ck_exam_sessions_attempt_no CHECK (attempt_no > 0),
    ADD CONSTRAINT ck_exam_sessions_submitted_at CHECK (submitted_at IS NULL OR submitted_at >= started_at),
    ADD CONSTRAINT ck_exam_sessions_expired_at CHECK (expired_at IS NULL OR expired_at >= started_at),
    ADD CONSTRAINT ck_exam_sessions_duration_seconds CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    ADD CONSTRAINT ck_exam_sessions_status CHECK (status IN ('READY', 'IN_PROGRESS', 'SUBMITTED', 'EXPIRED', 'CANCELLED'));

COMMENT ON COLUMN exam_sessions.id IS 'Primary key for the exam session';
COMMENT ON COLUMN exam_sessions.exam_version_id IS 'Reference to the exam_versions table indicating which exam version is being taken';
COMMENT ON COLUMN exam_sessions.user_id IS 'Reference to the identity service for the candidate taking the exam';
COMMENT ON COLUMN exam_sessions.device_id IS 'Optional reference to the device service or a unique device identifier for tracking';
COMMENT ON COLUMN exam_sessions.session_token IS 'Unique secure token used by the client for this specific session';
COMMENT ON COLUMN exam_sessions.created_by IS 'Reference to the identity service for the creator user';
COMMENT ON COLUMN exam_sessions.updated_by IS 'Reference to the identity service for the last updater user. NULL if never updated.';
COMMENT ON COLUMN exam_sessions.attempt_no IS 'The attempt number for this user and exam';
COMMENT ON COLUMN exam_sessions.duration_seconds IS 'Actual time spent on the exam in seconds (populated upon completion)';
COMMENT ON COLUMN exam_sessions.client_info IS 'Information about the client browser, IP, or environment for security/audit';
COMMENT ON COLUMN exam_sessions.started_at IS 'Timestamp when the candidate officially started the exam';
COMMENT ON COLUMN exam_sessions.submitted_at IS 'Timestamp when the candidate submitted the exam';
COMMENT ON COLUMN exam_sessions.expired_at IS 'Timestamp when the session is forcefully expired (due to time limit)';
COMMENT ON COLUMN exam_sessions.status IS 'Status of the session (e.g., READY, IN_PROGRESS, SUBMITTED, EXPIRED, CANCELLED)';
COMMENT ON COLUMN exam_sessions.metadata IS 'Extensible metadata for the exam session';
COMMENT ON COLUMN exam_sessions.created_at IS 'Timestamp when the record was created';
COMMENT ON COLUMN exam_sessions.updated_at IS 'Timestamp when the record was last updated';
COMMENT ON COLUMN exam_sessions.deleted_at IS 'Soft delete timestamp. NULL means not deleted.';

-- Index to query sessions by a specific candidate
CREATE INDEX ix_exam_sessions_user_id ON exam_sessions (user_id);

-- Index to support finding sessions for a specific exam version
CREATE INDEX ix_exam_sessions_exam_version_id ON exam_sessions (exam_version_id);

-- Index to support querying active/in-progress sessions efficiently
CREATE INDEX ix_exam_sessions_status ON exam_sessions (status) WHERE deleted_at IS NULL;

-- Unique index to quickly look up a session by its secure token
CREATE UNIQUE INDEX uq_exam_sessions_session_token ON exam_sessions (session_token);

CREATE TRIGGER trg_exam_sessions_updated_at
    BEFORE UPDATE ON exam_sessions
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
