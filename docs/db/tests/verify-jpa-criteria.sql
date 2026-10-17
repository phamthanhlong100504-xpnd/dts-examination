DO $scope$ BEGIN
 IF current_database() !~ '^dts12_schema_test_[a-z0-9_]{8,60}$' THEN
  RAISE EXCEPTION 'Test fixtures require the runner-owned temporary database';
 END IF;
END; $scope$;
DO $test$
DECLARE actual_hash text; saved record;
BEGIN
 IF to_regclass('public.exam_criteria') IS NOT NULL OR to_regclass('public.exam_criterias') IS NULL THEN
  RAISE EXCEPTION 'Schema must use the canonical JPA criteria table';
 END IF;
 IF (SELECT count(*) FROM dts12_test_jpa_preservation)<>5 THEN RAISE EXCEPTION 'Incomplete JPA preservation fixture'; END IF;
 FOR saved IN SELECT table_name,row_id,row_hash FROM dts12_test_jpa_preservation LOOP
  EXECUTE format('SELECT md5(to_jsonb(r)::text) FROM public.%I r WHERE id=$1',saved.table_name) INTO STRICT actual_hash USING saved.row_id;
  IF actual_hash IS DISTINCT FROM saved.row_hash THEN RAISE EXCEPTION 'Existing JPA row changed: %',saved.table_name; END IF;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.exam_versions'::regclass
  AND confrelid='public.exam_criterias'::regclass AND conname='fk_dts12_exam_versions_exam_criteria_id'
  AND contype='f' AND convalidated) THEN RAISE EXCEPTION 'Criteria FK does not target JPA table'; END IF;
 IF (SELECT count(*) FROM dts_examination_schema_history)<>4 OR
  (SELECT count(*) FROM dts_examination_schema_history WHERE version IN ('V001','V002','V003','V004'))<>4 THEN
  RAISE EXCEPTION 'Missing baseline/history versions V001 through V004 after JPA upgrade';
 END IF;
END;
$test$;
