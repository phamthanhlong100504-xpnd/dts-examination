DO $scope$ BEGIN
 IF current_database() !~ '^dts12_schema_test_[a-z0-9_]{8,60}$' THEN
  RAISE EXCEPTION 'Test fixtures require the runner-owned temporary database';
 END IF;
END; $scope$;

BEGIN;
DELETE FROM dts_examination_schema_history WHERE version IN ('V002','V003');
ALTER TABLE exam_structures DROP CONSTRAINT ck_exam_structures_status;
INSERT INTO exam_structures(title,created_by,status) VALUES('DTS12-INVALID-LEGACY','10000000-0000-0000-0000-000000000002','UNSUPPORTED_LEGACY');
COMMIT;
