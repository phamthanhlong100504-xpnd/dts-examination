DO $scope$ BEGIN
 IF current_database() !~ '^dts12_schema_test_[a-z0-9_]{8,60}$' THEN
  RAISE EXCEPTION 'Test fixtures require the runner-owned temporary database';
 END IF;
END; $scope$;
DO $test$ BEGIN IF NOT EXISTS(SELECT 1 FROM dts_examination_schema_history WHERE version='V001' AND checksum=repeat('0',64)) THEN RAISE EXCEPTION 'Changed executed checksum was overwritten'; END IF; END; $test$;
