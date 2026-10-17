-- V001: baseline entity columns for a new or existing service database.
-- No DELETE/UPDATE/backfill of existing business rows; incompatible legacy data causes rollback.


CREATE OR REPLACE FUNCTION pg_temp.dts_schema_column(
  target_table text, column_name text, declaration text, expected_type regtype,
  required boolean, default_expression text
) RETURNS void LANGUAGE plpgsql AS $body$
DECLARE actual_type oid; is_required boolean; type_modifier integer; width integer;
BEGIN
  SELECT a.atttypid,a.attnotnull,a.atttypmod INTO actual_type,is_required,type_modifier FROM pg_attribute a
    WHERE a.attrelid=to_regclass('public.'||quote_ident(target_table))
      AND a.attname=column_name AND a.attnum>0 AND NOT a.attisdropped;
  IF actual_type IS NULL THEN
    EXECUTE format('ALTER TABLE public.%I ADD COLUMN %I %s',target_table,column_name,declaration);
    SELECT a.atttypid,a.attnotnull,a.atttypmod INTO actual_type,is_required,type_modifier FROM pg_attribute a
      WHERE a.attrelid=to_regclass('public.'||quote_ident(target_table)) AND a.attname=column_name AND NOT a.attisdropped;
  END IF;
  -- Never silently convert a differently typed live column or shrink existing text.
  IF actual_type<>expected_type::oid AND NOT(expected_type::oid='varchar'::regtype::oid AND actual_type='text'::regtype::oid) THEN
    RAISE EXCEPTION 'Schema mismatch %.%: unexpected column type; review before migration',target_table,column_name;
  END IF;
  width := substring(declaration from '(?i)VARCHAR\(([0-9]+)\)')::integer;
  IF actual_type='varchar'::regtype::oid AND width IS NOT NULL AND type_modifier>=4 AND type_modifier-4<width THEN
    EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I TYPE varchar(%s)',target_table,column_name,width);
  END IF;
  IF required AND NOT is_required THEN
    EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I SET NOT NULL',target_table,column_name);
  ELSIF NOT required AND is_required THEN
    EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I DROP NOT NULL',target_table,column_name);
  END IF;
  IF default_expression IS NOT NULL THEN
    EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I SET DEFAULT %s',target_table,column_name,default_expression);
  END IF;
END;
$body$;

CREATE TABLE IF NOT EXISTS public.exams (
  id UUID            NOT NULL    DEFAULT gen_random_uuid(),
  thumbnail_id UUID            NULL,
  created_by UUID            NULL,
  updated_by UUID            NULL,
  title VARCHAR(100)    NOT NULL,
  status VARCHAR(30)     NOT NULL    DEFAULT 'DRAFT',
  metadata JSONB           NOT NULL    DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ     NULL
);

SELECT pg_temp.dts_schema_column('exams','id','UUID            NOT NULL    DEFAULT gen_random_uuid()','uuid'::regtype,true,'gen_random_uuid()');

SELECT pg_temp.dts_schema_column('exams','thumbnail_id','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exams','created_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exams','updated_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exams','title','VARCHAR(100)    NOT NULL','varchar'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exams','status','VARCHAR(30)     NOT NULL    DEFAULT ''DRAFT''','varchar'::regtype,true,'''DRAFT''');

SELECT pg_temp.dts_schema_column('exams','metadata','JSONB           NOT NULL    DEFAULT ''{}''::jsonb','jsonb'::regtype,true,'''{}''::jsonb');

SELECT pg_temp.dts_schema_column('exams','created_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exams','updated_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exams','deleted_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

DO $body$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.exams'::regclass AND contype='p') THEN
   ALTER TABLE public.exams ADD CONSTRAINT pk_exams PRIMARY KEY(id);
 ELSIF NOT EXISTS(SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='id'
   WHERE c.conrelid='public.exams'::regclass AND c.contype='p' AND c.conkey=ARRAY[a.attnum]::smallint[]) THEN
   RAISE EXCEPTION 'Unexpected primary key on exams';
 END IF;
END;
$body$;

CREATE TABLE IF NOT EXISTS public.exam_structures (
  id UUID            NOT NULL    DEFAULT gen_random_uuid(),
  created_by UUID            NOT NULL,
  updated_by UUID            NULL,
  title VARCHAR(255)    NOT NULL,
  sections JSONB           NOT NULL    DEFAULT '[]'::jsonb,
  status VARCHAR(30)     NOT NULL    DEFAULT 'DRAFT',
  metadata JSONB           NOT NULL    DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ     NULL
);

SELECT pg_temp.dts_schema_column('exam_structures','id','UUID            NOT NULL    DEFAULT gen_random_uuid()','uuid'::regtype,true,'gen_random_uuid()');

SELECT pg_temp.dts_schema_column('exam_structures','created_by','UUID            NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_structures','updated_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_structures','title','VARCHAR(255)    NOT NULL','varchar'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_structures','sections','JSONB           NOT NULL    DEFAULT ''[]''::jsonb','jsonb'::regtype,true,'''[]''::jsonb');

SELECT pg_temp.dts_schema_column('exam_structures','status','VARCHAR(30)     NOT NULL    DEFAULT ''DRAFT''','varchar'::regtype,true,'''DRAFT''');

SELECT pg_temp.dts_schema_column('exam_structures','metadata','JSONB           NOT NULL    DEFAULT ''{}''::jsonb','jsonb'::regtype,true,'''{}''::jsonb');

SELECT pg_temp.dts_schema_column('exam_structures','created_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_structures','updated_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_structures','deleted_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

DO $body$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.exam_structures'::regclass AND contype='p') THEN
   ALTER TABLE public.exam_structures ADD CONSTRAINT pk_exam_structures PRIMARY KEY(id);
 ELSIF NOT EXISTS(SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='id'
   WHERE c.conrelid='public.exam_structures'::regclass AND c.contype='p' AND c.conkey=ARRAY[a.attnum]::smallint[]) THEN
   RAISE EXCEPTION 'Unexpected primary key on exam_structures';
 END IF;
END;
$body$;

CREATE TABLE IF NOT EXISTS public.exam_criterias (
  id UUID            NOT NULL    DEFAULT gen_random_uuid(),
  created_by UUID            NULL,
  updated_by UUID            NULL,
  title VARCHAR(255)    NOT NULL,
  criteria JSONB           NOT NULL    DEFAULT '{}'::jsonb,
  status VARCHAR(30)     NOT NULL    DEFAULT 'ACTIVE',
  metadata JSONB           NULL    DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ     NULL
);

SELECT pg_temp.dts_schema_column('exam_criterias','id','UUID            NOT NULL    DEFAULT gen_random_uuid()','uuid'::regtype,true,'gen_random_uuid()');

SELECT pg_temp.dts_schema_column('exam_criterias','created_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_criterias','updated_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_criterias','title','VARCHAR(255)    NOT NULL','varchar'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_criterias','criteria','JSONB           NOT NULL    DEFAULT ''{}''::jsonb','jsonb'::regtype,true,'''{}''::jsonb');

SELECT pg_temp.dts_schema_column('exam_criterias','status','VARCHAR(30)     NOT NULL    DEFAULT ''ACTIVE''','varchar'::regtype,true,'''ACTIVE''');

SELECT pg_temp.dts_schema_column('exam_criterias','metadata','JSONB           NULL    DEFAULT ''{}''::jsonb','jsonb'::regtype,false,'''{}''::jsonb');

SELECT pg_temp.dts_schema_column('exam_criterias','created_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_criterias','updated_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_criterias','deleted_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

DO $body$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.exam_criterias'::regclass AND contype='p') THEN
   ALTER TABLE public.exam_criterias ADD CONSTRAINT pk_exam_criteria PRIMARY KEY(id);
 ELSIF NOT EXISTS(SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='id'
   WHERE c.conrelid='public.exam_criterias'::regclass AND c.contype='p' AND c.conkey=ARRAY[a.attnum]::smallint[]) THEN
   RAISE EXCEPTION 'Unexpected primary key on exam_criterias';
 END IF;
END;
$body$;

CREATE TABLE IF NOT EXISTS public.exam_rules (
  id UUID            NOT NULL    DEFAULT gen_random_uuid(),
  created_by UUID            NULL,
  updated_by UUID            NULL,
  title VARCHAR(255)    NOT NULL,
  allow_retry BOOLEAN         NOT NULL    DEFAULT false,
  max_retry INT             NOT NULL    DEFAULT 0,
  retry_interval_seconds INT             NOT NULL    DEFAULT 0,
  duration_seconds INT             NOT NULL    DEFAULT 0,
  grace_period_seconds INT             NOT NULL    DEFAULT 0,
  auto_submit BOOLEAN         NOT NULL    DEFAULT true,
  navigation_mode VARCHAR(30)     NOT NULL    DEFAULT 'FREE',
  allow_skip BOOLEAN         NOT NULL    DEFAULT true,
  review_mode VARCHAR(30)     NOT NULL    DEFAULT 'ALL',
  allow_pause BOOLEAN         NOT NULL    DEFAULT false,
  max_pause_count INT             NOT NULL    DEFAULT 0,
  max_pause_duration_seconds INT             NOT NULL    DEFAULT 0,
  allow_resume BOOLEAN         NOT NULL    DEFAULT false,
  resume_timeout_seconds INT             NOT NULL    DEFAULT 0,
  shuffle_sections BOOLEAN         NOT NULL    DEFAULT false,
  shuffle_questions_within_section BOOLEAN         NOT NULL    DEFAULT false,
  shuffle_questions_across_sections BOOLEAN         NOT NULL    DEFAULT false,
  shuffle_options BOOLEAN         NOT NULL    DEFAULT false,
  result_release_mode VARCHAR(30)     NOT NULL    DEFAULT 'IMMEDIATE',
  show_answer_after_submit BOOLEAN         NOT NULL    DEFAULT true,
  show_explanation_after_submit BOOLEAN         NOT NULL    DEFAULT false,
  show_question_score_after_submit BOOLEAN         NOT NULL    DEFAULT false,
  require_fullscreen BOOLEAN         NOT NULL    DEFAULT false,
  prevent_tab_switch BOOLEAN         NOT NULL    DEFAULT false,
  max_tab_switch_count INT             NOT NULL    DEFAULT 0,
  time_zone VARCHAR(100)    NULL,
  status VARCHAR(30)     NOT NULL    DEFAULT 'ACTIVE',
  metadata JSONB           NULL    DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ     NULL
);

SELECT pg_temp.dts_schema_column('exam_rules','id','UUID            NOT NULL    DEFAULT gen_random_uuid()','uuid'::regtype,true,'gen_random_uuid()');

SELECT pg_temp.dts_schema_column('exam_rules','created_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_rules','updated_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_rules','title','VARCHAR(255)    NOT NULL','varchar'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_rules','allow_retry','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','max_retry','INT             NOT NULL    DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_rules','retry_interval_seconds','INT             NOT NULL    DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_rules','duration_seconds','INT             NOT NULL    DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_rules','grace_period_seconds','INT             NOT NULL    DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_rules','auto_submit','BOOLEAN         NOT NULL    DEFAULT true','boolean'::regtype,true,'true');

SELECT pg_temp.dts_schema_column('exam_rules','navigation_mode','VARCHAR(30)     NOT NULL    DEFAULT ''FREE''','varchar'::regtype,true,'''FREE''');

SELECT pg_temp.dts_schema_column('exam_rules','allow_skip','BOOLEAN         NOT NULL    DEFAULT true','boolean'::regtype,true,'true');

SELECT pg_temp.dts_schema_column('exam_rules','review_mode','VARCHAR(30)     NOT NULL    DEFAULT ''ALL''','varchar'::regtype,true,'''ALL''');

SELECT pg_temp.dts_schema_column('exam_rules','allow_pause','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','max_pause_count','INT             NOT NULL    DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_rules','max_pause_duration_seconds','INT             NOT NULL    DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_rules','allow_resume','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','resume_timeout_seconds','INT             NOT NULL    DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_rules','shuffle_sections','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','shuffle_questions_within_section','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','shuffle_questions_across_sections','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','shuffle_options','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','result_release_mode','VARCHAR(30)     NOT NULL    DEFAULT ''IMMEDIATE''','varchar'::regtype,true,'''IMMEDIATE''');

SELECT pg_temp.dts_schema_column('exam_rules','show_answer_after_submit','BOOLEAN         NOT NULL    DEFAULT true','boolean'::regtype,true,'true');

SELECT pg_temp.dts_schema_column('exam_rules','show_explanation_after_submit','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','show_question_score_after_submit','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','require_fullscreen','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','prevent_tab_switch','BOOLEAN         NOT NULL    DEFAULT false','boolean'::regtype,true,'false');

SELECT pg_temp.dts_schema_column('exam_rules','max_tab_switch_count','INT             NOT NULL    DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_rules','time_zone','VARCHAR(100)    NULL','varchar'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_rules','status','VARCHAR(30)     NOT NULL    DEFAULT ''ACTIVE''','varchar'::regtype,true,'''ACTIVE''');

SELECT pg_temp.dts_schema_column('exam_rules','metadata','JSONB           NULL    DEFAULT ''{}''::jsonb','jsonb'::regtype,false,'''{}''::jsonb');

SELECT pg_temp.dts_schema_column('exam_rules','created_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_rules','updated_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_rules','deleted_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

DO $body$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.exam_rules'::regclass AND contype='p') THEN
   ALTER TABLE public.exam_rules ADD CONSTRAINT pk_exam_rules PRIMARY KEY(id);
 ELSIF NOT EXISTS(SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='id'
   WHERE c.conrelid='public.exam_rules'::regclass AND c.contype='p' AND c.conkey=ARRAY[a.attnum]::smallint[]) THEN
   RAISE EXCEPTION 'Unexpected primary key on exam_rules';
 END IF;
END;
$body$;

CREATE TABLE IF NOT EXISTS public.exam_versions (
  id UUID            NOT NULL    DEFAULT gen_random_uuid(),
  exam_id UUID            NOT NULL,
  exam_structure_id UUID            NOT NULL,
  exam_rule_id UUID            NOT NULL,
  exam_criteria_id UUID            NULL,
  thumbnail_id UUID            NULL,
  content_id UUID            NOT NULL,
  created_by UUID            NULL,
  updated_by UUID            NULL,
  version_no INT             NOT NULL    DEFAULT 1,
  title VARCHAR(255)    NOT NULL,
  exam_type VARCHAR(50)     NOT NULL,
  configs JSONB           NOT NULL    DEFAULT '{}'::jsonb,
  started_at TIMESTAMPTZ     NULL,
  ended_at TIMESTAMPTZ     NULL,
  content_type VARCHAR(30)     NOT NULL,
  status VARCHAR(30)     NOT NULL    DEFAULT 'DRAFT',
  metadata JSONB           NOT NULL    DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ     NULL
);

SELECT pg_temp.dts_schema_column('exam_versions','id','UUID            NOT NULL    DEFAULT gen_random_uuid()','uuid'::regtype,true,'gen_random_uuid()');

SELECT pg_temp.dts_schema_column('exam_versions','exam_id','UUID            NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','exam_structure_id','UUID            NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','exam_rule_id','UUID            NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','exam_criteria_id','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','thumbnail_id','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','content_id','UUID            NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','created_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','updated_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','version_no','INT             NOT NULL    DEFAULT 1','integer'::regtype,true,'1');

SELECT pg_temp.dts_schema_column('exam_versions','title','VARCHAR(255)    NOT NULL','varchar'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','exam_type','VARCHAR(50)     NOT NULL','varchar'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','configs','JSONB           NOT NULL    DEFAULT ''{}''::jsonb','jsonb'::regtype,true,'''{}''::jsonb');

SELECT pg_temp.dts_schema_column('exam_versions','started_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','ended_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','content_type','VARCHAR(30)     NOT NULL','varchar'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_versions','status','VARCHAR(30)     NOT NULL    DEFAULT ''DRAFT''','varchar'::regtype,true,'''DRAFT''');

SELECT pg_temp.dts_schema_column('exam_versions','metadata','JSONB           NOT NULL    DEFAULT ''{}''::jsonb','jsonb'::regtype,true,'''{}''::jsonb');

SELECT pg_temp.dts_schema_column('exam_versions','created_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_versions','updated_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_versions','deleted_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

DO $body$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.exam_versions'::regclass AND contype='p') THEN
   ALTER TABLE public.exam_versions ADD CONSTRAINT pk_exam_versions PRIMARY KEY(id);
 ELSIF NOT EXISTS(SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='id'
   WHERE c.conrelid='public.exam_versions'::regclass AND c.contype='p' AND c.conkey=ARRAY[a.attnum]::smallint[]) THEN
   RAISE EXCEPTION 'Unexpected primary key on exam_versions';
 END IF;
END;
$body$;

CREATE TABLE IF NOT EXISTS public.exam_sessions (
  id UUID            NOT NULL    DEFAULT gen_random_uuid(),
  exam_version_id UUID            NOT NULL,
  user_id UUID            NOT NULL,
  device_id UUID            NULL,
  session_token UUID            NOT NULL,
  created_by UUID            NULL,
  updated_by UUID            NULL,
  attempt_no INT             NOT NULL    DEFAULT 1,
  duration_seconds INT             NULL,
  client_info JSONB           NULL,
  started_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  submitted_at TIMESTAMPTZ     NULL,
  expired_at TIMESTAMPTZ     NULL,
  status VARCHAR(30)     NOT NULL    DEFAULT 'READY',
  metadata JSONB           NOT NULL    DEFAULT '{}'::jsonb,
  idempotency_key VARCHAR(100)    NULL,
  version INT             NOT NULL    DEFAULT 0,
  created_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMPTZ     NULL
);

SELECT pg_temp.dts_schema_column('exam_sessions','id','UUID            NOT NULL    DEFAULT gen_random_uuid()','uuid'::regtype,true,'gen_random_uuid()');

SELECT pg_temp.dts_schema_column('exam_sessions','exam_version_id','UUID            NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','user_id','UUID            NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','device_id','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','session_token','UUID            NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','created_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','updated_by','UUID            NULL','uuid'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','attempt_no','INT             NOT NULL    DEFAULT 1','integer'::regtype,true,'1');

SELECT pg_temp.dts_schema_column('exam_sessions','duration_seconds','INT             NULL','integer'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','client_info','JSONB           NULL','jsonb'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','started_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_sessions','submitted_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','expired_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','status','VARCHAR(30)     NOT NULL    DEFAULT ''READY''','varchar'::regtype,true,'''READY''');

SELECT pg_temp.dts_schema_column('exam_sessions','metadata','JSONB           NOT NULL    DEFAULT ''{}''::jsonb','jsonb'::regtype,true,'''{}''::jsonb');

SELECT pg_temp.dts_schema_column('exam_sessions','idempotency_key','VARCHAR(100)    NULL','varchar'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_sessions','version','INT             NOT NULL    DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_sessions','created_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_sessions','updated_at','TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_sessions','deleted_at','TIMESTAMPTZ     NULL','timestamptz'::regtype,false,NULL);

DO $body$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.exam_sessions'::regclass AND contype='p') THEN
   ALTER TABLE public.exam_sessions ADD CONSTRAINT pk_exam_sessions PRIMARY KEY(id);
 ELSIF NOT EXISTS(SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='id'
   WHERE c.conrelid='public.exam_sessions'::regclass AND c.contype='p' AND c.conkey=ARRAY[a.attnum]::smallint[]) THEN
   RAISE EXCEPTION 'Unexpected primary key on exam_sessions';
 END IF;
END;
$body$;

CREATE TABLE IF NOT EXISTS public.exam_session_answers (
  id UUID NOT NULL DEFAULT gen_random_uuid(),
  exam_session_id UUID NOT NULL,
  question_id UUID NOT NULL,
  selected_answer JSONB,
  display_snapshot JSONB,
  is_correct BOOLEAN,
  score NUMERIC(5,2),
  answered_at TIMESTAMPTZ NOT NULL,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT pg_temp.dts_schema_column('exam_session_answers','id','UUID NOT NULL DEFAULT gen_random_uuid()','uuid'::regtype,true,'gen_random_uuid()');

SELECT pg_temp.dts_schema_column('exam_session_answers','exam_session_id','UUID NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_session_answers','question_id','UUID NOT NULL','uuid'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_session_answers','selected_answer','JSONB','jsonb'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_session_answers','display_snapshot','JSONB','jsonb'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_session_answers','is_correct','BOOLEAN','boolean'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_session_answers','score','NUMERIC(5,2)','numeric'::regtype,false,NULL);

SELECT pg_temp.dts_schema_column('exam_session_answers','answered_at','TIMESTAMPTZ NOT NULL','timestamptz'::regtype,true,NULL);

SELECT pg_temp.dts_schema_column('exam_session_answers','version','INT NOT NULL DEFAULT 0','integer'::regtype,true,'0');

SELECT pg_temp.dts_schema_column('exam_session_answers','created_at','TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

SELECT pg_temp.dts_schema_column('exam_session_answers','updated_at','TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP','timestamptz'::regtype,true,'CURRENT_TIMESTAMP');

DO $body$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.exam_session_answers'::regclass AND contype='p') THEN
   ALTER TABLE public.exam_session_answers ADD CONSTRAINT pk_exam_session_answers PRIMARY KEY(id);
 ELSIF NOT EXISTS(SELECT 1 FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='id'
   WHERE c.conrelid='public.exam_session_answers'::regclass AND c.contype='p' AND c.conkey=ARRAY[a.attnum]::smallint[]) THEN
   RAISE EXCEPTION 'Unexpected primary key on exam_session_answers';
 END IF;
END;
$body$;
