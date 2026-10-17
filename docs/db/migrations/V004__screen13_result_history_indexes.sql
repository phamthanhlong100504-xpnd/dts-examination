-- Existing official outbox remains in the source session metadata.
CREATE INDEX ix_official_history_pending ON public.exam_sessions(submitted_at,id)
WHERE deleted_at IS NULL AND metadata->>'officialSessionVersion'='1' AND metadata->>'outboxState'='PENDING';
CREATE INDEX ix_official_history_backfill ON public.exam_sessions(submitted_at,id)
WHERE deleted_at IS NULL AND status='SUBMITTED' AND metadata->>'officialSessionVersion'='1'
    AND (metadata->>'historySchemaVersion') IS DISTINCT FROM '2';
CREATE INDEX ix_legacy_history_pending ON public.exam_sessions(submitted_at,id)
WHERE deleted_at IS NULL AND status='SUBMITTED' AND metadata->>'legacyHistoryOutboxState'='PENDING';
