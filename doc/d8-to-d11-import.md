# Importing a fresh waldseite Directus 8 dump into Directus 11

The legacy waldseite site runs Directus 8 / MariaDB. This repo runs Directus 11 / Postgres
and replaces it. Until cutover, prod is still on D8; we re-import its dumps periodically
to keep this side current. This doc explains how to do that import.

For the orthogonal MariaDB → PostgreSQL migration tooling (the festival-side transition
that gave us pgloader, `pg_migrate.sh`, etc.), see `MIGRATION.md`.

## TL;DR

```bash
cp <new-dump>.sql.zip ./                # drop the dump in repo root
rsync -a prod:/path/to/uploads/ directus/uploads/   # sync file blobs
nix run .#waldseite-bootstrap           # one command, end-to-end
```

Login afterwards: `admin@example.com` / `admin` (override via `ADMIN_EMAIL` /
`ADMIN_PASSWORD` env vars).

## Preconditions

- `nix run .#dev` is up — the bootstrap needs MariaDB on 3306, Postgres on 5435,
  Directus reachable on 8055.
- `directus/.env` has `DB_CLIENT=pg` plus the required `KEY` / `SECRET` (see
  `directus/.env.example`).
- A waldseite SQL dump zip is in the repo root (filename pattern
  `k113427_waldseite_*.sql.zip`), or pass an explicit path:
  `nix run .#waldseite-bootstrap -- /path/to/dump.sql.zip`.
- `directus/uploads/` is populated with the UUID-named blobs the dump references.
  See *Files folder must be current* under Caveats.

## What the bootstrap does

The orchestrator is `scripts/waldseite_bootstrap.sh`, run via the `waldseite-bootstrap`
nix app in `apps.nix`. It chains the following stages — each can also be run by hand
during debugging:

1. **`scripts/load_waldseite_dump.sh`** — unzips the dump, strips `directus_*` tables
   (D8 system tables would collide with D11's), loads what's left into a temp MariaDB
   `waldseite_directus` database. Idempotent: drops + recreates that DB on each run.
2. **PG reset** — drops the `directus` PG role + database. Destructive; see Caveats.
3. **`npx directus bootstrap`** — creates the D11 system tables (`directus_*`) and
   the first admin user (uses `ADMIN_EMAIL` / `ADMIN_PASSWORD` env vars).
4. **`pgloader pg_migration/waldseite.first.load`** — pumps the user-content tables
   from temp MariaDB into PG, creating tables + indexes + data. The CAST rule maps
   MySQL `char(N)` to varchar (avoids the bpchar-padding bug that broke the
   translations interface) and `datetime`/`date` zero-values to NULL.
5. **`scripts/align_fk_types.py`** — `ALTER COLUMN ... TYPE bigint` on every FK column
   in user collections. pgloader maps `int unsigned auto_increment` PKs to bigint but
   plain `int unsigned` FK columns to integer; D11's relation resolver returns null
   for cross-type FKs even when the relation row exists. Reads the dump's
   `directus_relations` to build the FK list.
6. **`scripts/rename_to_d11_standard.py`** — drops the user's D8 custom names for
   D11 conventions: `sprachen → languages`, `*_translations.language → languages_code`,
   `article_translations → articles_translations`, parent-FK columns
   (`haus_id`, `wohnung_id`, `ausflug_id`, `einzelseite_id`, `fixed_seite_id`,
   `article`) → `<parent>_id`. Runs ALTER TABLE / RENAME COLUMN at the PG layer
   plus UPDATEs to `directus_collections` / `directus_fields` / `directus_relations` /
   `directus_presets` to keep metadata in sync. Also rewrites JSON refs in
   `display_options` (e.g. `"languageField":"language" → "code"`).
7. **Directus restart + admin-user check** — restart so it picks up the renamed
   collections; create the admin user via `npx directus users create` if it doesn't
   exist (covers re-runs where the role was dropped without env vars set).
8. **`scripts/track_collections.py`** — flips 28 collections from "untracked" to
   managed, applies icons / hidden / singleton flags from the dump's
   `directus_collections`. Without this, the collections don't show up in the
   admin Content module.
9. **`scripts/track_fields.py`** — sets a non-null `meta` on every auto-detected
   field (~150) so `directus schema snapshot` actually exports them. pgloader-created
   columns start with `meta: null` and silently drop out of snapshots otherwise.
10. **`scripts/track_relations.py`** — creates the M2O and user-collection M2M
    relations (`wohnungen.haus → haeuser`, `news_haeuser`, `ausfluege_haeuser`),
    plus their alias fields with templates that traverse through junctions
    (`{{ news_id.translations.titel }}`). Excludes the two `directus_files` M2M
    junctions — those are wired by `import_d8_files.py` once the int → uuid
    rewrite is done.
11. **`scripts/track_translations.py`** — wires the 9 translations relations using
    the D11-standard names from step 6. Sets up the parent's `translations` alias
    with `defaultLanguage: "de"`.
12. **`scripts/track_interfaces.py`** — applies D8 → D11 interface mapping from the
    dump's `directus_fields`: `wysiwyg → input-rich-text-html`, `primary-key →
    input/hidden/readonly`, `datetime-created → datetime + date-created special`,
    `status → select-dropdown`, `owner / file / files → input/hidden`, etc. Also
    sets `archive_field='status'` on every collection that has a status column.
13. **`scripts/import_d8_files.py`** — parses the dump's `directus_files`,
    bulk-COPYs the metadata into D11's `directus_files` (id = UUID from
    filename_disk), ALTER + UPDATEs the 7 single-image FK columns and 2 M2M
    junction columns from integer to UUID using the D8 mapping, then re-creates
    the file relations with `interface: file-image` (single) and `interface: files`
    (M2M, renders thumbnails).
14. **`scripts/track_display_templates.py`** — `display_template` per collection
    (`{{ translations.titel }}`, `{{ name }}`, etc.) so related-item previews
    show meaningful titles.
15. **`scripts/track_layouts.py`** — global `directus_presets` row per collection
    picking sensible default columns (e.g. `wohnungen`: `hauptbild / name / haus /
    status`). What you see when you open `/admin/content/<collection>`.

Everything is idempotent — re-running the bootstrap on a fresh dump produces the
same end state.

## Verification

After the bootstrap completes:

```bash
# 1. API check — translations + image FK + relation traversal
TOKEN=$(curl -s -X POST http://localhost:8055/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"admin"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['access_token'])")
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8055/items/haeuser/1?fields=name,hauptbild.filename_download,translations.languages_code.code"
# → {"data":{"name":"Das Alte Forsthaus","hauptbild":{"filename_download":"Ansicht.jpg"},"translations":[{"languages_code":{"code":"de"}},...]}}

# 2. UI check — open http://localhost:8055/admin/content/haeuser
#    Expect: list view shows hauptbild thumbnails + names, no FORBIDDEN error.

# 3. Schema snapshot diff — should reveal no structural drift
nix run .#schema-export
git diff schema/snapshot.json
# → only sequence-value / cosmetic changes, no new collections/fields/relations
```

## Caveats

- **Destructive on the Directus DB.** Step 2 drops the directus role and the
  whole directus database. Anything you added via the admin (extra users, tweaked
  presets, extension-managed data, comments, revisions) is wiped. Acceptable now,
  while the schema is frozen and we're iterating; problematic once we go live.
  Once cutover is past, re-imports should use a non-destructive data-only refresh
  flow instead — see *Not yet built*.

- **Files folder must be current.** `import_d8_files.py` parses the dump's
  `directus_files` rows and assumes the matching UUID-named blob is already on
  disk under `directus/uploads/`. If a row's blob is missing, the metadata gets
  inserted but `/assets/<uuid>` returns 404. Sync `directus/uploads/` from prod
  (rsync or an scp dump) before running the bootstrap — there's no `pull_uploads.sh`
  helper yet.

- **Schema is assumed frozen.** The D8 → D11 mappings in `track_translations.py`
  (parent-FK list), `track_collections.py` (icons/flags), `track_interfaces.py`
  (interface MAPPING dict), and `rename_to_d11_standard.py` (table/column rename
  tables) are hard-coded against the field/relation names in the current dump.
  If D8 prod adds a new field or collection before cutover, those scripts need
  the corresponding entry — they don't auto-discover. Adding a row is the
  ~one-line edit per script though, so this is documentation cost more than
  engineering cost.

## Not yet built

A non-destructive data-only refresh flow. Once the schema is locked
post-cutover, the right path is the festival-style `pg_migrate.sh` shape:
`directus bootstrap → directus schema apply schema/snapshot.json → pgloader data
only` — preserves admin users, presets, and any in-Directus state across
re-imports. ~30 lines based on the existing `scripts/pg_migrate.sh` pattern. Build
it when needed; until then the destructive bootstrap above is fine because
nothing in the D11 admin is yet load-bearing.

A `scripts/pull_uploads.sh` helper that mirrors prod's uploads directory locally
(equivalent to festival's `scripts/pull_files.sh`). Trivial; defer until the
manual rsync gets annoying.
