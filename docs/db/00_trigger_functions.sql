-- Service-owned helper; does not replace another service's generic trigger function.
CREATE OR REPLACE FUNCTION public.dts_examination_set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$body$;
