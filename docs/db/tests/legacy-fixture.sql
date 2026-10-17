DO $scope$ BEGIN
 IF current_database() !~ '^dts12_schema_test_[a-z0-9_]{8,60}$' THEN
  RAISE EXCEPTION 'Test fixtures require the runner-owned temporary database';
 END IF;
END; $scope$;

BEGIN;
DELETE FROM dts_examination_schema_history WHERE version IN ('V002','V003','V004');
DROP INDEX ix_official_history_pending;
DROP INDEX ix_official_history_backfill;
DROP INDEX ix_legacy_history_pending;
ALTER TABLE exam_session_answers ADD CONSTRAINT ck_exam_session_answers_has_answer CHECK(selected_answer IS NOT NULL);
ALTER TABLE exam_versions DROP CONSTRAINT ck_exam_versions_content_type;
ALTER TABLE exam_versions ADD CONSTRAINT ck_exam_versions_content_type CHECK(content_type IN ('QUESTION','QUESTION_BLOCK','CHAPTER','LEARNING_PROGRAM'));
ALTER TABLE exam_structures DROP CONSTRAINT ck_exam_structures_status;
ALTER TABLE exam_structures ADD CONSTRAINT ck_exam_structures_status CHECK(status IN ('ACTIVE','INACTIVE'));
ALTER TABLE exam_structures ALTER COLUMN status SET DEFAULT 'ACTIVE';
CREATE UNIQUE INDEX uq_exam_sessions_idempotency_key ON exam_sessions(idempotency_key) WHERE idempotency_key IS NOT NULL;
INSERT INTO exams(id,title,created_by,metadata) VALUES('10000000-0000-0000-0000-000000000001','DTS12-KEEP-LEGACY','10000000-0000-0000-0000-000000000002','{"keep":"original","privateSnapshot":"must-not-rebuild"}');
INSERT INTO exam_structures(id,title,created_by,status,metadata)
 VALUES('10000000-0000-0000-0000-000000000005','DTS12-KEEP-ACTIVE-STRUCTURE','10000000-0000-0000-0000-000000000002','ACTIVE','{"keep":"original-structure"}');
CREATE TABLE dts12_test_preservation(hash text NOT NULL,structure_hash text NOT NULL,v001_history_hash text NOT NULL);
INSERT INTO dts12_test_preservation
 SELECT md5(to_jsonb(e)::text),
  (SELECT md5(to_jsonb(s)::text) FROM exam_structures s WHERE id='10000000-0000-0000-0000-000000000005'),
  (SELECT md5(to_jsonb(h)::text) FROM dts_examination_schema_history h WHERE version='V001')
 FROM exams e WHERE id='10000000-0000-0000-0000-000000000001';
COMMIT;
