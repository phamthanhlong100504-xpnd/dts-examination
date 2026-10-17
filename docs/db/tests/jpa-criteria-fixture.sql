-- Reproduce an existing Hibernate/JPA criteria table with valid version references.
-- Explicit exam_criterias name is grounded in ExamCriteria @Table, not legacy DDL.
DO $scope$ BEGIN
 IF current_database() !~ '^dts12_schema_test_[a-z0-9_]{8,60}$' THEN
  RAISE EXCEPTION 'Test fixtures require the runner-owned temporary database';
 END IF;
END; $scope$;
BEGIN;
INSERT INTO exams(id,title,metadata)
 VALUES('20000000-0000-0000-0000-000000000001','DTS12-JPA-EXAM','{"keep":"jpa-exam"}');
INSERT INTO exam_structures(id,title,created_by,status,metadata)
 VALUES('20000000-0000-0000-0000-000000000002','DTS12-JPA-STRUCTURE','20000000-0000-0000-0000-000000000009','ACTIVE','{"keep":"jpa-structure"}');
INSERT INTO exam_rules(id,title,metadata)
 VALUES('20000000-0000-0000-0000-000000000003','DTS12-JPA-RULE','{"keep":"jpa-rule"}');
INSERT INTO exam_criterias(id,title,criteria,metadata)
 VALUES('20000000-0000-0000-0000-000000000004','DTS12-JPA-CRITERIA','{"passScore":27,"totalScore":30,"failOnAnyCriticalWrong":true}','{"keep":"jpa-criteria"}');
INSERT INTO exam_versions(id,exam_id,exam_structure_id,exam_rule_id,exam_criteria_id,content_id,title,exam_type,content_type,metadata)
 VALUES('20000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000009','DTS12-JPA-VERSION','OFFICIAL','QUESTION','{"keep":"jpa-version"}');
CREATE TABLE dts12_test_jpa_preservation(table_name text PRIMARY KEY,row_id uuid NOT NULL,row_hash text NOT NULL);
INSERT INTO dts12_test_jpa_preservation
 SELECT 'exams',r.id,md5(to_jsonb(r)::text) FROM exams r WHERE id='20000000-0000-0000-0000-000000000001'
 UNION ALL SELECT 'exam_structures',r.id,md5(to_jsonb(r)::text) FROM exam_structures r WHERE id='20000000-0000-0000-0000-000000000002'
 UNION ALL SELECT 'exam_rules',r.id,md5(to_jsonb(r)::text) FROM exam_rules r WHERE id='20000000-0000-0000-0000-000000000003'
 UNION ALL SELECT 'exam_criterias',r.id,md5(to_jsonb(r)::text) FROM exam_criterias r WHERE id='20000000-0000-0000-0000-000000000004'
 UNION ALL SELECT 'exam_versions',r.id,md5(to_jsonb(r)::text) FROM exam_versions r WHERE id='20000000-0000-0000-0000-000000000005';
ALTER TABLE exam_versions DROP CONSTRAINT fk_dts12_exam_versions_exam_criteria_id;
-- Simulate a schema from before Screen13, together with its absent history.
-- V004 must recreate its indexes when the complete migration chain is replayed.
DROP INDEX ix_official_history_pending;
DROP INDEX ix_official_history_backfill;
DROP INDEX ix_legacy_history_pending;
DROP TABLE dts_examination_schema_history;
COMMIT;
