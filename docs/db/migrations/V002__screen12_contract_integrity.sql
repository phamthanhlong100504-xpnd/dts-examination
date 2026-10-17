-- V002: Screen12-compatible checks, indexes and service-owned update triggers.

-- Service-owned helper; does not replace another service's generic trigger function.
CREATE OR REPLACE FUNCTION public.dts_examination_set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$body$;


CREATE OR REPLACE FUNCTION pg_temp.dts_schema_index(
  index_name text, target_table text, columns_expected text[], is_unique boolean,
  predicate text DEFAULT NULL
) RETURNS void LANGUAGE plpgsql AS $body$
DECLARE info record; columns_sql text;
BEGIN
  SELECT i.indrelid,i.indisunique,i.indisvalid,i.indisready,i.indpred IS NOT NULL AS partial,
    ARRAY(SELECT a.attname::text FROM unnest(i.indkey) WITH ORDINALITY k(attnum,ordinal)
      JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=k.attnum ORDER BY k.ordinal) AS columns_actual
    INTO info FROM pg_index i WHERE i.indexrelid=to_regclass('public.'||quote_ident(index_name));
  IF FOUND THEN
    IF info.indrelid<>to_regclass('public.'||quote_ident(target_table)) OR info.indisunique<>is_unique
      OR NOT info.indisvalid OR NOT info.indisready OR info.columns_actual<>columns_expected
      OR info.partial<>(predicate IS NOT NULL) THEN
      RAISE EXCEPTION 'Existing index % has an incompatible definition; migration stopped',index_name;
    END IF;
    RETURN;
  END IF;
  SELECT string_agg(quote_ident(value),', ') INTO columns_sql FROM unnest(columns_expected) value;
  EXECUTE format('CREATE %s INDEX %I ON public.%I (%s)%s',CASE WHEN is_unique THEN 'UNIQUE' ELSE '' END,
    index_name,target_table,columns_sql,CASE WHEN predicate IS NULL THEN '' ELSE ' WHERE '||predicate END);
END;
$body$;

ALTER TABLE public.exam_session_answers DROP CONSTRAINT IF EXISTS ck_exam_session_answers_has_answer;

DO $body$
DECLARE item record;
BEGIN
 IF to_regclass('public.uq_exam_sessions_idempotency_key') IS NOT NULL THEN
   SELECT i.indrelid,i.indisunique,
    ARRAY(SELECT a.attname::text FROM unnest(i.indkey) WITH ORDINALITY k(attnum,ordinal)
      JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=k.attnum ORDER BY k.ordinal) AS cols
    INTO item FROM pg_index i WHERE i.indexrelid='public.uq_exam_sessions_idempotency_key'::regclass;
   IF item.indrelid<>'public.exam_sessions'::regclass OR NOT item.indisunique OR item.cols<>ARRAY['idempotency_key'] THEN
     RAISE EXCEPTION 'Unexpected definition for the legacy idempotency index';
   END IF;
   DROP INDEX public.uq_exam_sessions_idempotency_key;
 END IF;
END;
$body$;

ALTER TABLE public.exams DROP CONSTRAINT IF EXISTS ck_exams_status;
ALTER TABLE public.exams ADD CONSTRAINT ck_exams_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

ALTER TABLE public.exam_structures DROP CONSTRAINT IF EXISTS ck_exam_structures_status;
ALTER TABLE public.exam_structures ADD CONSTRAINT ck_exam_structures_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PUBLISHED', 'ARCHIVED', 'INACTIVE'));

ALTER TABLE public.exam_criterias DROP CONSTRAINT IF EXISTS ck_exam_criteria_status;
ALTER TABLE public.exam_criterias ADD CONSTRAINT ck_exam_criteria_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_status;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_navigation_mode;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_navigation_mode CHECK (navigation_mode IN ('FREE', 'SEQUENTIAL'));

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_review_mode;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_review_mode CHECK (review_mode IN ('NONE', 'CURRENT_SECTION', 'ALL'));

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_result_release_mode;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_result_release_mode CHECK (result_release_mode IN ('IMMEDIATE', 'AFTER_SUBMIT', 'AFTER_EXAM_END', 'MANUAL'));

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_max_retry;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_max_retry CHECK (max_retry >= 0);

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_retry_interval;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_retry_interval CHECK (retry_interval_seconds >= 0);

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_duration;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_duration CHECK (duration_seconds >= 0);

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_grace_period;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_grace_period CHECK (grace_period_seconds >= 0);

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_max_pause_count;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_max_pause_count CHECK (max_pause_count >= 0);

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_max_pause_duration;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_max_pause_duration CHECK (max_pause_duration_seconds >= 0);

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_resume_timeout;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_resume_timeout CHECK (resume_timeout_seconds >= 0);

ALTER TABLE public.exam_rules DROP CONSTRAINT IF EXISTS ck_exam_rules_max_tab_switch;
ALTER TABLE public.exam_rules ADD CONSTRAINT ck_exam_rules_max_tab_switch CHECK (max_tab_switch_count >= 0);

ALTER TABLE public.exam_versions DROP CONSTRAINT IF EXISTS ck_exam_versions_version_no;
ALTER TABLE public.exam_versions ADD CONSTRAINT ck_exam_versions_version_no CHECK (version_no > 0);

ALTER TABLE public.exam_versions DROP CONSTRAINT IF EXISTS ck_exam_versions_content_type;
ALTER TABLE public.exam_versions ADD CONSTRAINT ck_exam_versions_content_type CHECK (content_type IN ('QUESTION', 'QUESTION_BLOCK', 'CHAPTER', 'LEARNING_PROGRAM', 'BANK_VERSION'));

ALTER TABLE public.exam_versions DROP CONSTRAINT IF EXISTS ck_exam_versions_status;
ALTER TABLE public.exam_versions ADD CONSTRAINT ck_exam_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'HIDDEN'));

ALTER TABLE public.exam_sessions DROP CONSTRAINT IF EXISTS ck_exam_sessions_attempt_no;
ALTER TABLE public.exam_sessions ADD CONSTRAINT ck_exam_sessions_attempt_no CHECK (attempt_no > 0);

ALTER TABLE public.exam_sessions DROP CONSTRAINT IF EXISTS ck_exam_sessions_submitted_at;
ALTER TABLE public.exam_sessions ADD CONSTRAINT ck_exam_sessions_submitted_at CHECK (submitted_at IS NULL OR submitted_at >= started_at);

ALTER TABLE public.exam_sessions DROP CONSTRAINT IF EXISTS ck_exam_sessions_expired_at;
ALTER TABLE public.exam_sessions ADD CONSTRAINT ck_exam_sessions_expired_at CHECK (expired_at IS NULL OR expired_at >= started_at);

ALTER TABLE public.exam_sessions DROP CONSTRAINT IF EXISTS ck_exam_sessions_duration_seconds;
ALTER TABLE public.exam_sessions ADD CONSTRAINT ck_exam_sessions_duration_seconds CHECK (duration_seconds IS NULL OR duration_seconds >= 0);

ALTER TABLE public.exam_sessions DROP CONSTRAINT IF EXISTS ck_exam_sessions_status;
ALTER TABLE public.exam_sessions ADD CONSTRAINT ck_exam_sessions_status CHECK (status IN ('READY', 'IN_PROGRESS', 'SUBMITTED', 'EXPIRED', 'CANCELLED'));

ALTER TABLE public.exam_session_answers DROP CONSTRAINT IF EXISTS ck_exam_session_answers_score;
ALTER TABLE public.exam_session_answers ADD CONSTRAINT ck_exam_session_answers_score CHECK (score >= 0);

SELECT pg_temp.dts_schema_index('ix_exams_status','exams',ARRAY['status'],false,'deleted_at IS NULL');

SELECT pg_temp.dts_schema_index('ix_exams_created_by','exams',ARRAY['created_by'],false,NULL);

SELECT pg_temp.dts_schema_index('ix_exam_structures_status','exam_structures',ARRAY['status'],false,'deleted_at IS NULL');

SELECT pg_temp.dts_schema_index('ix_exam_criteria_status','exam_criterias',ARRAY['status'],false,'deleted_at IS NULL');

SELECT pg_temp.dts_schema_index('ix_exam_rules_status','exam_rules',ARRAY['status'],false,'deleted_at IS NULL');

SELECT pg_temp.dts_schema_index('ix_exam_rules_title','exam_rules',ARRAY['title'],false,'deleted_at IS NULL');

SELECT pg_temp.dts_schema_index('ix_exam_versions_exam_id','exam_versions',ARRAY['exam_id'],false,NULL);

SELECT pg_temp.dts_schema_index('ix_exam_versions_status','exam_versions',ARRAY['status'],false,'deleted_at IS NULL');

SELECT pg_temp.dts_schema_index('ix_exam_sessions_user_id','exam_sessions',ARRAY['user_id'],false,NULL);

SELECT pg_temp.dts_schema_index('ix_exam_sessions_exam_version_id','exam_sessions',ARRAY['exam_version_id'],false,NULL);

SELECT pg_temp.dts_schema_index('ix_exam_sessions_status','exam_sessions',ARRAY['status'],false,'deleted_at IS NULL');

SELECT pg_temp.dts_schema_index('uq_exam_sessions_session_token','exam_sessions',ARRAY['session_token'],true,NULL);

SELECT pg_temp.dts_schema_index('ix_exam_sessions_idempotency_key','exam_sessions',ARRAY['idempotency_key'],false,'idempotency_key IS NOT NULL');

SELECT pg_temp.dts_schema_index('ix_exam_session_answers_exam_session_id','exam_session_answers',ARRAY['exam_session_id'],false,NULL);

SELECT pg_temp.dts_schema_index('ix_exam_session_answers_question_id','exam_session_answers',ARRAY['question_id'],false,NULL);

SELECT pg_temp.dts_schema_index('uq_exams_title','exams',ARRAY['title'],true,NULL);

SELECT pg_temp.dts_schema_index('uq_exam_rules_title','exam_rules',ARRAY['title'],true,NULL);

SELECT pg_temp.dts_schema_index('ix_dts12_session_owner_version','exam_sessions',ARRAY['exam_version_id','user_id'],false,NULL);

SELECT pg_temp.dts_schema_index('ix_dts12_worker_expiry','exam_sessions',ARRAY['expired_at','id'],false,'deleted_at IS NULL AND metadata->>''officialSessionVersion''=''1'' AND status IN (''IN_PROGRESS'',''READY'')');

SELECT pg_temp.dts_schema_index('ix_dts12_worker_outbox','exam_sessions',ARRAY['submitted_at','id'],false,'deleted_at IS NULL AND metadata->>''officialSessionVersion''=''1'' AND metadata->>''outboxState''=''PENDING''');

DO $body$
DECLARE item record;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_versions'::regclass AND c.confrelid='public.exams'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[]) THEN
   ALTER TABLE public.exam_versions ADD CONSTRAINT fk_dts12_exam_versions_exam_id FOREIGN KEY(exam_id) REFERENCES public.exams(id);
 END IF;
 FOR item IN SELECT c.conname FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_versions'::regclass AND c.confrelid='public.exams'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[] AND NOT c.convalidated LOOP
   EXECUTE format('ALTER TABLE public.exam_versions VALIDATE CONSTRAINT %I',item.conname);
 END LOOP;
END;
$body$;

DO $body$
DECLARE item record;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_structure_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_versions'::regclass AND c.confrelid='public.exam_structures'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[]) THEN
   ALTER TABLE public.exam_versions ADD CONSTRAINT fk_dts12_exam_versions_exam_structure_id FOREIGN KEY(exam_structure_id) REFERENCES public.exam_structures(id);
 END IF;
 FOR item IN SELECT c.conname FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_structure_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_versions'::regclass AND c.confrelid='public.exam_structures'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[] AND NOT c.convalidated LOOP
   EXECUTE format('ALTER TABLE public.exam_versions VALIDATE CONSTRAINT %I',item.conname);
 END LOOP;
END;
$body$;

DO $body$
DECLARE item record;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_rule_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_versions'::regclass AND c.confrelid='public.exam_rules'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[]) THEN
   ALTER TABLE public.exam_versions ADD CONSTRAINT fk_dts12_exam_versions_exam_rule_id FOREIGN KEY(exam_rule_id) REFERENCES public.exam_rules(id);
 END IF;
 FOR item IN SELECT c.conname FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_rule_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_versions'::regclass AND c.confrelid='public.exam_rules'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[] AND NOT c.convalidated LOOP
   EXECUTE format('ALTER TABLE public.exam_versions VALIDATE CONSTRAINT %I',item.conname);
 END LOOP;
END;
$body$;

DO $body$
DECLARE item record;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_criteria_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_versions'::regclass AND c.confrelid='public.exam_criterias'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[]) THEN
   ALTER TABLE public.exam_versions ADD CONSTRAINT fk_dts12_exam_versions_exam_criteria_id FOREIGN KEY(exam_criteria_id) REFERENCES public.exam_criterias(id);
 END IF;
 FOR item IN SELECT c.conname FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_criteria_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_versions'::regclass AND c.confrelid='public.exam_criterias'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[] AND NOT c.convalidated LOOP
   EXECUTE format('ALTER TABLE public.exam_versions VALIDATE CONSTRAINT %I',item.conname);
 END LOOP;
END;
$body$;

DO $body$
DECLARE item record;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_version_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_sessions'::regclass AND c.confrelid='public.exam_versions'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[]) THEN
   ALTER TABLE public.exam_sessions ADD CONSTRAINT fk_dts12_exam_sessions_exam_version_id FOREIGN KEY(exam_version_id) REFERENCES public.exam_versions(id);
 END IF;
 FOR item IN SELECT c.conname FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_version_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_sessions'::regclass AND c.confrelid='public.exam_versions'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[] AND NOT c.convalidated LOOP
   EXECUTE format('ALTER TABLE public.exam_sessions VALIDATE CONSTRAINT %I',item.conname);
 END LOOP;
END;
$body$;

DO $body$
DECLARE item record;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_session_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_session_answers'::regclass AND c.confrelid='public.exam_sessions'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[]) THEN
   ALTER TABLE public.exam_session_answers ADD CONSTRAINT fk_dts12_exam_session_answers_exam_session_id FOREIGN KEY(exam_session_id) REFERENCES public.exam_sessions(id);
 END IF;
 FOR item IN SELECT c.conname FROM pg_constraint c
   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='exam_session_id'
   JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attname='id'
   WHERE c.contype='f' AND c.conrelid='public.exam_session_answers'::regclass AND c.confrelid='public.exam_sessions'::regclass
     AND c.conkey=ARRAY[a.attnum]::smallint[] AND c.confkey=ARRAY[b.attnum]::smallint[] AND NOT c.convalidated LOOP
   EXECUTE format('ALTER TABLE public.exam_session_answers VALIDATE CONSTRAINT %I',item.conname);
 END LOOP;
END;
$body$;

DROP TRIGGER IF EXISTS trg_exams_updated_at ON public.exams;
CREATE TRIGGER trg_exams_updated_at BEFORE UPDATE ON public.exams
FOR EACH ROW EXECUTE FUNCTION public.dts_examination_set_updated_at();

DROP TRIGGER IF EXISTS trg_exam_structures_updated_at ON public.exam_structures;
CREATE TRIGGER trg_exam_structures_updated_at BEFORE UPDATE ON public.exam_structures
FOR EACH ROW EXECUTE FUNCTION public.dts_examination_set_updated_at();

DROP TRIGGER IF EXISTS trg_exam_criteria_updated_at ON public.exam_criterias;
CREATE TRIGGER trg_exam_criteria_updated_at BEFORE UPDATE ON public.exam_criterias
FOR EACH ROW EXECUTE FUNCTION public.dts_examination_set_updated_at();

DROP TRIGGER IF EXISTS trg_exam_rules_updated_at ON public.exam_rules;
CREATE TRIGGER trg_exam_rules_updated_at BEFORE UPDATE ON public.exam_rules
FOR EACH ROW EXECUTE FUNCTION public.dts_examination_set_updated_at();

DROP TRIGGER IF EXISTS trg_exam_versions_updated_at ON public.exam_versions;
CREATE TRIGGER trg_exam_versions_updated_at BEFORE UPDATE ON public.exam_versions
FOR EACH ROW EXECUTE FUNCTION public.dts_examination_set_updated_at();

DROP TRIGGER IF EXISTS trg_exam_sessions_updated_at ON public.exam_sessions;
CREATE TRIGGER trg_exam_sessions_updated_at BEFORE UPDATE ON public.exam_sessions
FOR EACH ROW EXECUTE FUNCTION public.dts_examination_set_updated_at();

DROP TRIGGER IF EXISTS trg_exam_session_answers_updated_at ON public.exam_session_answers;
CREATE TRIGGER trg_exam_session_answers_updated_at BEFORE UPDATE ON public.exam_session_answers
FOR EACH ROW EXECUTE FUNCTION public.dts_examination_set_updated_at();
