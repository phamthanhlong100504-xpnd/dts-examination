-- V003: restore the Screen12 default when upgrading an existing structure schema.
-- V001/V002 remain immutable. Changing a default affects future inserts only;
-- existing ACTIVE/DRAFT structures and their audit fields are preserved.
ALTER TABLE public.exam_structures ALTER COLUMN status SET DEFAULT 'DRAFT';
