-- Regression: V001/V002 already executed, legacy default still ACTIVE.
DO $scope$ BEGIN
 IF current_database() !~ '^dts12_schema_test_[a-z0-9_]{8,60}$' THEN
  RAISE EXCEPTION 'Test fixtures require the runner-owned temporary database';
 END IF;
END; $scope$;

BEGIN;
DELETE FROM dts_examination_schema_history WHERE version='V003';
ALTER TABLE exam_structures ALTER COLUMN status SET DEFAULT 'ACTIVE';
CREATE TABLE dts12_test_v002_history(hash text NOT NULL);
INSERT INTO dts12_test_v002_history
 SELECT md5(to_jsonb(h)::text) FROM dts_examination_schema_history h WHERE version='V002';
COMMIT;
