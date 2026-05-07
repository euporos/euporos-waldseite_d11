#!/usr/bin/env python3
"""Rename the legacy D8 translation tables/columns to Directus 11's
standard names so the translations interface needs zero custom config:

  Collection      sprachen              -> languages
  Collection      article_translations  -> articles_translations
  Column          *_translations.language          -> languages_code
  Column          articles_translations.article    -> articles_id
  Column          ausfluege_translations.ausflug_id -> ausfluege_id
  Column          einzelseiten_translations.einzelseite_id -> einzelseiten_id
  Column          fixe_seiten_translations.fixed_seite_id -> fixe_seiten_id
  Column          haeuser_translations.haus_id     -> haeuser_id
  Column          wohnungen_translations.wohnung_id -> wohnungen_id

Runs ALTER TABLE / RENAME COLUMN at the PG layer and then UPDATEs the
matching rows in directus_collections / directus_fields /
directus_relations / directus_presets so D11's metadata stays in sync
with the live schema.

Idempotent: each rename is guarded by a `IF EXISTS` check, so re-running
is a no-op once everything is on the new names. Safe to chain into
waldseite_bootstrap.sh.

Usage:
  python3 scripts/rename_to_d11_standard.py
"""
import os
import subprocess
import sys

PGHOST = os.environ.get("PGHOST", "localhost")
PGPORT = os.environ.get("PGPORT", "5435")
PGUSER = "directus"
PGPASS = "password"
PGDB = "directus"

# (table_old, table_new) — collection-level renames
TABLE_RENAMES = [
    ("sprachen",             "languages"),
    ("article_translations", "articles_translations"),
]

# (table, column_old, column_new) — column-level renames.
# Use the *new* table name where the table itself was renamed above.
COLUMN_RENAMES = [
    # language → languages_code on every translation table
    ("allgemeines_translations",  "language", "languages_code"),
    ("articles_translations",     "language", "languages_code"),
    ("ausfluege_translations",    "language", "languages_code"),
    ("einzelseiten_translations", "language", "languages_code"),
    ("fixe_seiten_translations",  "language", "languages_code"),
    ("galerie_translations",      "language", "languages_code"),
    ("haeuser_translations",      "language", "languages_code"),
    ("news_translations",         "language", "languages_code"),
    ("startseite_translations",   "language", "languages_code"),
    ("wohnungen_translations",    "language", "languages_code"),

    # parent FK → <parent_collection>_id
    ("articles_translations",     "article",        "articles_id"),
    ("ausfluege_translations",    "ausflug_id",     "ausfluege_id"),
    ("einzelseiten_translations", "einzelseite_id", "einzelseiten_id"),
    ("fixe_seiten_translations",  "fixed_seite_id", "fixe_seiten_id"),
    ("haeuser_translations",      "haus_id",        "haeuser_id"),
    ("wohnungen_translations",    "wohnung_id",     "wohnungen_id"),
]


def psql(sql):
    env = dict(os.environ)
    env["PGPASSWORD"] = PGPASS
    p = subprocess.run(
        ["psql", "-h", PGHOST, "-p", str(PGPORT), "-U", PGUSER, "-d", PGDB,
         "-v", "ON_ERROR_STOP=1", "-c", sql],
        env=env, capture_output=True, text=True,
    )
    if p.returncode != 0:
        print(f"psql error: {p.stderr}\nSQL: {sql[:200]}", file=sys.stderr)
        sys.exit(1)
    return p.stdout


def table_exists(name):
    out = psql(f"SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='{name}';")
    return "1 row" in out or "(1 row)" in out or "1\n" in out


def column_exists(table, column):
    out = psql(f"SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='{table}' AND column_name='{column}';")
    return "1 row" in out or "(1 row)" in out or "1\n" in out


def main():
    # --- Phase 1: rename tables ---
    for old, new in TABLE_RENAMES:
        if table_exists(old) and not table_exists(new):
            psql(f"ALTER TABLE {old} RENAME TO {new};")
            print(f"  table {old} → {new}")
        elif table_exists(new):
            print(f"  table {new} already renamed")

    # --- Phase 2: rename columns ---
    for table, old, new in COLUMN_RENAMES:
        if not table_exists(table):
            print(f"  skip: table {table} missing")
            continue
        if column_exists(table, old) and not column_exists(table, new):
            psql(f"ALTER TABLE {table} RENAME COLUMN {old} TO {new};")
            print(f"  column {table}.{old} → {new}")
        elif column_exists(table, new):
            pass  # already done

    # --- Phase 3: update Directus metadata to match the new names ---
    # directus_collections: rename the rows
    for old, new in TABLE_RENAMES:
        psql(f"UPDATE directus_collections SET collection='{new}' WHERE collection='{old}';")
        psql(f"UPDATE directus_fields SET collection='{new}' WHERE collection='{old}';")
        psql(f"UPDATE directus_relations SET many_collection='{new}' WHERE many_collection='{old}';")
        psql(f"UPDATE directus_relations SET one_collection='{new}' WHERE one_collection='{old}';")
        psql(f"UPDATE directus_presets SET collection='{new}' WHERE collection='{old}';")

    # directus_fields: rename per-(collection,field) rows
    for table, old, new in COLUMN_RENAMES:
        psql(f"UPDATE directus_fields SET field='{new}' WHERE collection='{table}' AND field='{old}';")
        # directus_relations: many_field, junction_field
        psql(f"UPDATE directus_relations SET many_field='{new}' WHERE many_collection='{table}' AND many_field='{old}';")
        psql(f"UPDATE directus_relations SET junction_field='{new}' WHERE junction_field='{old}';")

    # The translations alias's display_options.languageField should hold
    # the field on the LANGUAGES collection that stores the locale code,
    # i.e. `code`. The user's D8 setup stored `language` there (the field
    # name on the junction, which is the wrong layer of indirection).
    # Rewrite to `code` so D11's translations display walks
    # translations.languages_code.code instead of .language.
    for stale in ('"language"', '"languages_code"'):
        psql(f"""
        UPDATE directus_fields
           SET display_options = replace(display_options::text,
               '"languageField":{stale}',
               '"languageField":"code"')::json
         WHERE display_options::text LIKE '%"languageField":{stale}%';
        """)

    print("\nDone.")


if __name__ == "__main__":
    main()
