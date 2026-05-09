#!/usr/bin/env bash
# Rewrites absolute Directus asset URLs in WYSIWYG fields to the path-only
# form `/directus/assets/<uuid>`. Two patterns:
#
#   1. D8 legacy:  https?://<host>/public/uploads/<project>/originals/<uuid>.<ext>
#                  → /directus/assets/<uuid>
#   2. D11 absolute: https?://<host>/directus/assets/<uuid>
#                    → /directus/assets/<uuid>
#
# Idempotent — running twice is a no-op. The list of (table, column) pairs
# matches every field whose Directus interface is `input-rich-text-html` per
# schema/snapshot.json. If a new WYSIWYG field is added, append it below.

set -euo pipefail

psql -h "$PGHOST" -p "$PGPORT" -U directus -d directus -v ON_ERROR_STOP=1 <<'SQL'
DO $$
DECLARE
  cols text[][] := ARRAY[
    ARRAY['einzelseiten_translations', 'text'],
    ARRAY['haeuser_translations',      'beschreibung'],
    ARRAY['haeuser_translations',      'anreisetext'],
    ARRAY['haeuser_translations',      'ausstattung'],
    ARRAY['haeuser_translations',      'buchungstext'],
    ARRAY['wohnungen_translations',    'beschreibung'],
    ARRAY['news_translations',         'text'],
    ARRAY['startseite_translations',   'haupttext'],
    ARRAY['ausfluege_translations',    'beschreibung'],
    ARRAY['gaestebuch',                'text'],
    ARRAY['mails',                     'zusage']
  ];
  patterns text[][] := ARRAY[
    ARRAY['https?://[^/"\s)]+/public/uploads/[^/]+/originals/([0-9a-f-]{36})\.[a-zA-Z0-9]+', '/directus/assets/\1'],
    ARRAY['https?://[^/"\s)]+/directus/assets/([0-9a-f-]{36})',                              '/directus/assets/\1']
  ];
  c text[];
  p text[];
  n_rows integer;
BEGIN
  FOREACH c SLICE 1 IN ARRAY cols LOOP
    FOREACH p SLICE 1 IN ARRAY patterns LOOP
      EXECUTE format(
        'UPDATE %I SET %I = regexp_replace(%I, $1, $2, ''g'') WHERE %I ~ $1',
        c[1], c[2], c[2], c[2]
      ) USING p[1], p[2];
      GET DIAGNOSTICS n_rows = ROW_COUNT;
      IF n_rows > 0 THEN
        RAISE NOTICE '%.%: rewrote % rows', c[1], c[2], n_rows;
      END IF;
    END LOOP;
  END LOOP;
END$$;
SQL
