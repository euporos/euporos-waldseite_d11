#!/usr/bin/env python3
"""Set sensible display_templates on each collection so related-item
previews in the Directus admin show meaningful titles instead of bare IDs.

For translated collections we reference `{{ translations.<field> }}` —
Directus picks the translation matching the admin user's interface
language and falls back to the first available, so we don't need to
backfill a slug column.

Usage:
  python3 scripts/track_display_templates.py [URL] [EMAIL] [PASSWORD]
"""
import json
import sys
import urllib.request
import urllib.error

URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8055"
EMAIL = sys.argv[2] if len(sys.argv) > 2 else "admin@example.com"
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else "admin"


# (collection, display_template).
# For translated collections, reference fields on the translations alias.
# For non-translated, reference parent columns directly.
TEMPLATES = [
    # Parents with a translations relation:
    ("articles",     "{{ translations.title }}"),
    ("ausfluege",    "{{ translations.titel }}"),
    ("einzelseiten", "{{ translations.titel }}"),
    ("fixe_seiten",  "{{ translations.titel }}"),
    ("news",         "{{ translations.titel }}"),
    ("galerie",      "{{ translations.beschreibung }}"),

    # Parents with their own columns:
    ("gaestebuch",   "{{ name }}"),
    ("haeuser",      "{{ name }}"),
    ("wohnungen",    "{{ name }}"),
    ("languages",    "{{ langname }} ({{ code }})"),

    # Translation rows themselves: language tag + the title-ish field.
    # Useful when D11 lists them flat (rare, but tidy).
    ("articles_translations",     "{{ languages_code }}: {{ title }}"),
    ("ausfluege_translations",    "{{ languages_code }}: {{ titel }}"),
    ("einzelseiten_translations", "{{ languages_code }}: {{ titel }}"),
    ("fixe_seiten_translations",  "{{ languages_code }}: {{ titel }}"),
    ("news_translations",         "{{ languages_code }}: {{ titel }}"),
    ("galerie_translations",      "{{ languages_code }}: {{ beschreibung }}"),
    ("haeuser_translations",      "{{ languages_code }}: {{ haeuser_id.name }}"),
    ("wohnungen_translations",    "{{ languages_code }}: {{ wohnungen_id.name }}"),
    ("startseite_translations",   "{{ languages_code }}"),
    ("allgemeines_translations",  "{{ languages_code }}"),

    # Singletons — display_template doesn't really show, but tidy anyway.
    ("startseite",   "Startseite"),
    ("allgemeines",  "Allgemeines"),
    ("einstellungen","Einstellungen"),
    ("mails",        "Mails"),
]


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


def main():
    headers = {"Content-Type": "application/json"}
    data = json.dumps({"email": EMAIL, "password": PASSWORD}).encode()
    r = urllib.request.Request(URL + "/auth/login", data=data, method="POST", headers=headers)
    with urllib.request.urlopen(r) as resp:
        token = json.loads(resp.read())["data"]["access_token"]
    print(f"Logged in to {URL}")

    for coll, template in TEMPLATES:
        if req("PATCH", f"/collections/{coll}", token,
               body={"meta": {"display_template": template}}) is not None:
            print(f"  {coll:32}  {template}")

    print(f"\n{len(TEMPLATES)} display templates set.")


if __name__ == "__main__":
    main()
