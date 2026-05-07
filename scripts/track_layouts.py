#!/usr/bin/env python3
"""Configure default tabular list-view layouts for each user collection so
list pages show meaningful columns (titles, names, thumbnails) instead of
the raw relation arrays Directus shows by default.

Stored as a global directus_presets row per collection (user/role NULL),
which D11 falls back to when no user-specific preset exists. Re-running
this script overwrites those global presets — user-specific presets are
not touched.

Usage:
  python3 scripts/track_layouts.py [URL] [EMAIL] [PASSWORD]
"""
import json
import sys
import urllib.request
import urllib.error

URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8055"
EMAIL = sys.argv[2] if len(sys.argv) > 2 else "admin@example.com"
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else "admin"


# (collection, [columns shown in tabular layout])
# For translated collections we reference `translations.<title>` so D11
# shows the matching-language value with fallback.
LAYOUTS = [
    ("haeuser",      ["hauptbild", "name", "status", "translations"]),
    ("wohnungen",    ["hauptbild", "name", "haus", "status"]),
    ("ausfluege",    ["bild", "translations", "haeuser", "status"]),
    ("news",         ["bild", "translations", "haeuser", "status"]),
    ("galerie",      ["bild", "translations", "haus", "status"]),
    ("articles",     ["translations", "status"]),
    ("einzelseiten", ["translations", "status"]),
    ("fixe_seiten",  ["id", "translations", "status"]),
    ("gaestebuch",   ["name", "haus", "wohnung", "status", "text"]),
    ("languages",    ["code", "kurzname", "langname", "status"]),
]


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
    headers = {"Content-Type": "application/json"}
    data = json.dumps({"email": EMAIL, "password": PASSWORD}).encode()
    r = urllib.request.Request(URL + "/auth/login", data=data, method="POST", headers=headers)
    with urllib.request.urlopen(r) as resp:
        token = json.loads(resp.read())["data"]["access_token"]
    print(f"Logged in to {URL}\n")

    # Look up existing global presets so we can update rather than duplicate.
    existing = req("GET", "/presets?filter[user][_null]=true&filter[role][_null]=true&limit=-1", token)
    by_collection = {p["collection"]: p["id"] for p in (existing or {}).get("data", [])
                     if p.get("collection")}

    for coll, fields in LAYOUTS:
        body = {
            "collection": coll,
            "user": None,
            "role": None,
            "layout": "tabular",
            "layout_query": {"tabular": {"fields": fields, "page": 1}},
            "layout_options": {"tabular": {"widths": {}, "spacing": "cozy"}},
        }
        if coll in by_collection:
            res = req("PATCH", f"/presets/{by_collection[coll]}", token, body=body)
            print(f"  updated: {coll:14}  fields={fields}")
        else:
            res = req("POST", "/presets", token, body=body)
            print(f"  created: {coll:14}  fields={fields}")


if __name__ == "__main__":
    main()
