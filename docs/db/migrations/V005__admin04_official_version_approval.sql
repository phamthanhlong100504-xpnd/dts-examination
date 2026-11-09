-- BE Admin 04 G3: append-only review audit plus guarded mutable review state.
-- Run only via an approved, checksummed schema transaction after verified V004.
-- No changes to exam versions, sessions, student results, or existing schema migrations.
CREATE TABLE public.admin_official_version_reviews (
    id UUID PRIMARY KEY,
    exam_version_id UUID NOT NULL REFERENCES public.exam_versions(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('IN_REVIEW','APPROVED','REJECTED')),
    fingerprint CHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    submitted_by UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    reason VARCHAR(500),
    CONSTRAINT ck_official_review_decision CHECK (
        (status='IN_REVIEW' AND decided_by IS NULL AND decided_at IS NULL AND reason IS NULL)
        OR (status='APPROVED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND reason IS NULL)
        OR (status='REJECTED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND reason IS NOT NULL)
    ),
    CONSTRAINT ck_official_review_two_people CHECK (decided_by IS NULL OR decided_by<>submitted_by)
);
CREATE UNIQUE INDEX ux_official_review_active ON public.admin_official_version_reviews(exam_version_id)
    WHERE status IN ('IN_REVIEW','APPROVED');
CREATE INDEX ix_official_review_history ON public.admin_official_version_reviews(exam_version_id,submitted_at DESC,id DESC);

CREATE TABLE public.admin_official_version_editor_events (
    id UUID PRIMARY KEY,
    exam_version_id UUID NOT NULL REFERENCES public.exam_versions(id),
    actor_id UUID NOT NULL,
    edited_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_official_editors_by_actor
    ON public.admin_official_version_editor_events(exam_version_id,actor_id);

CREATE TABLE public.admin_official_review_audit (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES public.admin_official_version_reviews(id),
    exam_version_id UUID NOT NULL REFERENCES public.exam_versions(id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('SUBMIT','APPROVE','REJECT')),
    actor_id UUID NOT NULL,
    fingerprint CHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_official_review_audit_subject ON public.admin_official_review_audit(exam_version_id,occurred_at DESC);
CREATE FUNCTION public.dts_official_review_audit_immutable() RETURNS TRIGGER
    LANGUAGE plpgsql AS $body$
BEGIN
    RAISE EXCEPTION 'Official review audit is append-only';
END;
$body$;
CREATE TRIGGER trg_official_review_audit_immutable
    BEFORE UPDATE OR DELETE ON public.admin_official_review_audit
    FOR EACH ROW EXECUTE FUNCTION public.dts_official_review_audit_immutable();
