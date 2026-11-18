# Manual click-through after the frontend refactor

Run `nix run .#dev`, then check each item in a real browser.

## Booking (`/de|en|nl/buchung`)
- [ ] Each apartment can be selected; price updates.
- [ ] Guest count above capacity: clamp warning shown, count clamped.
- [ ] Pets toggle changes price/surcharge as before.
- [ ] Date range across a season boundary: price matches the price admin.
- [ ] 7-night stay spanning the end of March (DST switch): weekly tariff applied (not 6 nights).
- [ ] Blocked days cannot be picked; selecting across them shows the error.
- [ ] Stay shorter than the minimum: min-stay error shown.
- [ ] Submit success: confirmation shown, mail arrives with server-computed price.
- [ ] Server errors: invalid payload (400 `:invalid`), blocked dates, too-short stay each show a localized message; iCal unreachable → request accepted, mail has warning line; rate limit (non-dev) → error.
- [ ] Repeat in en and nl: all texts translated.

## Price admin (`:admin` build)
- [ ] Load shows current prices and seasons.
- [ ] Edit prices and season dates; add, clone, delete and trash a season.
- [ ] Invalid dates are flagged and block saving.
- [ ] Save, reload: saved data shown.
- [ ] Not logged in: the not-logged-in path behaves as before.

## Contact form (JavaScript disabled)
- [ ] Page contains no script tags; success path sends mail.
- [ ] Submit immediately (too fast): form returns with input kept and a "submit again" note.
- [ ] Honeypot `website` filled (e.g. via devtools): answered like a success, but no mail is sent (log line `dropped as spam`).
- [ ] Validation errors re-render the form with input kept.
- [ ] Datenschutz link present when a matching einzelseite exists.
- [ ] Many rapid posts: localized 429 page.
