#!/usr/bin/env python3
"""Flip waldseite user collections from 'untracked' to managed in Directus 11.

Reads the D8 dump's directus_collections data (icon/hidden/single per collection),
then PATCHes /collections/{name} via the D11 API to set the meta object so the
collections appear in the Content module instead of just as raw tables.

Usage:
  python3 scripts/track_collections.py [DIRECTUS_URL] [EMAIL] [PASSWORD]

Defaults: http://localhost:8055 / admin@example.com / admin

Idempotent: re-running just re-PATCHes the same meta values.
"""
import json
import sys
import urllib.request
import urllib.error

URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8055"
EMAIL = sys.argv[2] if len(sys.argv) > 2 else "admin@example.com"
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else "admin"

# (collection, hidden, singleton, icon) — transcribed from the D8 dump's
# directus_collections INSERT for the waldseite database. Schema is frozen,
# so this list does not need to be regenerated for future dump imports.
COLLECTIONS = [
    ("allgemeines",                 0, 1, "font_download"),
    ("allgemeines_translations",    1, 0, None),
    ("articles",                    1, 0, None),
    ("article_translations",        1, 0, None),
    ("ausfluege",                   0, 0, "flight_takeoff"),
    ("ausfluege_haeuser",           1, 0, None),
    ("ausfluege_translations",      1, 0, None),
    ("einstellungen",               0, 1, "brightness_low"),
    ("einzelseiten",                0, 0, "description"),
    ("einzelseiten_translations",   1, 0, None),
    ("fixe_seiten",                 0, 0, "text_fields"),
    ("fixe_seiten_translations",    1, 0, None),
    ("gaestebuch",                  0, 0, "local_library"),
    ("galerie",                     0, 0, "image"),
    ("galerie_translations",        1, 0, None),
    ("haeuser",                     0, 0, "home"),
    ("haeuser_directus_files",      1, 0, None),
    ("haeuser_translations",        1, 0, None),
    ("mails",                       0, 1, None),
    ("news",                        0, 0, "chrome_reader_mode"),
    ("news_haeuser",                1, 0, None),
    ("news_translations",           1, 0, None),
    ("sprachen",                    0, 0, "translate"),
    ("startseite",                  0, 1, "stay_primary_portrait"),
    ("startseite_translations",     1, 0, None),
    ("wohnungen",                   0, 0, "event_seat"),
    ("wohnungen_directus_files",    1, 0, None),
    ("wohnungen_translations",      1, 0, None),
]


def request(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(URL + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        print(f"  HTTP {e.code} on {method} {path}: {e.read().decode()[:300]}", file=sys.stderr)
        raise


def login():
    r = request("POST", "/auth/login", body={"email": EMAIL, "password": PASSWORD})
    return r["data"]["access_token"]


def main():
    token = login()
    print(f"Logged in to {URL}")
    for name, hidden, single, icon in COLLECTIONS:
        meta = {
            "collection": name,
            "hidden": bool(hidden),
            "singleton": bool(single),
            "icon": icon,
        }
        # PATCH the collection to set its meta. Untracked collections have
        # meta: null; once meta is non-null they become managed and appear
        # in the Content module navigation.
        try:
            request("PATCH", f"/collections/{name}", token=token, body={"meta": meta})
            flags = []
            if hidden: flags.append("hidden")
            if single: flags.append("singleton")
            flag_str = f" [{','.join(flags)}]" if flags else ""
            icon_str = f" ({icon})" if icon else ""
            print(f"  tracked: {name}{icon_str}{flag_str}")
        except urllib.error.HTTPError:
            print(f"  FAILED: {name}", file=sys.stderr)
    print(f"Done — {len(COLLECTIONS)} collections processed.")


if __name__ == "__main__":
    main()
