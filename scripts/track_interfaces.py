#!/usr/bin/env python3
"""Apply D8-era field interfaces to D11 fields where the auto-detected default
isn't what the legacy site used. Reads the D8 dump's `directus_fields` rows
and translates each interface name to its D11 equivalent.

Currently handles WYSIWYG (D8 `wysiwyg` → D11 `input-rich-text-html`).
Other D8 interfaces (file, dropdown, date, numeric…) can be added here as
needed; the dispatcher is structured to make it obvious where to extend.

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

# D8 interface name → (D11 interface, special, options)
# `special` and `options` are merged into the existing field meta on PATCH.
MAPPING = {
    "wysiwyg": ("input-rich-text-html", None, None),
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
    """Extract directus_fields rows from the D8 SQL dump.
    Returns list of dicts: {collection, field, type, interface}."""
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(dump_path) as z:
        sql_name = next(n for n in z.namelist() if n.endswith(".sql"))
        z.extract(sql_name, tmp)
        with open(os.path.join(tmp, sql_name)) as f:
            sql = f.read()

    block = re.search(r"INSERT INTO `directus_fields` VALUES (.+?);\n", sql, re.DOTALL)
    if not block:
        return []
    body = block.group(1)
    # Each row: (id,'collection','field','type','iface_or_NULL','options_or_NULL',...)
    rows = re.findall(
        r"\((\d+),'([^']+)','([^']+)','([^']+)',('(?:[^'\\]|\\.)*'|NULL),",
        body,
    )
    return [{"collection": c, "field": f, "type": t,
             "interface": (i[1:-1] if i != "NULL" else None)}
            for _, c, f, t, i in rows]


def req(method, path, token=None, body=None):
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
        print(f"  HTTP {e.code} on {method} {path}: {e.read().decode()[:300]}", file=sys.stderr)
        return None


def main():
    dump = find_dump()
    print(f"Reading D8 metadata from {dump}")
    fields = parse_d8_fields(dump)

    auth = req("POST", "/auth/login", body={"email": EMAIL, "password": PASSWORD})
    token = auth["data"]["access_token"]
    print(f"Logged in to {URL}\n")

    applied = 0
    for f in fields:
        # Skip system collections — those are managed by Directus core.
        if f["collection"].startswith("directus_"):
            continue
        d8_iface = f["interface"]
        if d8_iface not in MAPPING:
            continue
        d11_iface, special, options = MAPPING[d8_iface]
        meta = {"interface": d11_iface}
        if special is not None:
            meta["special"] = special
        if options is not None:
            meta["options"] = options
        path = f"/fields/{f['collection']}/{f['field']}"
        if req("PATCH", path, token=token, body={"meta": meta}) is not None:
            print(f"  {f['collection']}.{f['field']}: {d8_iface} → {d11_iface}")
            applied += 1

    print(f"\nApplied {applied} interface overrides.")


if __name__ == "__main__":
    main()
