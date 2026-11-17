# Waldseite D11 — Frontend Refactor Roadmap

<!-- overnight
branch: frontend-refactor-run1
name: waldseite-frontend
prefix: frontend
model: opus
test: nix run .#test
allow: Bash(nix run .#test*)
allow: Bash(nix develop *)
allow: Bash(npx shadow-cljs *)
allow: Bash(node *)
deny: Bash(nix run .#deploy*)
deny: Bash(nix run .#pull-db*)
deny: Bash(nix run .#push-db*)
deny: Bash(nix run .#dev*)
-->

Machine-runnable roadmap for `overnight start <repo>`: one fresh Claude Code
background session per unchecked step. The trailing `@haiku`/`@sonnet`/`@opus`/
`@fable` tag on each item selects the model and is not part of the step.
Source: interactive review + planning session with the owner, 2026-11-17
(preliminary code read of `src/browser`, `src/common`, the server handlers they
talk to, and the Sounds of Ukraine sibling repo `../../festival/festival-macchiato`).

## Goal

The browser code (booking SPA `buchung.*`, price admin `preise.*`) is a
first-project Reagent app: global `r/atom`s, cursors threaded through
components, state mutated during render, `js/alert`, no tests. Rebuild both
on a clean re-frame architecture, move the domain logic (price calculation,
availability/date rules, the preise.edn shape) into `src/common` so the
**server validates and recomputes** booking requests with the same code, switch
the browser↔server transport to the Sounds of Ukraine EDN pattern, harden the
no-JS contact form against spam, and remove every Sounds of Ukraine leftover.

**Prime directive: behaviour parity.** Prices, minimum stays, blocked days and
every other user-visible rule must stay exactly as they are, proven by
golden-master / property tests against a frozen copy of the old code. The single
sanctioned behaviour change is the DST fix (see Decisions).

## Decisions that stand (made by the owner, do not revisit)

1. **DST bug gets fixed.** The old picker produces local `goog.date.Date`s; a
   stay spanning the spring DST switch (last Sunday of March, Europe/Berlin) is
   167 h long and cljs-time `in-days` floors it to N-1 nights → wrong weekly
   tariff, surcharges and min-stay check. New shared code counts **calendar
   days** (UTC-midnight arithmetic), independent of process TZ. Parity tests:
   new == legacy exactly when legacy runs on UTC dates; against legacy on
   Berlin-local dates, differences are allowed **only** for stays spanning the
   spring switch, and there new == legacy-on-UTC.
2. **Server check: reject, server price wins.** Malformed payload, blocked dates
   or too-short stay → HTTP 400 with an EDN error the UI displays. The price in
   the mail is always the server-computed one; a differing client price is only
   logged. If the iCal feeds cannot be fetched during validation, the request
   is accepted and the mail carries a warning line.
3. **Testing scope:** golden-master + property tests for the domain logic and
   re-frame event/sub tests, all in Node. No Playwright, no DB, no Directus,
   no secrets on the box. The owner does a manual click-through in the morning
   (checklist produced by the last steps).
4. **Contact form stays JavaScript-free.** Anti-spam: server-signed timestamp
   token, honeypot, per-IP rate limit, link heuristic.
5. **Transport = Sounds of Ukraine pattern:** browser POSTs an
   `application/edn` body with the CSRF token in the `X-CSRF-Token` header;
   `psite-middleware.core/wrap-edn-params` (already on the `/api/` route tree,
   `src/server/api/routes.cljc`) puts it on `:edn-params`; responses are EDN.
   Server→browser page hand-off stays transit via `:js-data` /
   `app.core/readarg` (already in place). Reference implementation:
   `../../festival/festival-macchiato/src/browser/app/book.cljs` and
   `.../src/server/api/book.cljs` (read-only; the sibling repo may be absent on
   the box — the pattern is fully described here).
6. **preise.edn on-disk format stays as is** (string-valued numbers like
   `"78"`, `"115.00"`, `""`; ISO date strings; `:felder` keyed by integer
   wohnung id). Normalisation to numbers happens in memory only.

## Rules for every step

- **Test-driven:** `nix run .#test` (created in step 1; it compiles the
  `server`, `browser`, `admin` and `test` shadow builds and runs the Node test
  suite under both `TZ=Europe/Berlin` and `TZ=UTC`). A step is done only when it
  is green. Until step 1 is done, the check is
  `nix develop -c npx shadow-cljs compile server browser admin`.
- After each step: `git add` the files you touched, tick the step's checkbox
  to `- [x]`, and commit everything with a descriptive message prefixed
  `frontend:`. **Never push.**
- Steps are strictly ordered; never start a later step early, never do more
  than one step per session.
- If a step cannot be completed after honest effort, rewrite its item as
  `- [ ] BLOCKED: <short reason> — <original title>`, commit, and stop.
- `- [ ] WAIT: <shell command>` items are not steps for you: the runner
  executes the command itself (blocking) and ticks the item when it
  succeeds. Never tick, edit or skip one.
- The box has **no database, no Directus, no `../settings.edn`, no
  `../preise.edn`**. Never run `nix run .#dev`, deploy, or DB scripts. Use the
  fixtures under `test/fixtures/`.
- Do not add new **git** deps (they need SSH agent forwarding the box lacks
  during the run). Maven deps are fine (fetched over HTTPS). Avoid new npm
  packages; if one is truly needed, add it to `package.json` and run
  `npm install` inside `nix develop`.
- **The frozen legacy oracle** (`test/legacy/**`, created in step 4) is never
  edited after step 4, except to keep it compiling. It is the definition of
  "old behaviour".
- Gotcha: shadow-cljs does not track `schema/snapshot.json`; if a build emits
  "Use of undeclared Var s/…", `rm -rf .shadow-cljs/builds/server` and rebuild.
- Match the existing idioms (`CLAUDE.md`; `def-go-handler`/`defhandler`,
  `comp.snippets/defsnips` for every user-visible string in de/en/nl). Keep
  `CLAUDE.md` accurate when a step changes what it describes.
- Line numbers below are as of commit `4510d4c`; verify before editing.

## Target architecture (all re-frame steps follow this)

```
src/common/
  specs/common.cljs        email-regex, iso-date regex, shared malli bits
  specs/anfrage.cljs       booking request payload schema (exists; extended)
  specs/kontakt.cljs       contact form schema (new)
  kalender/core.cljs       ISO "yyyy-MM-dd" <-> UTC-midnight cljs-time, nights,
                           days-in-month grid, today-iso (local calendar date)
  preise/model.cljs        malli schema of preise.edn (string-valued),
                           normalize (numparse + parsed seasons), season helpers
  preise/calculations.cljs weighted average (moved from browser)
  buchung/preis.cljs       berechnen — the price calculation
  buchung/verfuegbarkeit.cljs  blocked days, handovers, Sunday rule, <47h
                           filter, date errors as data ({:error :belegt} …)
src/browser/buchung/       db.cljs events.cljs subs.cljs fx.cljs
                           views/*.cljs core.cljs (mount only)
src/browser/preise/        db.cljs events.cljs subs.cljs views.cljs core.cljs
```

- app-db holds **plain data only**: dates as ISO strings, prices as on disk
  (strings) plus normalized derived data computed in subs, seasons addressed by
  `:id`, UI state (shown month per picker, selection, post state) under `:ui`.
- Views never `reset!`/`swap!` anything; they deref subs and dispatch events.
  Local component state only for purely visual things (e.g. trash confirm).
- Side effects only in `reg-fx` / `reg-event-fx` (http via
  `day8.re-frame/http-fx` + `ajax.edn/edn-request-format` /
  `edn-response-format`, already on the classpath via cljs-ajax 0.8.x).
- Locale lives in app-db; snippets are called with it, no global locale atom.
- Domain rules return **data** (keywords + params); only views localize.

## Phase A — Foundations

- [x] 1. Node test infrastructure and new `nix run .#test` @sonnet
  - Why: there are no runnable tests. `nix run .#test` (`apps.nix:119-124`)
    runs a JVM cuic browser suite in `test/test/*.clj` that tests Sounds of
    Ukraine concert booking and needs MariaDB + Chromium — dead.
  - Add a shadow-cljs build `:test` in `shadow-cljs.edn`:
    `{:target :node-test :output-to "target/test/node-tests.js" :ns-regexp "-test$" :autorun false :compiler-options {:reader-features #{:node}}}`.
    Test sources live under `test/cljs/` (add `"test/cljs"` and
    `"test/fixtures"`-reading helpers as needed; add the path via a deps.edn
    alias included in `shadow-cljs.edn` `:deps {:aliases [...]}`).
    Add `org.clojure/test.check` (Maven) for property tests.
  - Rewrite the `test` app in `apps.nix`: compile `server browser admin test`
    (`npx shadow-cljs compile …`, fail on warnings is not required), then run
    `TZ=Europe/Berlin node target/test/node-tests.js` and
    `TZ=UTC node target/test/node-tests.js`; non-zero exit on any failure.
    Check that the node-test runner propagates failures as exit code (it
    does via `process.exitCode` in shadow's node-test; verify).
  - Delete the dead JVM suite: `test/test/*.clj` and the `:test` alias in
    `deps.edn` (cuic, next.jdbc, mysql connector, clj-http, test-runner).
    Add `target/` to `.gitignore` if not ignored.
  - Gotcha: the `server` build expands `psite-utils.macros/cache-bust`, which
    slurps `public/compiled/bundle/main.css` at compile time; a fresh clone
    lacks it (it comes from `nix run .#build`). In the test app, run the
    styles/webpack part of the build first when that file is missing (see the
    `build` app in `apps.nix`), so `nix run .#test` works on a fresh clone.
    (On the box the file already exists from the pre-flight build.)
  - One trivial smoke test (`test/cljs/smoke_test.cljs`) that requires
    `specs.anfrage` and validates a sample payload.
  - Update `CLAUDE.md` "Development Commands"/testing text.
  - Acceptance: `nix run .#test` passes from a clean shell on a fresh clone
    (after `nix run .#setup`), runs the smoke test under both TZs, and fails if
    the smoke test is made to fail (try it, then revert).

- [x] 2. Purge Sounds of Ukraine code from the browser bundle @sonnet
  - Delete `src/browser/app/book.cljs` (concert seat reservation),
    `src/browser/app/admin.cljs` (concert reservations/reminder mails),
    `src/common/specs/book.cljs` (move `email-regex` to a new
    `src/common/specs/common.cljs`; `specs.anfrage` currently refers it from
    `specs.book`), and the entries `app.book`, `app.admin` in
    `shadow-cljs.edn` `:browser` `:entries`.
  - `src/browser/app/modal.cljs`: drop `notes` (music-note spinner) and the
    unused re-frame `::close`/`::display`/`::content` registrations if nothing
    uses them; keep `open-image`/`init` if referenced (grep server templates).
  - `src/browser/app/reframe.cljs` registers catch-all `:get-in`, `:set`,
    `:set-db`. `app.router/reverse-match` reads `[:get-in [:locale]]`. Remove
    the catch-alls; if `app.router` is still needed by surviving code, give it
    an explicit locale argument instead; otherwise delete it (check whether
    `app.router` is used anywhere after the purge — `buchung.*` does not use it).
  - Check `app.youtube`, `app.translate`, `app.obfuscate` — keep those used by
    Waldseite pages (`app.translate` is the /admin/uebersetzungen dashboard;
    grep `src/server` for each `:onload`/export name).
  - Remove snippets in `src/common/comp/snippets.cljs` used **only** by
    removed code (grep every candidate across `src/`; e.g. `extra-concert`,
    `reserve-seats`, `reservierung-erfolgreich`, `spamfilter`,
    `anzahl-personen` …). Never remove a snippet still referenced.
  - Acceptance: no reference to concerts/reservations/attendees remains under
    `src/browser` or `src/common`; all builds compile without new warnings;
    `nix run .#test` green.

- [x] 3. Purge Sounds of Ukraine code from server, assets and build @sonnet
  - `src/server/seiten/components/common.cljs`: `musician-card`,
    `musicians-c`, `concert-card`, `concerts-c` — delete if unreferenced
    (grep), keep anything Waldseite uses.
  - Stub handlers via `serving.stub`: `api.qr` (`/api/qr`),
    `api.wohnungen-voll` (`/api/wohnungen-und-preise`), `seiten.test-page`.
    Remove routes + namespaces unless something links to them (grep for route
    names `:qr`, `:wohnungen-voll`, the test page's route name, in
    navigation/sitemap/templates). Remove `serving.stub` if then unused.
  - `src/server/serving/core.cljs:41-43`: rate-limit buckets `:book`, `:ical`,
    `:qr` are festival buckets. Replace with `:anfrage`
    `{:capacity 5 :refill-per-sec 0.002}` and `:kontakt`
    `{:capacity 5 :refill-per-sec 0.002}` (wired to routes in steps 9 and 15;
    here just define them, drop the unused ones).
  - `src/server/db/setup.cljs:44` docstring example mentions `:concerts`/`:uk`
    → rewrite with a Waldseite collection.
  - `apps.nix`: rename `festival-*` app/script names to `waldseite-*`
    (names only, no behaviour change). Same for any `festival` naming in
    `flake.nix` that is purely a label (do NOT rename things the prod server
    flake depends on — e.g. a systemd unit name referenced by deploy scripts;
    grep `scripts/` and leave such names, noting them in CLAUDE.md).
  - Assets: `reservoir/imgs/` festival logos (`Bechstein_Logo.svg`,
    `logo-phil-*.svg`, `logo_gvl.svg`, `logo_klavins*.svg`, `nrw.svg`,
    `konsulat.*`, `zamus_logo.*`, `klarinette.svg`, `klavier.svg`,
    `violine.svg`) and `resources/email-templates/confirmation.html` — delete
    only those not referenced from `src/` (grep file names, incl. Garden styles
    under `src/common/style`) and from `resources/`.
  - `src/common/testdata.cljs` and `src/common/test/data.cljs` (identical
    files, `caldata` iCal sample): move one copy to
    `test/fixtures/caldata.ics` (or a test ns) if it is a usable iCal sample
    for step 4, delete both source copies.
  - Acceptance: `grep -riE 'concert|konzert|musician|festival|ukrain|attendee'`
    over `src/ apps.nix flake.nix shadow-cljs.edn settings.edn` only hits
    intentionally kept names (documented in the commit message); `nix run
    .#test` green; `CLAUDE.md` mentions nothing removed.

## Phase B — Freeze the old behaviour

- [x] 4. Frozen legacy oracle + characterization tests + quirks list @opus
  - Why: every later step proves parity against this. Do it before any logic
    moves.
  - Copy **verbatim** (only change: `ns` name to `legacy.*`, `defn-` →
    `defn`, and requires re-pointed to the legacy copies) into
    `test/cljs/legacy/`:
    `src/browser/buchung/preisberechnung.cljs`, `datechecking.cljs`,
    `utils.cljs`, the pure parts of `datepicker.cljs` (`handovers`,
    `sunday-and-anabreise?`, `blocked?`), the data reworking in
    `buchung/ajaxing.cljs` (`timify-saisonen`, `numparse-recursively`,
    `numeric-fields`, `rework-preise`, `rework-wohnungen`),
    `src/browser/preise/calculations.cljs`, `preise/util.cljs`, and the pure
    functions of `preise/components.cljs` (`interval-sn`, `seasons-overlap?`,
    `saison-overlaps?`, `next-saison`, `shift-saison-by-years`,
    `shift-saisonen-by-one-year`, `unselect-all-saisonen`). `legacy.utils`
    keeps its global `locale` atom — tests set it.
  - Fixtures: copy the owner's real price file into
    `test/fixtures/preise.edn` — it is **not** on the box; it is committed
    together with this roadmap at `test/fixtures/preise.edn` (if it is
    missing, mark this step BLOCKED). Build a synthetic belegung fixture
    `test/fixtures/belegung.edn` (vector of `{:dtstart "…" :dtend "…"}` ISO
    strings per wohnung id) covering: adjacent bookings (handover day),
    bookings starting/ending on Sundays, single-night bookings (<47 h, which
    `rework-wohnungen` filters out), bookings across season boundaries and
    across the spring and autumn DST switches.
  - Characterization tests (`test/cljs/legacy_characterization_test.cljs`)
    pinning legacy outputs on hand-picked cases — each documents a rule:
    weekly tariff from 7 nights (`preis_woche/7`), day tariff below; season
    value ≤ 10 or missing → `grundpreis`; **empty `preis_woche` `""`** →
    `"" / 7` = 0 → falls back to `grundpreis`, not `preis_tag` (wohnung 4 in
    season id 39 of the fixture); season lookup inclusive of `:ende`
    (`within-left?`) and first match wins; extra-person surcharge above
    `standardbelegung`; pets; cleaning fee when nights < `reinigung-unter`
    (22); `gaestebeitrag` and `energieaufschlag` per night per guest with their
    rounding; min stay = min over overlapping seasons' `mindestaufenthalt`
    else `mindestaufenthalt_standard`; blocked-day rules incl. the Sunday
    rule and handovers; error precedence (order before blocked before
    min-stay). Plus a test that **demonstrates the DST bug** under
    `TZ=Europe/Berlin` with `goog.date.Date` inputs built the way the picker
    builds them (`t/today`-style local dates + `t/plus-` days) — guard it so it
    is skipped when the process TZ has no DST (UTC run).
  - Write `doc/frontend-refactor-quirks.md`: every surprising legacy rule
    found (the empty-`preis_woche` fallback, the >10 threshold, `NaN`
    weighted average when no data, `:selected` persisted to disk, …) with the
    decision "preserved" — except DST: "fixed (owner decision 2026-11-17)".
  - Acceptance: legacy namespaces compile in the test build unmodified in
    logic; characterization tests green under both TZs (DST test runs in
    Berlin, skipped in UTC); quirks doc committed.

## Phase C — Shared domain in `src/common`

- [x] 5. Shared calendar helpers and preise.edn model + schema @opus
  - `src/common/kalender/core.cljs`: `iso->date` (UTC midnight via
    `cljs-time.coerce/from-string`), `date->iso`, `nights` (calendar-day
    difference, exact — must never floor across DST), `days-between`
    (`[start, end)` as ISO or dates), `days-in-month`, `month-grid` for the
    picker (6×7 incl. outside days, Monday-first, matching legacy
    `format-month`), `today-iso` (the **local** calendar date of the runtime,
    from `js/Date` getters), `weekday` (1=Mon … 7=Sun).
  - `src/common/preise/model.cljs`: malli schema for the on-disk preise.edn
    (`:saisonen` vector of `{:id int :beginn iso :ende iso :felder {int {:preis_tag s :preis_woche s :mindestaufenthalt s}} (:selected bool optional)}`,
    `:allgdaten`, `:basisdaten {int {…}}`; number-strings may be `""`,
    `"78"`, `"115.00"`; also accept numbers, since a saved file may contain
    them — check the fixture and the old admin's `on-change` which stores raw
    input strings). Must validate `test/fixtures/preise.edn`. `normalize`
    = legacy `rework-preise` semantics (same numeric fields, same parseFloat
    behaviour leaving `""` and non-numbers untouched) but seasons get
    UTC-midnight dates. Season-id helpers (`next-id`).
  - Tests: schema accepts fixture, rejects obvious garbage; `normalize`
    equals legacy `rework-preise` on the fixture modulo date representation;
    kalender functions incl. DST-spanning ranges in both TZs.
  - Acceptance: tests green under both TZs.

- [x] 6. Shared price calculation with property-based parity @opus
  - `src/common/buchung/preis.cljs` `berechnen` taking
    `{:anreise iso :abreise iso :gaestezahl :haustierzahl :wohnungsid}` and
    normalized preise; returns the **same map shape and values** as legacy
    `berechnen` (`:tageszahl :gaestezahl :einzeltage-summe
    :belegungsaufschlag :haustieraufschlag :reinigungsaufschlag
    :gaestebeitrag :energieaufschlag :gesamtsumme`, same rounding, same
    float quirks — e.g. `(float …)` and `Math/round`). Nil when dates missing
    or not ordered. Pure, no locale, no cljs-time types in the signature.
  - Parity tests with test.check, ≥ 20 000 cases per TZ run: generators for
    wohnung id (1–8 from fixture), anreise across the fixture's season range
    (2025-01 … 2027-12 plus some outside), nights 1–35 with extra weight on
    6/7/8, 21/22/23 and season-boundary crossings, gaestezahl 1–max,
    haustierzahl 0–3. Oracle A: legacy `berechnen` fed UTC dates
    (`legacy.utils/parse-iso`) → must be `=` exactly. Oracle B (only when TZ
    has DST): legacy fed Berlin-local `goog.date.Date`s → `=` except when the
    stay spans the spring switch; for those assert new == oracle A. Also
    explicit regression cases from step 4. (Step-4 finding: oracle B also
    differs for stays arriving on a season's first day — see "Time zone" in
    `doc/frontend-refactor-quirks.md`; treat those like DST stays.)
  - Acceptance: tests green both TZs; no change to the old UI yet.

- [x] 7. Shared availability and date rules with parity @opus
  - `src/common/buchung/verfuegbarkeit.cljs`: `prepare-belegung` (legacy
    `rework-wohnungen` filter: drop vevents ≤ 47 h, invalid dates),
    `handover-days`, `day-blocked?` (Sunday rule + inside a booking; departure
    days free), `day-selectable?` (not past relative to a given `today-iso`,
    not blocked), and `stay-error` returning `nil` or data:
    `{:error :anreise-nach-abreise}`, `{:error :belegt}`,
    `{:error :mindestaufenthalt :min n :wohnung name}` — same precedence and
    same rules as legacy `err-dates-bad?` (min stay with `js/parseInt`, min over
    overlapping seasons, else standard), but nights counted by calendar days.
  - Parity tests (test.check + belegung fixture): `day-blocked?` vs legacy
    `blocked?` over every day 2025–2027 for every fixture wohnung; `stay-error`
    vs legacy `err-dates-bad?` mapped to codes (map legacy localized strings
    back to codes by comparing against the snippets with `legacy.utils/locale`
    set to `:de`); same DST allowance as step 6, plus (step-4 finding)
    stays arriving on a season's `ende` day for the min-stay rule — see
    "Time zone" in `doc/frontend-refactor-quirks.md`.
  - Acceptance: tests green both TZs.

## Phase D — Transport and server-side validation

- [ ] 8. Booking request: EDN transport + server validation + rate limit @opus
  - Server `src/server/api/anfrage.cljs`: read `(:edn-params req)` (drop the
    `:anfragedaten` form-param path). Validate with `specs.anfrage/payload`
    (tighten: `:preis` now optional and ignored; dates must parse; wohnungsid
    must exist in the price cache). Then semantic checks with the shared
    domain: recompute price via `buchung.preis/berechnen` on
    `(preise.model/normalize (preise.cache/read-prices))`; fetch belegung for
    that wohnung via the same path as `api.buchungsproxy`
    (`db.queries/wohnungen-with-ical` + `buchung.fetch/fetch-ical-feeds`) and
    run `stay-error`; guests ≤ `maximalbelegung`. Errors → 400 EDN
    `{:error :invalid :fields [...]}` (humanized malli paths), `{:error
    :belegt}`, `{:error :mindestaufenthalt :min n}`, `{:error
    :ueberbelegung}`. iCal fetch failure → accept and add a warning line to
    the mail (`buchung.mailer`). Mail always uses the server price; log a
    `warn` when the client sent a different `:gesamtsumme`.
  - Rate limit: wrap the `:anfrage` route with
    `(psite-rate-limit.core/wrap-rate-limit :anfrage)` (see festival
    `api/routes.cljc` usage; the bucket was defined in step 3). Honeypot:
    optional `:website` key in the payload; non-empty → respond as success
    but do not send (log).
  - Keep the **old** UI working: change `buchung.ajaxing/post-anfrage!` to
    POST `{:edn-params anfrage :headers {"X-CSRF-Token" token}}` with
    cljs-http (token still read from `#ifg`); map 400 error codes to the
    existing failure state for now.
  - Make DB/iCal/mail access injectable for tests (pass functions or
    `with-redefs` on small wrapper fns). Handler tests in Node: valid request
    → mail called with server price; schema violation; blocked; too short;
    over capacity; iCal failure → mail with warning; honeypot; client price
    mismatch → logged, still OK.
  - Acceptance: tests green; `server` + `browser` builds compile;
    `CLAUDE.md` documents the EDN transport pattern in "Key Patterns".

- [ ] 9. Price admin save: EDN transport + schema validation @sonnet
  - `src/server/api/preise.cljs` `save`: accept `(:edn-params req)` →
    `{:preisstruktur <map>}`; validate with `preise.model/schema` (400 with
    `{:error :invalid :explain …}` on failure); additionally reject seasons
    with `beginn` after `ende` and duplicate season ids. Overlapping seasons
    stay allowed (old UI only warns). Persist with
    `persist/write-edn-string! (pr-str m)`; keep the existing response.
  - `src/server/preise/persistence.cljs` stays format-compatible.
  - Old admin UI `src/browser/preise/ajaxing.cljs` `post-preise!`: send
    `:edn-params {:preisstruktur (u/serialize-daten daten)}` + `X-CSRF-Token`
    header (token from `#ifg`); map 400 to `:fehlgeschlagen`.
  - Tests: handler accepts the fixture, rejects malformed/duplicate-id/
    reversed-date structures; persistence round-trip writes EDN that
    `read-string`s back `=` (use a temp path via `with-redefs` on the path fn).
  - Acceptance: tests green; `admin` build compiles.

## Phase E — Booking SPA on re-frame

- [ ] 10. Booking state layer: app-db, events, subs, effects @fable
  - New `src/browser/buchung/{db,events,subs,fx}.cljs` per "Target
    architecture". app-db: `{:locale :status (:loading|:ready|:load-failed)
    :wohnungen [...] :preise <raw> :auswahl {:wohnungsid 1 :gaestezahl 2
    :haustierzahl 0 :anreise nil :abreise nil} :gast {…} :zusatznachricht
    :datenschutz-gelesen? :mietbedingungen-gelesen? :ui {:picker {:anreise
    {:shown-month iso} :abreise {…}} :ueberbelegung? :post {:state :idle|
    :pending|:success|:failed :error <server error data>}
    :submit-attempted? }}`. Defaults must equal the legacy ones
    (`buchung/core.cljs:273-279`: wohnung 1, 2 guests, 0 pets).
  - Events: `::init` (locale), `::fetch-data` / `::data-loaded` /
    `::data-failed` (GET `/api/buchungsproxy`, EDN), `::select-wohnung` (from
    dropdown and from carousel; resets `ueberbelegung?`; **clamps gaestezahl
    to the new wohnung's `maximalbelegung` and sets `ueberbelegung?` when it
    clamped** — this replaces the legacy render-time `reset!` in
    `gaestezahl-select`, `buchung/core.cljs:338-351`; max defaults to 6 when
    unknown as in legacy `maximalbelegung`), `::set-gaestezahl` (clears
    `ueberbelegung?`), `::set-haustierzahl`, `::pick-date` (`:anreise`/
    `:abreise`, ISO), `::shift-month` (per picker, ±1 month/±1 year),
    `::set-field` for guest/contact fields with explicit allowed paths,
    `::submit` (validate with `specs.anfrage`; invalid → `:submit-attempted?
    true`; valid → POST EDN via http-fx with CSRF header, token from `#ifg`
    read in a coeffect), `::submit-ok`, `::submit-failed` (keeps server error
    data).
  - Subs: current wohnung, belegung (prepared via shared
    `prepare-belegung`), `stay-error` (shared), `preis` (shared `berechnen`),
    `anfrage` payload (same keys as legacy `build-anfrage`,
    `buchung/core.cljs:312-325`, incl. rounded `:preis`), `field-errors`
    (malli explain → set of paths, shown only after `submit-attempted?`),
    `can-submit?`, picker cell model per day (classes as legacy
    `day-cell`: past/blocked/contained/free/selected/bad).
  - Tests (Node, `re-frame.core` with `day8.re-frame.test`-style helpers or
    plain `dispatch-sync` + `subscribe` after `rf/clear-subscription-cache!`;
    no DOM): defaults; apartment switch clamps guests + warning; date flow →
    price equals shared `berechnen`; date error codes; anfrage payload shape
    equals legacy `build-anfrage` for the same state (use the legacy copy in
    the oracle if extracted, otherwise reimplement the expectation from the
    legacy source); submit states; server error mapping. Effects stubbed via
    `rf/reg-fx` overrides in tests.
  - Acceptance: tests green; no views changed yet; builds compile.

- [ ] 11. Booking views on re-frame; delete the Reagent version @opus
  - Rewrite `src/browser/buchung/views/*.cljs` and `core.cljs` (`main` keeps
    its exported name and `{:keys [locale]}` arg — `seiten.buchung` calls
    `buchung.core.main` via `:js-data`). Same DOM structure and Bulma classes
    as legacy (`buchung/core.cljs:338-495`, `datepicker.cljs`) so
    `public`/Garden styles keep applying; same snippets and `gstring/format`
    texts.
  - Datepicker: a pure component `(datepicker {:cells … :shown-month …
    :on-pick … :on-shift …})` fed by subs; no atoms passed in.
  - Carousel (`react_slick.cljs`): form-3 wrapper receiving `wohnungen` and
    `selected-id` as props and an `on-select` callback; `slickGoTo` on
    update when the selected id changed; no atoms passed in.
  - New, visible improvements allowed (they do not change rules): loading
    failure message with a retry button (instead of the endless spinner);
    per-field validation messages after a submit attempt (snippets in
    de/en/nl); server error codes shown as localized messages
    (`:belegt`, `:mindestaufenthalt`, `:ueberbelegung`, generic).
  - Add `data-test-id` attributes on interactive elements (future e2e).
  - Delete `buchung/{ajaxing,datechecking,preisberechnung,utils}.cljs` and the
    old view code; nothing outside `test/cljs/legacy` may reference them.
  - Acceptance: `browser` build compiles without warnings in the new
    namespaces; tests green; `grep -rn "r/atom\|r/cursor\|reset!\|swap!"
    src/browser/buchung` shows nothing outside the slick wrapper's ref.

## Phase F — Price admin on re-frame

- [ ] 12. Price admin state layer: app-db, events, subs @opus
  - New `src/browser/preise/{db,events,subs}.cljs`. app-db: `{:status
    :loading|:ready|:load-failed|:not-logged-in, :wohnungen (sorted by id
    like legacy `(into (sorted-map) …)`), :daten <raw preise.edn map, strings
    as typed>, :ui {:selected #{season-ids} :save-state :ungespeichert|
    :ongoing|:erfolgreich|:falsches-passwort|:fehlgeschlagen|:req-failed
    :trash-armed #{ids}}}`. Selection is UI state; on load, ids with
    `:selected true` on disk seed `:selected`; on save `:selected` is written
    back for selected seasons only (keeps disk format stable for readers).
  - Events (all by season `:id`, never by value — legacy `swap-in-seq!`
    matched by value): `::load` (parallel GET refdata + data with `?t=`
    cache-bust as legacy `preise/ajaxing.cljs:15-37`; 401 → redirect effect to
    the `location` header or `/admin/preise`), `::set-allg`, `::set-basis`,
    `::set-saison-feld`, `::set-saison-datum` (reject beginn ≥ ende with an
    inline message instead of `js/alert`, keeping the old value — legacy
    `components.cljs` `datepicker` on-change), `::add-saison` (legacy
    `next-saison`: day after the latest `ende`, two weeks long, id = max+1),
    `::toggle-select`, `::clone-selected-next-year` (+1 year, new ids,
    then unselect all — legacy `special-actions`), `::unselect-all`,
    `::delete-selected`, `::arm-trash`/`::trash-saison`, `::save` (EDN POST
    from step 9; any edit sets `:ungespeichert`).
  - Subs: sorted seasons (by `beginn`), per-season errors (dates bad /
    overlaps — legacy `check-saison`, `seasons-overlap?` incl. touching
    ends), weighted averages per wohnung via shared
    `preise.calculations` (moved from `src/browser/preise/calculations.cljs`
    to `src/common`, legacy `NaN`→ display must match legacy, see quirks doc).
  - Parity tests vs legacy oracle for: next-saison, clone/shift, overlap
    detection, weighted average on the fixture; event tests for every
    action above, especially id-based editing with two identical seasons.
  - Acceptance: tests green; builds compile.

- [ ] 13. Price admin views on re-frame; delete the Reagent version @sonnet
  - Rewrite `src/browser/preise/views.cljs` + `core.cljs` (`main` keeps its
    name; mounted by `seiten.preise` via `/compiled/admin/preise.js`, the
    `:admin` build). Same table layout, CSS classes, labels, units, icons
    (`/imgs/preise/trash.png`) as legacy `preise/components.cljs` and
    `preise/core.cljs`. Inputs remain `type number`/`date`, values stored as
    the typed strings.
  - Inline messages replace `js/alert` (load failure, invalid dates).
  - Delete `preise/{components,ajaxing,util,calculations}.cljs` and the old
    core; nothing outside `test/cljs/legacy` may reference them.
  - Acceptance: `admin` build compiles; tests green; no `r/cursor`/
    `r/atom` (except the purely visual trash confirm, if kept local) in
    `src/browser/preise`.

## Phase G — Contact form (no JavaScript)

- [ ] 14. Contact form: shared schema and anti-spam without JS @opus
  - Today: `src/server/seiten/kontakt.cljs` renders a plain form posting to
    `:api_kontakt` (`/:locale/kontaktform`, `seiten.kontaktform`). Spam
    defences: honeypot `age` and a `time-spent` hidden field **filled by an
    inline script** — without JS it stays empty and `validate` treats every
    no-JS submission as `:too-fast`. Errors render a result page and the
    user's message is lost. The Datenschutz link is `href "#"`. Email check
    is a loose regex unlike `specs`' `email-regex`.
  - New `src/common/specs/kontakt.cljs` (malli: name 1–256, email via
    `specs.common/email-regex`, message 1–5000, datenschutz `true`), used by
    the server.
  - Signed timestamp token: when rendering the form, embed hidden `ts` and
    `sig` = HMAC-SHA256 (node `crypto`) over `ts` with a per-process random
    key (`defonce`, `crypto.randomBytes 32`). On submit: missing/bad
    signature, younger than 4 s or older than 24 h → do **not** send; re-render
    the form with the user's input preserved, a fresh token and a localized
    "please submit again" note (covers server restarts). No new secret in
    settings.
  - Honeypot: rename `age` to a plausible field (e.g. `website`), keep it
    visually hidden with `autocomplete="off"` and `tabindex="-1"`,
    `aria-hidden`. Filled → pretend success, do not send, log.
  - Rate limit `:kontakt` (bucket from step 3) on the POST route; exceeded →
    the limiter's response or a localized page.
  - Link heuristic: more than 2 URLs or any `[url=` / `<a ` in the message →
    treat as spam (pretend success, do not send, log).
  - Validation errors re-render the form with values and inline Bulma
    `is-danger` messages (existing snippets `bitte-name`, `bitte-email`,
    `nachricht-leer`, `datenschutzregelung`), not a separate result page.
    Fix the Datenschutz link to the real page (find the route used elsewhere,
    e.g. `:single-page` with the Datenschutz page id/slug, or what
    `seiten.buchung`/templates footer links to).
  - Remove the inline `<script>`.
  - Tests: token sign/verify (valid, tampered, too fast, expired), honeypot,
    link heuristic, schema, handler re-render keeps values; all via direct
    handler calls with stubbed mail.
  - Acceptance: tests green; `server` build compiles; the page works with JS
    disabled by construction (no script tags in the kontakt page body).

## Phase H — Cleanup, docs, review

- [ ] 15. Dependency and dead-code sweep, docs @sonnet
  - Remove now-unused browser deps/requires: `cljs-http` and
    `cljs.core.async` in browser code if unused (check server too before
    touching `deps.edn`), `reagent-forms` if present, leftover helpers in
    `src/browser/app/*`. Remove `re-frame-10x` only if unused in dev config
    (it is commented out in `shadow-cljs.edn`; leave if harmless).
  - `CLAUDE.md`: new "Frontend architecture" section (target layout above,
    app-db conventions, EDN transport, shared domain in `src/common`, test
    command and the legacy-oracle rule); fix any outdated statements found
    on the way (e.g. MariaDB mentions in Development Commands/process list
    that the migration notes say are gone — verify against `apps.nix` and
    `process-compose.yaml` before changing).
  - `doc/frontend-refactor-checklist.md`: manual morning click-through for
    the owner: booking (each apartment, guest clamp warning, pets, dates
    across season boundary, a 7-night stay spanning end of March, blocked
    days, min-stay error, submit success and each server error), price admin
    (load, edit, add/clone/delete/trash, invalid dates, save, reload shows
    saved data, not-logged-in path), contact form with JS disabled (success,
    too fast, honeypot, validation re-render).
  - Acceptance: tests green; all builds compile without warnings from
    project namespaces.

- [ ] 16. Whole-branch review against this roadmap @opus
  - Review `git diff 4510d4c..HEAD` against every step's acceptance criteria
    and the "Decisions that stand". Look specifically for: behaviour drift not
    covered by a parity test, views that mutate state, domain functions that
    take cljs-time objects across the browser/server boundary, missing
    snippet translations (de/en/nl), server paths that still trust client
    data.
  - Fix small issues directly (with tests). Anything larger: append a
    "Follow-ups" section to `doc/frontend-refactor-quirks.md` instead of
    fixing it.
  - Write `doc/frontend-refactor-summary.md`: what changed per area, the
    behaviour changes (DST fix, server validation, contact form flow, new
    error messages), test counts, follow-ups.
  - Acceptance: tests green; summary committed.

## Deliberately NOT in this roadmap

- No change to the preise.edn on-disk format and no migration of prices into
  Postgres/Directus.
- No Playwright/cuic/e2e tests and no running of the dev stack on the box
  (owner decision: Node-only tests + manual checklist).
- No React/Reagent major upgrade (React 16.13 + `reagent.dom/render` stay);
  separate, later job.
- No JavaScript on the contact form; no CAPTCHA/third-party anti-spam service.
- No "fixing" of legacy price/availability quirks other than the DST bug —
  they are documented in `doc/frontend-refactor-quirks.md` for the owner.
- No new secrets in `settings.edn` (the form-token key is per-process).
- No removal of the frozen legacy oracle under `test/cljs/legacy`.
- No restyling or design changes beyond the explicitly listed messages.
- No changes to Directus schema, DB views or `schema/snapshot.json`.
- No crow-framework rewrite (separate future project).
