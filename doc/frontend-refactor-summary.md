# Frontend refactor — summary

Overnight run `waldseite-frontend`, branch `frontend-refactor-run1`,
commits `b31492f` … HEAD on top of `4510d4c` (roadmap:
`roadmap-frontend-refactor.md`). Manual morning click-through:
`doc/frontend-refactor-checklist.md`. Legacy quirks and their decisions,
plus the review follow-ups: `doc/frontend-refactor-quirks.md`.

## What changed, per area

**Tests and build.** New shadow `:test` (node-test) build under
`test/cljs`, test.check on the `:cljs-test` alias. `nix run .#test` compiles
`server`, `browser`, `admin` and `test` and runs the Node suite twice, under
`TZ=Europe/Berlin` and `TZ=UTC`. The dead JVM cuic suite is gone.

**Sounds of Ukraine purge.** Removed from the browser bundle (`app.book`,
`app.admin`, `app.reframe`, `app.router`, `specs.book`, unused snippets) and
from the server (`api.qr`, `api.wohnungen-voll`, `serving.stub`,
`seiten.components.common`, test page, `common/testdata`, festival assets).
Deliberately kept: `festival-directus.service` and the `festival_directus` DB
name (see `CLAUDE.md`).

**Frozen legacy oracle.** The old Reagent booking/price code lives verbatim
under `test/cljs/legacy/**` (`legacy.*`) and is the definition of "old
behaviour". `legacy_characterization_test` pins every quirk listed in the
quirks doc.

**Shared domain in `src/common`** (used by browser *and* server):
- `kalender.core` — ISO `yyyy-MM-dd` ↔ UTC-midnight dates, exact night counts,
  picker month grid, `today-iso`.
- `preise.model` — malli schema of the on-disk `preise.edn` (unchanged
  string-valued format) + `normalize`; `preise.calculations` — weighted
  averages of the admin.
- `buchung.preis/berechnen` — the price calculation.
- `buchung.verfuegbarkeit` — iCal preparation (47 h filter), handover days,
  blocked/selectable picker days, `stay-error` returning data.
- `specs.anfrage`, `specs.kontakt`, `specs.common` — shared malli specs.

**Booking SPA (`buchung.*`)** rebuilt on re-frame: `db`, `events`, `subs`,
`fx`, pure views (`views.formular`, props-only `views.datepicker`, all
strings in `views.texte`), controlled carousel. app-db is plain data (ISO
dates, raw prices, UI state under `:ui`); no view mutates state; `js/alert`
is gone. The guest-count clamp on apartment change is an event, not a
render-time `reset!`.

**Booking request transport + server check.** The SPA POSTs `application/edn`
with `X-CSRF-Token` to `/api/anfrage`; responses are EDN. The server
(`buchung.anfrage-pruefung`) validates the payload, recomputes the price and
checks capacity, occupancy (live iCal) and minimum stay with the shared code.
Rate-limited per IP (bucket `:anfrage`), honeypot `:website`.

**Price admin (`preise.*`)** rebuilt on re-frame with the same table layout.
Seasons are addressed by `:id`, the selection is UI state. Saving POSTs EDN
`{:preisstruktur …}`; the server (`preise.speichern`) validates against the
schema and rejects reversed seasons and duplicate ids before writing the
unchanged on-disk format.

**Contact form** stays JavaScript-free (no script tags on the page). New
`kontakt.pruefung`: HMAC-signed timestamp token (per-process key), honeypot,
link heuristic, shared schema, per-IP rate limit `:kontakt` with a localized
429 page. Validation errors re-render the form with the input kept and
inline Bulma messages. The Datenschutz link points at the real einzelseite.

**Docs.** `CLAUDE.md` has a "Frontend architecture" section and documents the
EDN transport, the config-free-namespace rule for tests and the legacy-oracle
rule.

## Behaviour changes (everything else is parity-tested)

1. **DST fix (owner decision).** Nights are counted as calendar days. A stay
   across the spring switch (last Sunday of March) is no longer one night
   short, so the weekly tariff, surcharges, levies and the minimum-stay check
   are right there. As a consequence two Berlin-local time-zone artefacts
   vanish too (arrival on a season's first day priced at `grundpreis`;
   arrival on a season's `ende` day pulling in that season's minimum stay) —
   *owner to confirm*, see quirks doc.
2. **Arrival on a booking's last night** is rejected by the stay check
   (legacy let it through on UTC dates; guests never saw that because the
   picker blocks the day) — *owner to confirm*.
3. **Server-side validation of booking requests.** Malformed payload,
   unknown apartment, departure not after arrival, too many guests, booked
   nights or too-short stay → HTTP 400 with an EDN error that the SPA shows as
   a localized message. The mail always quotes the **server-computed price**
   (a differing client price is only logged) and the **apartment name from the
   DB** (review fix). If the iCal feeds cannot be fetched, the request is
   accepted and the mail starts with a "⚠ Belegung … nicht vollständig
   geprüft" line.
4. **Contact form flow.** Too-fast / expired / tampered token → form comes
   back with input kept and a "please submit again" note; honeypot or more
   than 2 links → looks like success, nothing is sent; validation errors are
   shown inline on the form instead of a separate result page; too many posts
   → localized 429 page.
5. **New user-visible messages** (de/en/nl): per-field booking form errors,
   "please check the marked fields", server rejection messages, "booking data
   could not be loaded", contact form "submit again", "message too long",
   "too many requests". The price admin shows inline save/validation messages
   instead of `js/alert`.
6. **Price admin:** `:selected false` flags are no longer written to
   `preise.edn` (only `:selected true`); every edit addresses one season by
   id (legacy edited value-identical seasons together); new seasons carry
   `:felder {}`.

## Tests

`nix run .#test`: **130 tests, ~12 990 assertions, 0 failures** in each of the two runs (TZ=Europe/Berlin: 12 994 assertions, TZ=UTC: 12 986; the property tests seed from the clock, so assertion counts vary slightly). All four shadow builds (`server`, `browser`, `admin`, `test`) compile.

Breakdown: golden-master and property tests (test.check) of the shared
price, calendar and availability code against the frozen oracle — under both
Berlin-local and UTC legacy inputs, with the DST/TZ exemptions above;
characterization tests of every legacy quirk; re-frame event/sub tests of both
SPAs; pure view-text tests; server handler tests with stubs for the booking
request, the price save and the contact form.

## Review (step 16) — fixed directly

- The booking mail and analytics used the client-supplied `:wohnungsname`;
  they now use the DB name of the apartment (client value only if the DB
  lookup found nothing). Tests `wohnungsname-vom-server`,
  `wohnungsname-fallback-ohne-db-treffer`.
- Checklist: the honeypot item said "rejected"; it is answered like a success
  without sending.

No view mutates app-db (only `app.modal`'s own content atom and the carousel's
React ref, both purely visual); no shared domain function takes cljs-time
objects across the browser/server boundary (dates are ISO strings, prices
raw on-disk maps); every snippet has de/en/nl.

## Follow-ups

See "Follow-ups" in `doc/frontend-refactor-quirks.md`: past arrival dates and
the picker-only Sunday/handover rule are not re-checked on the server; the
capacity fallback of 6 guests is client-only; 429 on booking shows the
generic error; the price admin stays German-only. Plus the three *owner to
confirm* decisions above.
