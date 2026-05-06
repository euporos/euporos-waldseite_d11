#!/usr/bin/env python3
"""Apply D8-era field interfaces to D11 fields where the auto-detected default
isn't what the legacy site used. Reads the D8 dump's `directus_fields` rows
and translates each interface name to its D11 equivalent.

Also sets up some collection-level conventions implied by D8's per-field
interfaces (e.g. when a collection has a `status` field with the D8 `status`
interface, configure D11's archive_field on that collection).

Usage:
  python3 scripts/track_interfaces.py [DUMP_ZIP] [URL] [EMAIL] [PASSWORD]
"""
import json
import os
import re
import sys
import tempfile
import urllib.request
import urllib.error
import zipfile

DUMP = sys.argv[1] if len(sys.argv) > 1 else None
URL = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8055"
EMAIL = sys.argv[3] if len(sys.argv) > 3 else "admin@example.com"
PASSWORD = sys.argv[4] if len(sys.argv) > 4 else "admin"


# D8 interface name → D11 meta patch (merged into existing field meta).
# Values that should be cleared back to null are written as None — `req`
# preserves them in the JSON body.
MAPPING = {
    # Rich-text body fields.
    "wysiwyg":           {"interface": "input-rich-text-html"},

    # Auto-increment integer primary keys: hide on form, mark readonly.
    "primary-key":       {"interface": "input", "hidden": True, "readonly": True},

    # D8 status field → D11 select-dropdown with the same three values.
    # We also set the collection's archive_field separately (see below).
    "status":            {"interface": "select-dropdown",
                          "special": None,
                          "options": {"choices": [
                              {"text": "Published", "value": "published"},
                              {"text": "Draft",     "value": "draft"},
                              {"text": "Deleted",   "value": "deleted"},
                          ]},
                          "width": "full"},

    # System-managed timestamps. D11 uses the `date-created` / `date-updated`
    # specials to auto-populate them; we mark the field readonly + datetime UI.
    "datetime-created":  {"interface": "datetime",
                          "special": ["date-created"],
                          "readonly": True,
                          "display": "datetime",
                          "display_options": {"relative": True}},
    "datetime-updated":  {"interface": "datetime",
                          "special": ["date-updated"],
                          "readonly": True,
                          "display": "datetime",
                          "display_options": {"relative": True}},

    # D8 "owner" was a FK to the legacy users table (integer). It's not
    # portable to D11's UUID-keyed directus_users, so hide rather than wire.
    "owner":             {"interface": "input", "readonly": True, "hidden": True},

    # File pickers — D8 stored integer file IDs. Hide until the file
    # migration assigns UUIDs and we can re-wire properly.
    "file":              {"interface": "input", "readonly": True, "hidden": True},
    "files":             {"interface": "input", "readonly": True, "hidden": True},

    # Plain interfaces that map cleanly.
    "text-input":        {"interface": "input"},
    "textarea":          {"interface": "input-multiline"},
    "numeric":           {"interface": "input"},
    "date":              {"interface": "datetime", "options": {"includeTime": False}},
    "dropdown":          {"interface": "select-dropdown"},

    # The translation language picker on each `*_translations` row.
    "language":          {"interface": "select-dropdown-m2o", "special": ["m2o"]},
}




def find_dump():
    if DUMP and os.path.exists(DUMP):
        return DUMP
    matches = sorted([f for f in os.listdir(".") if f.startswith("k113427_waldseite_") and f.endswith(".sql.zip")],
                     reverse=True)
    if not matches:
        sys.exit("Error: no waldseite dump found. Pass the path as the first arg.")
    return matches[0]


def parse_d8_fields(dump_path):
    """Yield {collection, field, type, interface} dicts from the D8 dump."""
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(dump_path) as z:
        sql_name = next(n for n in z.namelist() if n.endswith(".sql"))
        z.extract(sql_name, tmp)
        with open(os.path.join(tmp, sql_name)) as f:
            sql = f.read()

    block = re.search(r"INSERT INTO `directus_fields` VALUES (.+?);\n", sql, re.DOTALL)
    if not block:
        return []
    body = block.group(1)
    rows = re.findall(
        r"\((\d+),'([^']+)','([^']+)','([^']+)',('(?:[^'\\]|\\.)*'|NULL),",
        body,
    )
    return [{"collection": c, "field": f, "type": t,
             "interface": (i[1:-1] if i != "NULL" else None)}
            for _, c, f, t, i in rows]


def req(method, path, token, body=None):
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(URL + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        print(f"  HTTP {e.code} on {method} {path}: {e.read().decode()[:300]}", file=sys.stderr)
        return None


def login():
    headers = {"Content-Type": "application/json"}
    data = json.dumps({"email": EMAIL, "password": PASSWORD}).encode()
    r = urllib.request.Request(URL + "/auth/login", data=data, method="POST", headers=headers)
    with urllib.request.urlopen(r) as resp:
        return json.loads(resp.read())["data"]["access_token"]


def main():
    dump = find_dump()
    print(f"Reading D8 metadata from {dump}")
    fields = parse_d8_fields(dump)
    token = login()
    print(f"Logged in to {URL}\n")

    # --- Field-level interface overrides ---
    applied = 0
    skipped = 0
    archive_collections = set()
    for f in fields:
        if f["collection"].startswith("directus_"):
            continue
        d8_iface = f["interface"]
        if d8_iface == "status":
            archive_collections.add(f["collection"])
        if d8_iface not in MAPPING:
            skipped += 1
            continue
        meta = dict(MAPPING[d8_iface])
        path = f"/fields/{f['collection']}/{f['field']}"
        if req("PATCH", path, token, body={"meta": meta}) is not None:
            applied += 1
    print(f"Applied {applied} field interface overrides ({skipped} D8 interfaces unmapped).")

    # --- Collection-level archive_field for collections with a status field ---
    for coll in sorted(archive_collections):
        meta = {"archive_field": "status",
                "archive_value": "deleted",
                "unarchive_value": "draft",
                "archive_app_filter": True}
        if req("PATCH", f"/collections/{coll}", token, body={"meta": meta}) is not None:
            print(f"  archive_field='status' on {coll}")

    print("\nDone.")


if __name__ == "__main__":
    main()
