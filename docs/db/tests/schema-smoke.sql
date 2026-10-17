-- Run only in an isolated temporary database after apply-schema.psql.
BEGIN;
SET LOCAL search_path=public,pg_catalog;
SET LOCAL statement_timeout='30s';
DO $scope$
BEGIN
 IF current_database() !~ '^dts12_schema_test_[a-z0-9_]{8,60}$' THEN
   RAISE EXCEPTION 'Schema smoke fixtures require an isolated temporary test database';
 END IF;
END;
$scope$;

INSERT INTO exams(id,title) VALUES('00000000-0000-0000-0000-000000000001','DTS12-SCHEMA-TEST');
INSERT INTO exam_structures(id,title,created_by) VALUES('00000000-0000-0000-0000-000000000002','schema structure','00000000-0000-0000-0000-000000000009');
INSERT INTO exam_rules(id,title) VALUES('00000000-0000-0000-0000-000000000003','schema rule');
INSERT INTO exam_criterias(id,title) VALUES('00000000-0000-0000-0000-000000000004','schema criteria');
INSERT INTO exam_versions(id,exam_id,exam_structure_id,exam_rule_id,exam_criteria_id,content_id,title,exam_type,content_type)
 VALUES('00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000009','schema version','OFFICIAL','BANK_VERSION');
INSERT INTO exam_sessions(id,exam_version_id,user_id,session_token,started_at,idempotency_key)
 VALUES('00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000009',gen_random_uuid(),CURRENT_TIMESTAMP,'same-key'),
 ('00000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000009',gen_random_uuid(),CURRENT_TIMESTAMP,'same-key');
INSERT INTO exam_session_answers(id,exam_session_id,question_id,selected_answer,answered_at)
 VALUES('00000000-0000-0000-0000-000000000008','00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000009',NULL,CURRENT_TIMESTAMP);
UPDATE exam_session_answers SET selected_answer='{"type":"single_choice","value":"sample"}'::jsonb WHERE id='00000000-0000-0000-0000-000000000008';
UPDATE exam_session_answers SET selected_answer=NULL WHERE id='00000000-0000-0000-0000-000000000008';
UPDATE exam_sessions SET metadata='{"officialSessionVersion":"1","outboxState":"PENDING","resultEvent":{"eventId":"test"},"gradingSnapshot":{"score":30}}'::jsonb,version=version+1 WHERE id='00000000-0000-0000-0000-000000000006';
DO $body$
BEGIN
 IF (SELECT status FROM exam_structures WHERE id='00000000-0000-0000-0000-000000000002')<>'DRAFT' THEN RAISE EXCEPTION 'Wrong structure default'; END IF;
 IF (SELECT selected_answer FROM exam_session_answers WHERE id='00000000-0000-0000-0000-000000000008') IS NOT NULL THEN RAISE EXCEPTION 'Answer clearing failed'; END IF;
 IF (SELECT metadata->>'outboxState' FROM exam_sessions WHERE id='00000000-0000-0000-0000-000000000006')<>'PENDING' THEN RAISE EXCEPTION 'JSONB outbox state failed'; END IF;
 BEGIN
  UPDATE exam_versions SET content_type='INVALID_SOURCE' WHERE id='00000000-0000-0000-0000-000000000005';
  RAISE EXCEPTION 'Unknown content type accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
  INSERT INTO exam_session_answers(exam_session_id,question_id,answered_at) VALUES(gen_random_uuid(),gen_random_uuid(),CURRENT_TIMESTAMP);
  RAISE EXCEPTION 'Orphan answer accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
END;
$body$;
SELECT json_build_object('schema_smoke_passed',true,'fixture_scope','temporary database; transaction rolled back');
ROLLBACK;
