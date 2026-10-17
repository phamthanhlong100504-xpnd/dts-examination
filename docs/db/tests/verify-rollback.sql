DO $scope$ BEGIN
 IF current_database() !~ '^dts12_schema_test_[a-z0-9_]{8,60}$' THEN
  RAISE EXCEPTION 'Test fixtures require the runner-owned temporary database';
 END IF;
END; $scope$;

DO $test$ BEGIN
 IF EXISTS(SELECT 1 FROM dts_examination_schema_history WHERE version IN ('V002','V003')) OR
  (SELECT md5(to_jsonb(e)::text) FROM exams e WHERE id='10000000-0000-0000-0000-000000000001')
   IS DISTINCT FROM (SELECT hash FROM dts12_test_preservation) OR
  (SELECT md5(to_jsonb(s)::text) FROM exam_structures s WHERE id='10000000-0000-0000-0000-000000000005')
   IS DISTINCT FROM (SELECT structure_hash FROM dts12_test_preservation) OR
  (SELECT md5(to_jsonb(h)::text) FROM dts_examination_schema_history h WHERE version='V001')
   IS DISTINCT FROM (SELECT v001_history_hash FROM dts12_test_preservation) OR
  NOT EXISTS(SELECT 1 FROM exam_structures WHERE title='DTS12-INVALID-LEGACY' AND status='UNSUPPORTED_LEGACY') THEN
  RAISE EXCEPTION 'Failure did not preserve legacy data/history';
 END IF;
END; $test$;
