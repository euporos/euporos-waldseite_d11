#!/usr/bin/env python3
"""Wire up the 9 translations relations between waldseite parent collections
and their `*_translations` junctions.

The legacy data already matches D11's standard translations layout:
  - `sprachen` collection has `code` as PK with values {de, en, nl}
  - each `*_translations` table has a `language` column (FK to sprachen.code)
    plus a parent FK column (e.g. `haus_id`)

So all this script does is register that layout via the D11 API:
  1. add an alias `translations` field on each parent (special: translations)
  2. mark the parent FK + language fields on the junction as M2O
  3. create the two relations: junction→parent and junction→sprachen

Usage:
  python3 scripts/track_translations.py [URL] [EMAIL] [PASSWORD]
"""
import json
import sys
import urllib.request
import urllib.error

URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8055"
EMAIL = sys.argv[2] if len(sys.argv) > 2 else "admin@example.com"
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else "admin"

# (junction_collection, parent_fk_field, parent_collection)
# Source: D8 directus_relations rows where field_one == 'translations'.
TRANSLATIONS = [
    ("allgemeines_translations",  "allgemeines_id",  "allgemeines"),
    ("article_translations",      "article",         "articles"),
    ("ausfluege_translations",    "ausflug_id",      "ausfluege"),
    ("einzelseiten_translations", "einzelseite_id",  "einzelseiten"),
    ("fixe_seiten_translations",  "fixed_seite_id",  "fixe_seiten"),
    ("galerie_translations",      "galerie_id",      "galerie"),
    ("haeuser_translations",      "haus_id",         "haeuser"),
    ("news_translations",         "news_id",         "news"),
    ("startseite_translations",   "startseite_id",   "startseite"),
    ("wohnungen_translations",    "wohnung_id",      "wohnungen"),
]

LANGUAGE_COLLECTION = "sprachen"
LANGUAGE_FIELD = "language"


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
        body_txt = e.read().decode()
        if ("already exists" in body_txt
                or "RECORD_NOT_UNIQUE" in body_txt
                or "already has an associated relationship" in body_txt):
            return {"_already": True}
        if "cannot be implemented" in body_txt:
            return {"_fk_type_mismatch": True}
        print(f"  HTTP {e.code} on {method} {path}: {body_txt[:400]}", file=sys.stderr)
        return None


def login():
    return req("POST", "/auth/login", body={"email": EMAIL, "password": PASSWORD})["data"]["access_token"]


def patch_field(token, collection, field, meta):
    res = req("PATCH", f"/fields/{collection}/{field}", token=token, body={"meta": meta})
    return res


def post_field(token, collection, body):
    res = req("POST", f"/fields/{collection}", token=token, body=body)
    return res


def post_relation(token, collection, field, related, meta):
    body = {"collection": collection, "field": field,
            "related_collection": related, "meta": meta}
    res = req("POST", "/relations", token=token, body=body)
    return res


def main():
    token = login()
    print(f"Logged in to {URL}")

    # 1. Make sure the language collection itself is set up so D11 treats it
    #    as a languages collection. Standard D11 expects display_template
    #    based on the code; not strictly required, but helps the UI.
    req("PATCH", f"/collections/{LANGUAGE_COLLECTION}", token=token,
        body={"meta": {"icon": "translate", "display_template": "{{code}} — {{langname}}"}})

    for junction, parent_fk, parent in TRANSLATIONS:
        print(f"\n-- {parent} ↔ {junction} --")

        # 2. Add (or update) alias field `translations` on parent.
        # The `translations` display renders the matching-language value in
        # list views instead of an array of translation row IDs.
        alias_body = {
            "field": "translations",
            "type": "alias",
            "meta": {
                "interface": "translations",
                "special": ["translations"],
                "options": {"languageField": "code"},
                "display": "translations",
                "display_options": {"defaultLanguage": "de",
                                    "languageField": "language",
                                    "userLanguage": True},
            },
        }
        res = post_field(token, parent, alias_body)
        tag = " (exists)" if res and res.get("_already") else " created"
        print(f"  alias {parent}.translations{tag}")

        # 3. Mark parent FK on junction as M2O so the relation interface works
        patch_field(token, junction, parent_fk,
                    {"interface": "select-dropdown-m2o", "special": ["m2o"], "hidden": True})
        print(f"  field {junction}.{parent_fk} marked m2o (hidden)")

        # 4. Mark language field on junction as M2O to sprachen
        patch_field(token, junction, LANGUAGE_FIELD,
                    {"interface": "select-dropdown-m2o", "special": ["m2o"], "hidden": True,
                     "display": "translations",
                     "display_options": {"template": "{{ code }}"}})
        print(f"  field {junction}.language marked m2o (hidden)")

        # 5. Create the two relations
        res = post_relation(token, junction, parent_fk, parent,
                            {"one_field": "translations",
                             "junction_field": LANGUAGE_FIELD,
                             "sort_field": None})
        if res and res.get("_already"):
            print(f"  relation {junction}.{parent_fk} -> {parent} (exists)")
        elif res and res.get("_fk_type_mismatch"):
            print(f"  relation {junction}.{parent_fk} -> {parent} SKIPPED (FK type mismatch)")
        else:
            print(f"  relation {junction}.{parent_fk} -> {parent} created")

        res = post_relation(token, junction, LANGUAGE_FIELD, LANGUAGE_COLLECTION,
                            {"junction_field": parent_fk})
        if res and res.get("_already"):
            print(f"  relation {junction}.language -> sprachen (exists)")
        elif res and res.get("_fk_type_mismatch"):
            print(f"  relation {junction}.language -> sprachen SKIPPED (FK type mismatch)")
        else:
            print(f"  relation {junction}.language -> sprachen created")

    print("\nDone.")


if __name__ == "__main__":
    main()
