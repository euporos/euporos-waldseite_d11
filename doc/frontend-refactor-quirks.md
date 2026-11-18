# Frontend refactor — legacy quirks

Surprising rules of the legacy booking SPA (`buchung.*`) and price admin
(`preise.*`), found while freezing the legacy oracle (`test/cljs/legacy/**`,
roadmap step 4). Each one is pinned by a test in
`test/cljs/legacy_characterization_test.cljs`, named in brackets below.

**Decision** for each quirk: *preserved* means the new shared code
reproduces it exactly. *Fixed* means it deliberately does not.

## Price calculation (`buchung.preisberechnung/berechnen`)

| Quirk | Decision |
|---|---|
| Weekly tariff from 7 nights: every night costs `preis_woche / 7`. Below 7 nights, `preis_tag` applies. The switch uses the night count `t/in-days` of the whole stay, not a per-week split. [`weekly-tariff-from-seven-nights`] | preserved |
| A season value applies only if it is `> 10`. If it is ≤ 10, missing, or the wohnung has no field in that season, the night costs `grundpreis`. For the weekly tariff the threshold applies to the per-night value, so `preis_woche` ≤ 70 also falls back. [`season-value-ten-or-less-or-missing-falls-back-to-grundpreis`] | preserved |
| **Empty `preis_woche` `""`** stays `""` after `numparse-recursively` (which only parses non-empty strings). `("" / 7)` is `0` in JS, `0 > 10` is false, so the night costs **`grundpreis`, not `preis_tag`**. Fixture: wohnung 4 in season 39 (also wohnung 5 in season 54). An empty `preis_tag` behaves the same way. [`empty-preis-woche-falls-back-to-grundpreis-not-preis-tag`] | preserved |
| Season lookup per night: `beginn` and `ende` are both inclusive (`within-left?`). If two seasons share a day, the **first season in vector order** wins. A day in a gap between seasons costs `grundpreis` (fixture gap 2027-11-07 … 2027-12-22). [`season-lookup-inclusive-of-ende-first-match-wins`] | preserved |
| Extra-person surcharge: `nights × max(0, gäste − standardbelegung) × aufschlag_zus_person`. Pets: `nights × tiere × aufschlag_haustier`. [`surcharges-guests-pets`] | preserved |
| Cleaning fee `reinigung` only when nights `< reinigung-unter` (22). At exactly 22 nights there is no fee. [`cleaning-fee-below-reinigung-unter`] | preserved |
| `gaestebeitrag` (nights × guests × 2.5) and `energieaufschlag` (nights × guests × 1.5) are each `Math.round`ed separately. They are returned beside the total, **not** included in `:gesamtsumme`. JS `Math.round` rounds .5 up (7.5 → 8, 4.5 → 5). `:gesamtsumme` is rounded once at the end. `:einzeltage-summe` is not rounded at all, so it is a float, e.g. 7 × (500/7). [`per-night-per-guest-levies-rounding`, `cleaning-fee-below-reinigung-unter`] | preserved |
| `berechnen` returns `nil` for a `nil` stay. | preserved |

## Date rules (`buchung.datechecking`, `buchung.datepicker`)

| Quirk | Decision |
|---|---|
| Minimum stay is the **minimum** of the `mindestaufenthalt` values of all overlapping seasons (`js/parseInt`). Only when no season overlaps does `mindestaufenthalt_standard` apply. [`min-stay-is-min-over-overlapping-seasons`] | preserved |
| Season overlap for the minimum stay uses `t/overlaps?`, where touching does **not** count. A stay ending on a season's `beginn` ignores that season, and (on UTC dates) so does a stay starting on a season's `ende` day. Pricing treats `ende` as inclusive, so the two rules disagree at season edges. [`min-stay-is-min-over-overlapping-seasons`] | preserved |
| Blocked-stay check: a booking blocks `[dtstart, dtend − 1 day]`. Arriving on the previous guest's departure day is OK, and so is departing on the next guest's arrival day. A handover day as arrival is blocked. The Sunday rule is **not** part of this check. It exists only in the picker. [`blocked-stay-check-allows-departure-day-as-arrival`] | preserved |
| **Arrival on a booking's last night** slips through the stay check on UTC dates: `t/overlap` ignores touching intervals, and the stay `[anreise, abreise]` only *touches* `[dtstart, dtend − 1 day]` when `anreise = dtend − 1 day`. On the Berlin-local dates the picker produced (what guests saw) the local arrival midnight lies before that UTC day, so it **was** blocked; the picker also blocks that day as an interior day. | **blocked** in the new code (`buchung.verfuegbarkeit`, step 7): a booking occupies the nights `[dtstart, dtend)`. Matches production behaviour and keeps the server check from accepting a double booking. Parity test: exempt from oracle A, equal to oracle B. *Owner to confirm.* |
| iCal date-*time* values (not seen in the real feeds, which use `VALUE=DATE`): legacy compared exact instants. The new `prepare-belegung` applies the 47 h filter on the exact instants like legacy, then truncates both dates to their UTC day. | changed (day granularity only) |
| Error precedence: arrival-not-before-departure, then blocked, then minimum stay. Nothing is checked unless both dates are set. [`error-precedence`] | preserved |
| Picker: a day is blocked if it is a handover day (`dtend` of one booking = `dtstart` of the next, after sorting by `dtstart`), or a **Sunday that is the arrival or departure day** of any booking, or strictly inside a booking. Plain departure and arrival days on other weekdays are free. [`picker-blocked-days`] | preserved |
| `rework-wohnungen` drops vevents of ≤ 47 h, i.e. single-night bookings. Their days are never interior anyway; what gets lost is their Sunday-rule and handover effects. iCal dates parse to UTC midnight, so DST cannot move a booking across the 47 h line. [`rework-wohnungen-drops-short-vevents`] | preserved |
| The minimum-stay error message names the wohnung by its `:name` from `/api/buchungsproxy`. If the wohnung is unknown, the message says `null`. | preserved (new code returns data, views localize) |

## Time zone (root cause: mixing local and UTC midnights)

The picker hands out local `goog.date.Date`s. `days-in-interval` and the
picker cell `t/floor` every day except the **arrival day** into a UTC-midnight
instant that keeps the same calendar fields. Seasons and iCal dates are UTC
midnights. East of UTC (Europe/Berlin), the unfloored local arrival midnight is
an instant on the previous UTC day. All three quirks below vanish when the
legacy code is fed UTC dates.

| Quirk | Decision |
|---|---|
| **DST bug:** a stay spanning the spring switch (last Sunday of March) is 167 h long, and `t/in-days` floors it to N − 1 nights. The wrong count feeds the weekly tariff (7 nights are priced as 6 at the day rate), the levies, the surcharges and the minimum-stay check. The autumn switch (25 h night) is harmless. [`dst-bug-spring-switch`, `autumn-switch-is-harmless`] | **fixed (owner decision 2026-11-17)** |
| **Arrival on a season's first day** (Berlin-local): the arrival night matches no season, because the local midnight lies after the previous season's `ende` and before the new season's `beginn`. That night costs `grundpreis`. [`tz-artefact-arrival-on-season-first-day`] | **fixed as a consequence of decision 1** (calendar-day arithmetic makes it impossible to reproduce); new code = legacy on UTC dates. *Owner to confirm.* |
| **Arrival on a season's `ende` day** (Berlin-local): the stay overlaps that season for the minimum-stay check, so its `mindestaufenthalt` counts. On UTC dates it does not count. [`tz-artefact-min-stay-starting-on-season-ende`] | **fixed as a consequence of decision 1**; new code = legacy on UTC dates. *Owner to confirm.* |

Consequence for the parity tests of steps 6 and 7 (oracle B, legacy on
Berlin-local dates): besides stays spanning the spring switch, they must also
allow differences for stays **arriving on a season's first day** (price) and
**arriving on a season's `ende` day** (minimum stay). For those cases, assert
new == oracle A (legacy on UTC dates).

## Price admin (`preise.*`)

| Quirk | Decision |
|---|---|
| The weighted average (`gewichte-preise`) runs on the **raw string values** (`"78"`). JS coercion makes `*`/`>` work, and `""` is skipped because `"" > 0` is false. Weights are `count-days` (`ende` exclusive, minutes / 1440). [`weighted-average-on-raw-strings`] | preserved |
| `gewichte-preise` returns **`NaN`** (0/0) when no season has a positive value for the wohnung. The UI shows "NaN". [`weighted-average-on-raw-strings`] | preserved (value); display may be improved in the views step |
| **`:selected` is persisted to disk**: `serialize-daten` only stringifies the dates, so the UI selection is saved into `preise.edn` (the fixture has `:selected true` on seasons 39 and 40). `timify-daten` followed by `serialize-daten` round-trips the file exactly. [`admin-shape-round-trip`] | preserved in part: the re-frame admin keeps the selection as UI state (`:ui :selected`, seeded from `:selected true` on load) and writes `:selected true` back on the selected seasons only; `:selected false` flags are dropped on save (`preise.db/auf-disk`) |
| `seasons-overlap?` counts **touching** seasons (one's `beginn` = another's `ende`) as overlapping, unlike the booking side. `saison-overlaps?` excludes the checked season by **value equality**, so two identical seasons do not flag each other. [`admin-season-helpers`] | preserved for touching; the re-frame admin excludes the season itself **by `:id`** (`preise.db/saison-fehler`). Seasons always differ in id, so the result is the same. Legacy *editing* also matched seasons by value, so two identical seasons were edited together: **fixed**, every edit addresses one id |
| `next-saison` begins the day after the **latest `ende`**, regardless of gaps, and lasts 2 weeks. With no seasons it starts at `t/today`. [`admin-season-helpers`] | preserved; the new season additionally carries `:felder {}` so it passes the step-9 save schema (legacy left the key out) |
| "für nächstes Jahr klonen" shifts only `:selected` seasons by one year, and the clones keep `:selected true` until `unselect-all-saisonen` runs. The clones get fresh ids (`max + 1`, in `add-saison!`). [`admin-season-helpers`] | preserved |
| `interval-sn` silently substitutes an empty `now`–`now` interval, with a console warning, for an invalid season (not pinned by a test). | preserved on day granularity: `preise.db/seasons-overlap?` uses an empty interval at midday of `:heute` (local date at load), without the warning |

## Follow-ups (from the whole-branch review, step 16)

Found in the final review, deliberately not fixed in this run (larger than a
review fix, or a behaviour decision for the owner):

| Finding | Suggestion |
|---|---|
| The server stay check (`buchung.anfrage-pruefung`) does not reject an **arrival date in the past**. Only the picker enforces `day-selectable?` against today. A hand-crafted request for a past stay would reach the owner's inbox. | Reject `anreise` before the server's Berlin calendar date (`{:error :invalid :fields [[:anreise]]}`); mind the TZ (server runs UTC, guests think Berlin). |
| The server does not apply the picker-only **Sunday rule** (no arrival/departure on a Sunday that is a booking's arrival/departure day) and does not check handover days as departure. Pinned as legacy behaviour (`blocked-stay-check-allows-departure-day-as-arrival`), so the server is laxer than the picker. | If the owner wants the server as strict as the picker, add `day-blocked?` checks for `anreise`/`abreise` to `stay-error`'s server use (not to the shared rule, to keep the picker parity tests intact). |
| `maximalbelegung` missing for a wohnung: the SPA falls back to 6 guests (legacy), the server skips the capacity check (schema caps at 20). | Use `buchung.db/default-maximalbelegung` on the server too (move the constant to `src/common`). |
| A rate-limited booking request (HTTP 429) is shown as the generic "could not be sent" message. | Add a dedicated snippet if it turns out to matter in practice. |
| The price admin views are German-only (as legacy; the admin is used by the owner only). | None unless other operators appear. |
