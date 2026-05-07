-- Dedupe *_translations tables and add UNIQUE (parent_id, languages_code).
--
-- The D8→D11 importer (scripts/track_translations.py) does not enforce
-- uniqueness on (parent_id, languages_code), and a re-import can leave
-- duplicate rows in any *_translations table. The dschema-generated _t views
-- LEFT JOIN once per locale alias, so duplicate translation rows cartesian-
-- multiply view rows. Adding the constraint at the SQL layer makes the
-- importer fail loudly on its next bad run.
--
-- Strategy per table:
--   1. Keep the row with MAX(id) per (parent_id, languages_code) group.
--   2. ALTER TABLE … ADD CONSTRAINT … UNIQUE (parent_id, languages_code)
--      if not already present.
--
-- Idempotent: re-running on a clean DB is a no-op.

BEGIN;

DO $$
DECLARE
  r          record;
  parent     text;
  fkcol      text;
  cname      text;
  ddl        text;
  deleted    integer;
BEGIN
  FOR r IN
    SELECT table_name FROM information_schema.tables
     WHERE table_name LIKE '%\_translations' ESCAPE '\'
       AND table_schema = 'public'
       AND table_name NOT LIKE 'directus%'
     ORDER BY 1
  LOOP
    parent := regexp_replace(r.table_name, '_translations$', '');
    fkcol  := parent || '_id';
    cname  := r.table_name || '_parent_locale_uq';

    -- Dedupe: keep MAX(id) per (parent_id, languages_code).
    EXECUTE format(
      'WITH d AS (
         DELETE FROM %1$I
          WHERE id IN (
            SELECT id FROM %1$I t
             WHERE id < (SELECT MAX(id) FROM %1$I
                          WHERE %2$I = t.%2$I
                            AND languages_code = t.languages_code))
          RETURNING 1
       ) SELECT count(*) FROM d', r.table_name, fkcol)
    INTO deleted;
    IF deleted > 0 THEN
      RAISE NOTICE '%: deleted % duplicate row(s)', r.table_name, deleted;
    END IF;

    -- Add UNIQUE constraint if missing.
    IF NOT EXISTS (
      SELECT 1 FROM pg_constraint
       WHERE conname = cname
         AND conrelid = format('public.%I', r.table_name)::regclass
    ) THEN
      ddl := format(
        'ALTER TABLE %I ADD CONSTRAINT %I UNIQUE (%I, languages_code)',
        r.table_name, cname, fkcol);
      EXECUTE ddl;
      RAISE NOTICE '%: added constraint %', r.table_name, cname;
    END IF;
  END LOOP;
END $$;

COMMIT;
