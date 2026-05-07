#!/usr/bin/env python3
"""Create the M2O and M2M relations in Directus 11 from the D8 dump's metadata.

Translations relations are deliberately skipped — they require deciding how
the legacy `sprachen` collection should map onto D11's expected language
collection (with `code` field), which is a one-time judgment call. Configure
those via the UI after running this script (or extend it once the call is made).

Usage:
  python3 scripts/track_relations.py [DIRECTUS_URL] [EMAIL] [PASSWORD]

Idempotent-ish: re-creating an existing relation returns 400; we treat that
as success and move on.
"""
import json
import sys
import urllib.request
import urllib.error

URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8055"
EMAIL = sys.argv[2] if len(sys.argv) > 2 else "admin@example.com"
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else "admin"

# Plain M2O relations: (collection, field, related_collection, one_field_or_None)
# Source: D8 directus_relations rows where junction_field is NULL and the row
# isn't a translation FK.
#
# Note: skipping (sprachen, status, haeuser) — that D8 row was spurious
# (sprachen.status is a varchar workflow field, not an FK to haeuser.id).
M2O = [
    ("wohnungen",   "haus",     "haeuser",   None),
    ("gaestebuch",  "wohnung",  "wohnungen", None),
    ("gaestebuch",  "haus",     "haeuser",   "gaestestimmen"),  # reverse alias on haeuser
    ("galerie",     "haus",     "haeuser",   None),
]

# M2M junctions:
#   (junction_collection,
#    side_a_field, side_a_target, side_a_title_path,
#    side_b_field, side_b_target, side_b_title_path,
#    alias_on_a, alias_on_b)
# Each generates two relation rows + alias fields on both sides. The
# title_path for each side is the template fragment used when the OPPOSITE
# side displays junction rows (so e.g. when haeuser shows its `news` alias,
# we render `{{ news_id.translations.titel }}` per junction row).
#
# Excluded: `wohnungen_directus_files` and `haeuser_directus_files`. Their
# `directus_files_id` columns can only be wired after the file migration
# runs (see scripts/import_d8_files.py).
M2M = [
    ("news_haeuser",
     "news_id",   "news",    "translations.titel",
     "haeuser_id","haeuser", "name",
     "haeuser",   "news"),
    ("ausfluege_haeuser",
     "ausfluege_id", "ausfluege", "translations.titel",
     "haeuser_id",   "haeuser",   "name",
     "haeuser",      "ausfluege"),
]


def req(method, path, token=None, body=None, ok_codes=(200, 204)):
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
        if ("already exists" in body_txt
                or "RECORD_NOT_UNIQUE" in body_txt
                or "already has an associated relationship" in body_txt):
            return {"_already": True}
        if "cannot be implemented" in body_txt:
            return {"_fk_type_mismatch": True}
        print(f"  HTTP {e.code} on {method} {path}: {body_txt[:400]}", file=sys.stderr)
        raise


def login():
    r = req("POST", "/auth/login", body={"email": EMAIL, "password": PASSWORD})
    return r["data"]["access_token"]


def make_alias(token, collection, field, interface, special, options=None):
    body = {
        "field": field,
        "type": "alias",
        "meta": {
            "interface": interface,
            "special": special,
            "options": options or {},
        },
    }
    res = req("POST", f"/fields/{collection}", token=token, body=body)
    if res and res.get("_already"):
        print(f"    alias {collection}.{field} (exists)")
    else:
        print(f"    alias {collection}.{field} created")


def make_relation(token, collection, field, related, meta=None, schema=None):
    body = {"collection": collection, "field": field, "related_collection": related}
    if meta:
        body["meta"] = meta
    if schema is not None:
        body["schema"] = schema
    res = req("POST", "/relations", token=token, body=body)
    if res and res.get("_already"):
        tag = " (exists)"
    elif res and res.get("_fk_type_mismatch"):
        tag = " SKIPPED — FK type mismatch (manual fix needed)"
    else:
        tag = ""
    one = f" → one_field={meta.get('one_field')}" if meta and meta.get("one_field") else ""
    print(f"    relation {collection}.{field} -> {related}{one}{tag}")


def main():
    token = login()
    print(f"Logged in to {URL}")

    # The D8 dump had no DB-level FKs (relations were Directus-managed only),
    # and pgloader created PK columns as bigint while FK columns are 32-bit
    # integers — so D11 can't add real FK constraints. We register the
    # relations as metadata-only (no `schema` arg), matching D8 behavior.
    print("\n-- M2O relations (metadata only) --")
    for coll, field, related, one_field in M2O:
        meta = {}
        if one_field:
            meta["one_field"] = one_field
            make_alias(token, related, one_field, "list-o2m", ["o2m"])
        make_relation(token, coll, field, related, meta=meta or None)

    print("\n-- M2M junctions (metadata only) --")
    for jc, fa, ta, title_a, fb, tb, title_b, alias_a, alias_b in M2M:
        # The alias on side A lists junction rows; each row should display
        # the side-B item, traversed via fb. So template = {{ fb.title_b }}.
        make_alias(token, ta, alias_a, "list-m2m", ["m2m"],
                   options={"template": "{{ " + fb + "." + title_b + " }}"})
        make_alias(token, tb, alias_b, "list-m2m", ["m2m"],
                   options={"template": "{{ " + fa + "." + title_a + " }}"})
        # Mark the junction's M2O fields as such so Directus's REST resolver
        # actually traverses them. Without special:[m2o] + interface, deep
        # field queries return null for these columns even when the relation
        # row exists in directus_relations.
        for f, t in ((fa, ta), (fb, tb)):
            req("PATCH", f"/fields/{jc}/{f}", token=token, body={"meta": {
                "special": ["m2o"], "interface": "select-dropdown-m2o",
            }})
        make_relation(token, jc, fa, ta,
                      meta={"one_field": alias_a, "junction_field": fb})
        make_relation(token, jc, fb, tb,
                      meta={"one_field": alias_b, "junction_field": fa})

    print("\nDone.")
    print("Skipped: translations relations (9). Configure via UI after deciding")
    print("how the legacy `sprachen` collection maps to a D11 language collection.")


if __name__ == "__main__":
    main()
