#!/usr/bin/env python3
"""ALTER all user-collection FK columns to bigint so they match the parent
collections' bigint primary keys. pgloader maps MySQL `int unsigned`
auto-increment PKs to bigint but plain `int unsigned` FK columns to
integer, and Directus's REST resolver returns null for cross-type FKs
even when the relation is registered in directus_relations.

Reads the D8 dump's directus_relations to discover which columns are FKs;
the schema is frozen, so this is reliable.

Skips:
  - directus_* tables (managed by Directus)
  - file FK columns (already converted to uuid by import_d8_files.py)
  - already-bigint columns (psql ALTER is a no-op anyway)

Usage:
  python3 scripts/align_fk_types.py [DUMP_ZIP]
"""
import os
import re
import subprocess
import sys
import tempfile
import zipfile

DUMP = sys.argv[1] if len(sys.argv) > 1 else None
PGHOST = os.environ.get("PGHOST", "localhost")
PGPORT = os.environ.get("PGPORT", "5435")
PGUSER = "directus"
PGPASS = "password"
PGDB = "directus"

# Columns already migrated to uuid by import_d8_files.py — skip.
UUID_COLUMNS = {
    ("haeuser", "hauptbild"),
    ("wohnungen", "hauptbild"),
    ("ausfluege", "bild"),
    ("news", "bild"),
    ("einstellungen", "default_bild_ausfluege"),
    ("startseite", "familienbild"),
    ("galerie", "bild"),
    ("haeuser_directus_files", "directus_files_id"),
    ("wohnungen_directus_files", "directus_files_id"),
}


def find_dump():
    if DUMP and os.path.exists(DUMP):
        return DUMP
    matches = sorted([f for f in os.listdir(".") if f.startswith("k113427_waldseite_") and f.endswith(".sql.zip")],
                     reverse=True)
    if not matches:
        sys.exit("No dump found. Pass path as first arg.")
    return matches[0]


def parse_relations(zip_path):
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(zip_path) as z:
        sql_name = next(n for n in z.namelist() if n.endswith(".sql"))
        z.extract(sql_name, tmp)
        with open(os.path.join(tmp, sql_name)) as f:
            sql = f.read()
    m = re.search(r"INSERT INTO `directus_relations` VALUES (.+?);\n", sql, re.DOTALL)
    if not m:
        return []
    body = m.group(1)
    rows = re.findall(
        r"\((\d+),'([^']+)','([^']+)',(?:'([^']+)'|NULL),(?:'([^']+)'|NULL),(?:'([^']+)'|NULL)\)",
        body,
    )
    return [{"id": r[0], "many_coll": r[1], "many_field": r[2],
             "one_coll": r[3], "one_field": r[4], "junction": r[5]}
            for r in rows]


def psql(sql):
    env = dict(os.environ)
    env["PGPASSWORD"] = PGPASS
    p = subprocess.run(
        ["psql", "-h", PGHOST, "-p", str(PGPORT), "-U", PGUSER, "-d", PGDB,
         "-v", "ON_ERROR_STOP=1", "-c", sql],
        env=env, capture_output=True, text=True,
    )
    return p


def main():
    dump = find_dump()
    rels = parse_relations(dump)
    targets = []
    for r in rels:
        coll, field = r["many_coll"], r["many_field"]
        if coll.startswith("directus_") or r["one_coll"].startswith("directus_"):
            continue
        if (coll, field) in UUID_COLUMNS:
            continue
        targets.append((coll, field))

    # Also include the parent FK on each *_translations table. (D8 lists them
    # via directus_relations rows above; nothing extra needed unless a row
    # is missing — which it isn't for our frozen dump.)

    print(f"Aligning {len(targets)} FK columns to bigint…")
    for coll, field in targets:
        sql = f"ALTER TABLE {coll} ALTER COLUMN {field} TYPE bigint;"
        p = psql(sql)
        if p.returncode == 0:
            print(f"  {coll}.{field}")
        else:
            # Skip silently when column doesn't exist or is already bigint
            # via another path (idempotent re-runs).
            err = p.stderr.strip().splitlines()[-1] if p.stderr else ""
            print(f"  {coll}.{field} — skipped ({err[:80]})")

    print("\nDone.")


if __name__ == "__main__":
    main()
