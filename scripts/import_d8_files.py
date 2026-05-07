#!/usr/bin/env python3
"""Import the D8 file metadata into D11's directus_files, then rewrite the
integer file-FK columns (hauptbild, bild, ...) and the two M2M junctions
to point at the new UUIDs. Finally, re-create the file relations in D11
that were dropped earlier (when no UUID mapping existed yet).

Preconditions:
  - The waldseite SQL dump zip is in the repo root (or path passed as $1).
  - Local Postgres is up and the directus DB is populated by waldseite_bootstrap.
  - directus/uploads/ already contains the actual file blobs (UUID-named).
  - Directus is running and reachable at http://localhost:8055.

Usage:
  python3 scripts/import_d8_files.py [DUMP_ZIP]
"""
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
import urllib.error
import zipfile

DUMP = sys.argv[1] if len(sys.argv) > 1 else None
URL = "http://localhost:8055"
EMAIL = "admin@example.com"
PASSWORD = "admin"

PGHOST = os.environ.get("PGHOST", "localhost")
PGPORT = os.environ.get("PGPORT", "5435")
PGUSER = "directus"
PGPASS = "password"
PGDB = "directus"

# Single-image FK columns (D8 integer FK to directus_files): (collection, field)
SINGLE_FILE_FKS = [
    ("wohnungen",     "hauptbild"),
    ("haeuser",       "hauptbild"),
    ("ausfluege",     "bild"),
    ("news",          "bild"),
    ("einstellungen", "default_bild_ausfluege"),
    ("startseite",    "familienbild"),
    ("galerie",       "bild"),
]

# M2M junction tables that point at directus_files
JUNCTIONS = [
    # (junction_collection, parent_collection, parent_fk, file_fk, alias_on_parent, alias_on_files)
    ("haeuser_directus_files",   "haeuser",   "haeuser_id",   "directus_files_id", "weitere_bilder", "haeuser"),
    ("wohnungen_directus_files", "wohnungen", "wohnungen_id", "directus_files_id", "weitere_bilder", "wohnungen"),
]

# Order of fields in D8 directus_files INSERT VALUES tuples
D8_FIELDS = ["id", "storage", "private_hash", "filename_disk", "filename_download",
             "title", "type", "uploaded_by", "uploaded_on", "charset", "filesize",
             "width", "height", "duration", "embed", "folder", "description",
             "location", "tags", "checksum", "metadata"]


def parse_d8_files(zip_path):
    """Return list of dicts from the dump's directus_files INSERT."""
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(zip_path) as z:
        sql_name = next(n for n in z.namelist() if n.endswith(".sql"))
        z.extract(sql_name, tmp)
        with open(os.path.join(tmp, sql_name)) as f:
            sql = f.read()

    m = re.search(r"INSERT INTO `directus_files` VALUES (.+?);\n/\*!", sql, re.DOTALL)
    if not m:
        sys.exit("Couldn't find directus_files INSERT in dump.")
    body = m.group(1)

    # Hand-rolled tuple parser, since D8 rows can contain commas inside
    # quoted strings (titles, descriptions).
    rows = []
    i = 0
    while i < len(body):
        if body[i] != "(":
            i += 1
            continue
        i += 1
        fields = []
        while body[i] != ")":
            if body[i] == "'":
                i += 1
                start = i
                while i < len(body):
                    if body[i] == "\\":
                        i += 2
                    elif body[i] == "'":
                        break
                    else:
                        i += 1
                # Unescape MySQL string escapes: \' \" \\ \n
                raw = body[start:i]
                val = (raw.replace("\\'", "'")
                          .replace('\\"', '"')
                          .replace("\\\\", "\\")
                          .replace("\\n", "\n")
                          .replace("\\r", "\r")
                          .replace("\\t", "\t"))
                fields.append(val)
                i += 1
            elif body[i:i + 4] == "NULL":
                fields.append(None)
                i += 4
            else:
                start = i
                while i < len(body) and body[i] not in ",)":
                    i += 1
                fields.append(body[start:i])
            if body[i] == ",":
                i += 1
        i += 1  # skip ')'
        rows.append(dict(zip(D8_FIELDS, fields)))
        if i < len(body) and body[i] == ",":
            i += 1
    return rows


def psql(sql, db=PGDB):
    """Run a SQL statement via psql. Caller is responsible for any quoting."""
    env = dict(os.environ)
    env["PGPASSWORD"] = PGPASS
    p = subprocess.run(
        ["psql", "-h", PGHOST, "-p", str(PGPORT), "-U", PGUSER, "-d", db,
         "-v", "ON_ERROR_STOP=1", "-c", sql],
        env=env, capture_output=True, text=True,
    )
    if p.returncode != 0:
        sys.exit(f"psql error:\n{p.stderr}\nSQL: {sql[:200]}")
    return p.stdout


def psql_copy(sql, stdin_data):
    """Run a `\\copy` via psql with stdin."""
    env = dict(os.environ)
    env["PGPASSWORD"] = PGPASS
    p = subprocess.run(
        ["psql", "-h", PGHOST, "-p", str(PGPORT), "-U", PGUSER, "-d", PGDB,
         "-v", "ON_ERROR_STOP=1", "-c", sql],
        env=env, input=stdin_data, capture_output=True, text=True,
    )
    if p.returncode != 0:
        sys.exit(f"psql copy error:\n{p.stderr}")


def http(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(URL + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        body_txt = e.read().decode()
        if "already exists" in body_txt or "already has an associated relationship" in body_txt:
            return {"_already": True}
        print(f"  HTTP {e.code} on {method} {path}: {body_txt[:300]}", file=sys.stderr)
        return None


def login():
    return http("POST", "/auth/login", body={"email": EMAIL, "password": PASSWORD})["data"]["access_token"]


def find_dump():
    if DUMP and os.path.exists(DUMP):
        return DUMP
    matches = sorted([f for f in os.listdir(".") if f.startswith("k113427_waldseite_") and f.endswith(".sql.zip")],
                     reverse=True)
    if not matches:
        sys.exit("No dump zip found in cwd. Pass path as first arg.")
    return matches[0]


def main():
    dump = find_dump()
    print(f"Reading {dump}")
    rows = parse_d8_files(dump)
    print(f"  {len(rows)} D8 file rows")

    # Build int_id → uuid map (UUID is filename_disk minus extension).
    int_to_uuid = {}
    tsv_lines = []
    for r in rows:
        int_id = int(r["id"])
        disk = r["filename_disk"]
        uuid = disk.rsplit(".", 1)[0]
        int_to_uuid[int_id] = uuid

        # Build a TSV row with the columns we care about.
        def cell(v):
            if v is None or v == "":
                return r"\N"
            # Replace tabs / newlines (TSV-unsafe) with spaces.
            return str(v).replace("\t", " ").replace("\n", " ").replace("\r", " ")

        tsv_lines.append("\t".join([
            uuid,
            cell(r["storage"]) or "local",
            cell(r["filename_disk"]),
            cell(r["filename_download"]),
            cell(r["title"]),
            cell(r["type"]),
            cell(r["charset"]),
            cell(r["filesize"]) or "0",
            cell(r["width"]),
            cell(r["height"]),
            cell(r["duration"]),
            cell(r["embed"]),
            cell(r["description"]),
            cell(r["location"]),
            cell(r["tags"]),
            cell(r["uploaded_on"]),
        ]))
    tsv = "\n".join(tsv_lines) + "\n"

    # 1. Wipe any prior import attempt and bulk-insert via COPY.
    print("Inserting D8 file metadata into directus_files…")
    psql("DELETE FROM directus_files WHERE storage = 'local' AND uploaded_by IS NULL;")
    psql_copy(
        r"\copy directus_files (id, storage, filename_disk, filename_download, "
        "title, type, charset, filesize, width, height, duration, embed, "
        "description, location, tags, uploaded_on) "
        r"FROM STDIN WITH (FORMAT text, DELIMITER E'\t', NULL '\N')",
        tsv,
    )
    n = psql("SELECT count(*) FROM directus_files;").strip().splitlines()[-2].strip()
    print(f"  directus_files now has {n} rows")

    # 2. For each integer FK column, build a temp mapping and ALTER+UPDATE.
    # We use a single CTE per ALTER to avoid keeping a permanent mapping table.
    mapping_values = ",\n  ".join(f"({k}, '{v}')" for k, v in int_to_uuid.items())

    print("Rewriting single-image FK columns to UUID…")
    for tbl, col in SINGLE_FILE_FKS:
        # Add tmp uuid column, populate from mapping, drop integer, rename.
        sql = f"""
        ALTER TABLE {tbl} ADD COLUMN IF NOT EXISTS {col}_uuid uuid;
        WITH map(int_id, uu) AS (VALUES
          {mapping_values}
        )
        UPDATE {tbl} SET {col}_uuid = map.uu::uuid
          FROM map WHERE {tbl}.{col} = map.int_id;
        ALTER TABLE {tbl} DROP COLUMN {col};
        ALTER TABLE {tbl} RENAME COLUMN {col}_uuid TO {col};
        """
        psql(sql)
        print(f"  {tbl}.{col} -> uuid")

    print("Rewriting M2M junctions to UUID…")
    for jc, parent, parent_fk, file_fk, _, _ in JUNCTIONS:
        sql = f"""
        ALTER TABLE {jc} ADD COLUMN IF NOT EXISTS {file_fk}_uuid uuid;
        WITH map(int_id, uu) AS (VALUES
          {mapping_values}
        )
        UPDATE {jc} SET {file_fk}_uuid = map.uu::uuid
          FROM map WHERE {jc}.{file_fk} = map.int_id;
        ALTER TABLE {jc} DROP COLUMN {file_fk};
        ALTER TABLE {jc} RENAME COLUMN {file_fk}_uuid TO {file_fk};
        """
        psql(sql)
        print(f"  {jc}.{file_fk} -> uuid")

    # 3. Wire up the new relations in Directus.
    token = login()
    print("\nCreating relations and aliases via API…")

    # Single-image M2O fields → use file-image interface.
    for coll, field in SINGLE_FILE_FKS:
        # Patch the field's meta first (pgloader-tracked column starts with no meta).
        # The field meta was previously set to interface=input,readonly,hidden by
        # track_interfaces; flip it to file-image so it shows the picker.
        http("PATCH", f"/fields/{coll}/{field}", token, body={"meta": {
            "interface": "file-image", "special": ["file"], "hidden": False, "readonly": False,
            "display": "image", "display_options": {},
        }})
        res = http("POST", "/relations", token, body={
            "collection": coll, "field": field, "related_collection": "directus_files",
            "meta": {},
        })
        if res and res.get("_already"):
            print(f"  M2O {coll}.{field} → directus_files (exists)")
        else:
            print(f"  M2O {coll}.{field} → directus_files")

    # M2M junctions: aliases on both sides + two relation rows each.
    for jc, parent, parent_fk, file_fk, alias_p, alias_f in JUNCTIONS:
        # Alias on parent: use the dedicated `files` interface so the list
        # renders thumbnails instead of bare filenames.
        http("POST", f"/fields/{parent}", token, body={
            "field": alias_p, "type": "alias",
            "meta": {"interface": "files", "special": ["m2m"],
                     "display": "related-values",
                     "display_options": {"template": "{{ " + file_fk + ".$thumbnail }}"}},
        })
        # Alias on directus_files: stay with list-m2m, the parents aren't
        # files so a thumbnail picker isn't appropriate.
        http("POST", "/fields/directus_files", token, body={
            "field": alias_f, "type": "alias",
            "meta": {"interface": "list-m2m", "special": ["m2m"],
                     "options": {"template": "{{ " + parent_fk + ".name }}"}},
        })
        # Junction → parent
        http("POST", "/relations", token, body={
            "collection": jc, "field": parent_fk, "related_collection": parent,
            "meta": {"one_field": alias_p, "junction_field": file_fk},
        })
        # Junction → directus_files
        http("POST", "/relations", token, body={
            "collection": jc, "field": file_fk, "related_collection": "directus_files",
            "meta": {"one_field": alias_f, "junction_field": parent_fk},
        })
        print(f"  M2M {parent} ↔ directus_files via {jc}")

    print("\nDone. Re-export schema/snapshot.json and verify the UI.")


if __name__ == "__main__":
    main()
