#!/usr/bin/env python3
"""Flip every auto-detected field on user collections from untracked
(meta: null) to tracked (meta: {}). Required so `directus schema snapshot`
exports the full column schema (otherwise schema apply on a fresh DB won't
recreate the tables).

Usage:
  python3 scripts/track_fields.py [DIRECTUS_URL] [EMAIL] [PASSWORD]

Idempotent: PATCHing a tracked field with {meta: {}} is a no-op.
"""
import json
import sys
import urllib.request
import urllib.error

URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8055"
EMAIL = sys.argv[2] if len(sys.argv) > 2 else "admin@example.com"
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else "admin"


def req(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(URL + path, data=data, method=method, headers=headers)
    with urllib.request.urlopen(r) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else None


def main():
    auth = req("POST", "/auth/login", body={"email": EMAIL, "password": PASSWORD})
    token = auth["data"]["access_token"]

    # All collections, then filter to user (non-directus_*) tracked ones.
    all_collections = req("GET", "/collections?limit=-1", token=token)["data"]
    user_colls = [c["collection"] for c in all_collections
                  if not c["collection"].startswith("directus_")
                  and c.get("meta") is not None]

    total = 0
    tracked = 0
    for coll in user_colls:
        fields = req("GET", f"/fields/{coll}", token=token)["data"]
        for f in fields:
            total += 1
            field = f["field"]
            # Only PATCH fields whose meta is missing or whose meta.id is None
            # (auto-detected). Already-tracked fields have meta.id set.
            if f.get("meta") and f["meta"].get("id") is not None:
                continue
            # Skip alias fields — they already exist with meta from track_relations.
            if f.get("type") == "alias":
                continue
            req("PATCH", f"/fields/{coll}/{field}", token=token, body={"meta": {}})
            tracked += 1
        print(f"  {coll}: {len(fields)} fields, {sum(1 for x in fields if x.get('meta') and x['meta'].get('id'))} already tracked")

    print(f"\nTotal: {total} fields, {tracked} newly tracked.")


if __name__ == "__main__":
    main()
