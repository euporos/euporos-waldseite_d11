(ns legacy-characterization-test
  "Characterization tests pinning the behaviour of the frozen legacy oracle
   (test/cljs/legacy/**) on hand-picked cases. Each deftest documents one
   rule; doc/frontend-refactor-quirks.md lists the surprising ones.

   Inputs are built two ways:
   - `utc`   — UTC-midnight instants (what cljs-time.coerce/from-string makes
               of an ISO day; this is how seasons and iCal dates arrive);
   - `local` — process-local goog.date.Date built like the picker builds them
               (a local month start, then t/plus- days).
   Unless a test says otherwise the two agree, and the assertions run on both."
  (:require [cljs.test :refer [deftest is testing]]
            [cljs-time.core :as t]
            [legacy.buchung.ajaxing :as aj]
            [legacy.buchung.datechecking :as dc]
            [legacy.buchung.datepicker :as dp]
            [legacy.buchung.preisberechnung :as prs]
            [legacy.buchung.utils :as u]
            [legacy.preise.calculations :as calc]
            [legacy.preise.components :as comps]
            [legacy.preise.util :as pu]
            [test-support.fixtures :as fx]))

;; ---------------------------------------------------------------------------
;; Helpers

(defn- ymd [iso] (map js/parseInt (.split iso "-")))

(defn utc
  "ISO day → UTC-midnight cljs-time instant (legacy parse path)."
  [iso]
  (u/parse-iso iso))

(defn local
  "ISO day → local goog.date.Date the way the legacy picker produces one:
   first day of a local month, then t/plus- days."
  [iso]
  (let [[y m d] (ymd iso)]
    (t/plus- (t/local-date y m 1) (t/days (dec d)))))

(def date-kinds {:utc utc :local local})

(defn preise [] (aj/rework-preise (fx/preise)))

(defn wohnungen []
  (aj/rework-wohnungen
   (vec (for [[id bs] (sort-by key (fx/belegung))]
          {:id id :name (str "W" id) :belegung bs}))))

(defn belegung [whg-id]
  (u/get-by-id (wohnungen) whg-id :belegung))

(defn preis
  "Legacy price for a stay given as ISO days, with dates built by `mk`."
  ([whg von bis gaeste tiere] (preis utc whg von bis gaeste tiere (preise)))
  ([mk whg von bis gaeste tiere p]
   (prs/berechnen (t/interval (mk von) (mk bis)) gaeste tiere whg p)))

(defn- set-felder
  "Raw (string) preise.edn with one season field replaced, reworked."
  [season-id whg field v]
  (aj/rework-preise
   (update (fx/preise) :saisonen
           (fn [ss] (mapv #(if (= season-id (:id %))
                             (assoc-in % [:felder whg field] v)
                             %)
                          ss)))))

(defn- dst-zone? []
  ;; true when the process TZ observes DST (e.g. Europe/Berlin), false on UTC
  (not= (.getTimezoneOffset (js/Date. 2026 0 15))
        (.getTimezoneOffset (js/Date. 2026 6 15))))

;; ---------------------------------------------------------------------------
;; Data reworking (buchung.ajaxing)

(deftest rework-preise-parses-numbers-but-keeps-empty-strings
  (let [p (preise)
        s39 (first (filter #(= 39 (:id %)) (:saisonen p)))]
    (testing "string numbers become numbers"
      (is (= 78 (get-in s39 [:felder 1 :preis_tag])))
      (is (= 115 (get-in s39 [:felder 2 :preis_tag])))
      (is (= 2.5 (get-in p [:allgdaten :gaestebeitrag])))
      (is (= 22 (get-in p [:allgdaten :reinigung-unter]))))
    (testing "empty string stays an empty string"
      (is (= "" (get-in s39 [:felder 4 :preis_woche]))))
    (testing "season dates become instants plus an :interval; :selected survives"
      (is (t/= (utc "2025-01-27") (:beginn s39)))
      (is (t/= (utc "2025-03-09") (:ende s39)))
      (is (some? (:interval s39)))
      (is (true? (:selected s39))))))

(deftest rework-wohnungen-drops-short-vevents
  (testing "only vevents longer than 47 h survive (single nights dropped)"
    (let [bs (belegung 1)]
      (is (= 7 (count bs)))
      (is (not-any? #(t/= (utc "2026-08-20") (:dtstart %)) bs))
      (is (not-any? #(t/= (utc "2026-11-29") (:dtstart %)) bs))
      (is (some #(t/= (utc "2026-08-25") (:dtstart %)) bs) "48 h is kept"))))

;; ---------------------------------------------------------------------------
;; Price calculation (buchung.preisberechnung)

(deftest weekly-tariff-from-seven-nights
  (doseq [[kind mk] date-kinds]
    (testing (str kind " — 7 nights: preis_woche / 7 per night (season 51, whg 1: 500)")
      (let [r (preis mk 1 "2026-07-04" "2026-07-11" 2 0 (preise))]
        (is (= 7 (:tageszahl r)))
        (is (< (js/Math.abs (- 500 (:einzeltage-summe r))) 1e-9))
        (is (= 590 (:gesamtsumme r)))))
    (testing (str kind " — 6 nights: preis_tag per night (85)")
      (let [r (preis mk 1 "2026-07-04" "2026-07-10" 2 0 (preise))]
        (is (= 6 (:tageszahl r)))
        (is (= 510 (:einzeltage-summe r)))
        (is (= 600 (:gesamtsumme r)))))))

(deftest season-value-ten-or-less-or-missing-falls-back-to-grundpreis
  (testing "preis_tag 10 → grundpreis (75)"
    (let [p (set-felder 51 1 :preis_tag "10")]
      (is (= 225 (:einzeltage-summe (preis utc 1 "2026-07-04" "2026-07-07" 2 0 p))))))
  (testing "preis_tag 10.5 is used"
    (let [p (set-felder 51 1 :preis_tag "10.5")]
      (is (= 31.5 (:einzeltage-summe (preis utc 1 "2026-07-04" "2026-07-07" 2 0 p))))))
  (testing "field missing for the wohnung → grundpreis"
    (let [p (aj/rework-preise
             (update (fx/preise) :saisonen
                     (fn [ss] (mapv #(update % :felder dissoc 1) ss))))]
      (is (= 225 (:einzeltage-summe (preis utc 1 "2026-07-04" "2026-07-07" 2 0 p))))))
  (testing "no season covers the day (gap 2027-11-07 … 2027-12-22) → grundpreis"
    (let [r (preis 1 "2027-11-10" "2027-11-13" 2 0)]
      (is (= 225 (:einzeltage-summe r)))
      (is (= 315 (:gesamtsumme r))))))

(deftest empty-preis-woche-falls-back-to-grundpreis-not-preis-tag
  ;; whg 4 in season 39 has :preis_woche "" → ("" / 7) = 0 → not > 10
  (testing "7 nights: grundpreis 75, not preis_tag 83"
    (let [r (preis 4 "2025-02-01" "2025-02-08" 2 0)]
      (is (= 525 (:einzeltage-summe r)))
      (is (= 615 (:gesamtsumme r)))))
  (testing "6 nights: preis_tag 83"
    (is (= 498 (:einzeltage-summe (preis 4 "2025-02-01" "2025-02-07" 2 0))))))

(deftest season-lookup-inclusive-of-ende-first-match-wins
  (let [p (aj/rework-preise
           (assoc (fx/preise) :saisonen
                  [{:id 1 :beginn "2030-01-01" :ende "2030-01-10" :felder {1 {:preis_tag "50"}}}
                   {:id 2 :beginn "2030-01-10" :ende "2030-01-20" :felder {1 {:preis_tag "60"}}}]))]
    (testing "beginn inclusive (UTC dates; Berlin-local see tz-artefacts)"
      (is (= 50 (:einzeltage-summe (preis utc 1 "2030-01-01" "2030-01-02" 2 0 p)))))
    (doseq [[kind mk] date-kinds]
      (testing (str kind " — ende inclusive, and on overlap the first season wins")
        (is (= 50 (:einzeltage-summe (preis mk 1 "2030-01-10" "2030-01-11" 2 0 p)))))
      (testing (str kind " — after the shared day the second season applies")
        (is (= 60 (:einzeltage-summe (preis mk 1 "2030-01-11" "2030-01-12" 2 0 p))))))
    (testing "real fixture: season 51 ends 2026-09-10, 52 starts 09-11"
      (doseq [[kind mk] date-kinds]
        (is (= (+ (* 3 85) (* 3 80))
               (:einzeltage-summe (preis mk 1 "2026-09-08" "2026-09-14" 2 0 (preise))))
            (str kind))))))

(deftest surcharges-guests-pets
  (testing "whg 1 (standardbelegung 4): 6 guests, 2 pets, 7 nights"
    (let [r (preis 1 "2026-07-04" "2026-07-11" 6 2)]
      (is (= (* 7 2 15) (:belegungsaufschlag r)) "15 € per extra guest per night")
      (is (= (* 7 2 10) (:haustieraufschlag r)) "10 € per pet per night")
      (is (= 90 (:reinigungsaufschlag r)))
      (is (= (+ 500 210 140 90) (:gesamtsumme r)))
      (is (= 105 (:gaestebeitrag r)) "2.5 per guest per night, separate from total")
      (is (= 63 (:energieaufschlag r)) "1.5 per guest per night, separate from total")))
  (testing "guests at or below standardbelegung → no surcharge"
    (is (= 0 (:belegungsaufschlag (preis 1 "2026-07-04" "2026-07-11" 4 0))))))

(deftest cleaning-fee-below-reinigung-unter
  (testing "21 nights < 22 → cleaning fee"
    (let [r (preis 1 "2026-07-01" "2026-07-22" 2 0)]
      (is (= 90 (:reinigungsaufschlag r)))
      (is (= 1590 (:gesamtsumme r)))))
  (testing "22 nights → no cleaning fee; total rounded"
    (let [r (preis 1 "2026-07-01" "2026-07-23" 2 0)]
      (is (= 0 (:reinigungsaufschlag r)))
      (is (= 1571 (:gesamtsumme r)) "22 × 500/7 = 1571.43 → 1571"))))

(deftest per-night-per-guest-levies-rounding
  (testing "3 nights × 1 guest: gaestebeitrag 7.5 → 8, energie 4.5 → 5 (Math.round)"
    (let [r (preis 1 "2026-07-04" "2026-07-07" 1 0)]
      (is (= 8 (:gaestebeitrag r)))
      (is (= 5 (:energieaufschlag r)))))
  (testing "nil stay → nil"
    (is (nil? (prs/berechnen nil 2 0 1 (preise))))))

;; ---------------------------------------------------------------------------
;; Date checks (buchung.datechecking)

(defn- err [mk whg von bis]
  (reset! u/locale :de)
  (let [a (mk von) b (mk bis)
        aufenthalt (when (t/before? a b) (t/interval a b))]
    (dc/err-dates-bad? a b aufenthalt whg (wohnungen) (preise))))

(def msg-order "Das Anreisedatum muss vor dem Abreisedatum liegen.")
(def msg-belegt "Der gewählte Zeitraum umfasst bereits belegte Tage.")
(defn msg-min [whg n]
  (str "Für die gewählte Saison beträgt die Mindestbuchung der Wohnung W" whg " " n " Nächte."))

(deftest min-stay-is-min-over-overlapping-seasons
  (doseq [[kind mk] date-kinds]
    (testing (str kind " — whg 2: season 48 (min 5) + 49 (min 7) → 5")
      (is (= (msg-min 2 5) (err mk 2 "2026-04-05" "2026-04-09")))
      (is (nil? (err mk 2 "2026-04-04" "2026-04-09"))))
    (testing (str kind " — whg 2 entirely in season 49 → 7")
      (is (= (msg-min 2 7) (err mk 2 "2026-04-10" "2026-04-16")))
      (is (nil? (err mk 2 "2026-04-10" "2026-04-17"))))
    (testing (str kind " — touching does not overlap: a stay ending on a season's beginn ignores it (whg 3: 48 min 3, 49 min 5)")
      (is (nil? (err mk 3 "2026-04-05" "2026-04-08")))
      (is (= (msg-min 3 5) (err mk 3 "2026-04-08" "2026-04-11"))))
    (testing "UTC dates — a stay starting on a season's ende day ignores that season (Berlin-local see tz-artefacts)"
      (is (= (msg-min 3 5) (err utc 3 "2026-04-07" "2026-04-10"))))
    (testing (str kind " — no season → mindestaufenthalt_standard (whg 1: 5)")
      (is (= (msg-min 1 5) (err mk 1 "2027-11-10" "2027-11-13"))))))

(deftest blocked-stay-check-allows-departure-day-as-arrival
  (doseq [[kind mk] date-kinds]
    (testing (str kind)
      (is (nil? (err mk 1 "2026-07-18" "2026-07-25")) "arrive on previous guest's departure day")
      (is (nil? (err mk 1 "2026-06-26" "2026-07-04")) "depart on next guest's arrival day")
      (is (= msg-belegt (err mk 1 "2026-06-28" "2026-07-05")))
      (is (= msg-belegt (err mk 1 "2026-07-11" "2026-07-16")) "handover day as arrival")
      (testing "the Sunday rule is a picker rule only; the stay check ignores it"
        (is (nil? (err mk 1 "2026-07-27" "2026-08-02")))))))

(deftest error-precedence
  (doseq [[kind mk] date-kinds]
    (testing (str kind " — order before everything")
      (is (= msg-order (err mk 1 "2026-07-05" "2026-07-05")))
      (is (= msg-order (err mk 1 "2026-07-06" "2026-07-05"))))
    (testing (str kind " — blocked before min-stay (2 nights, overlapping booking)")
      (is (= msg-belegt (err mk 1 "2026-07-03" "2026-07-05"))))
    (testing (str kind " — nil when either date missing")
      (reset! u/locale :de)
      (is (nil? (dc/err-dates-bad? nil (mk "2026-07-05") nil 1 (wohnungen) (preise)))))))

;; ---------------------------------------------------------------------------
;; Picker blocking (buchung.datepicker)

(defn- blocked-in-picker?
  "As legacy day-cell calls it: the cell's day floored to t/day first
   (which turns a local goog.date.Date into a UTC-midnight instant with the
   same calendar fields)."
  [mk whg iso]
  (let [bs (belegung whg)]
    (boolean (dp/blocked? (t/floor (mk iso) t/day) bs (dp/handovers bs)))))

(deftest picker-blocked-days
  (testing "handovers: dtend of one == dtstart of the next"
    (let [hs (dp/handovers (belegung 1))]
      (is (= 1 (count hs)))
      (is (t/= (utc "2026-07-11") (first hs)))))
  (doseq [[kind mk] date-kinds]
    (testing (str kind)
      (is (blocked-in-picker? mk 1 "2026-07-05") "interior day")
      (is (not (blocked-in-picker? mk 1 "2026-07-04")) "arrival day (Sat) is free")
      (is (not (blocked-in-picker? mk 1 "2026-07-18")) "departure day (Sat) is free")
      (is (blocked-in-picker? mk 1 "2026-07-11") "handover day")
      (is (blocked-in-picker? mk 1 "2026-08-02") "Sunday arrival day")
      (is (blocked-in-picker? mk 1 "2026-08-09") "Sunday departure day")
      (is (not (blocked-in-picker? mk 1 "2026-11-29")) "single-night Sunday booking was filtered")
      (is (not (blocked-in-picker? mk 1 "2026-08-25")))
      (is (blocked-in-picker? mk 1 "2026-08-26"))
      (is (blocked-in-picker? mk 1 "2026-03-29") "interior day across spring DST")
      (is (blocked-in-picker? mk 1 "2026-10-25") "interior day across autumn DST")
      (is (not (blocked-in-picker? mk 3 "2026-07-05")) "other wohnung unaffected"))))

;; ---------------------------------------------------------------------------
;; Time-zone artefacts (same root cause as the DST bug: the anreise is an
;; unfloored local midnight, every other date is a UTC midnight; east of UTC
;; the local midnight is an instant on the *previous* UTC day). Under TZ=UTC
;; local == UTC and the artefacts vanish.

(defn- utc-process? [] (zero? (.getTimezoneOffset (js/Date. 2026 0 15))))

(deftest tz-artefact-arrival-on-season-first-day
  (testing "whg 1, arrival on the first day of season 52 (preis_tag 80), 3 nights"
    (is (= 240 (:einzeltage-summe (preis utc 1 "2026-09-11" "2026-09-14" 2 0 (preise)))))
    (is (= (if (utc-process?) 240 (+ 75 80 80))
           (:einzeltage-summe (preis local 1 "2026-09-11" "2026-09-14" 2 0 (preise))))
        "Berlin-local: the arrival night matches no season → grundpreis 75")))

(deftest tz-artefact-min-stay-starting-on-season-ende
  (testing "whg 3, arrival on the ende day of season 48 (min 3), season 49 min 5, 3 nights"
    (is (= (msg-min 3 5) (err utc 3 "2026-04-07" "2026-04-10")))
    (is (= (if (utc-process?) (msg-min 3 5) nil)
           (err local 3 "2026-04-07" "2026-04-10"))
        "Berlin-local: the stay overlaps season 48 too → min 3")))

;; ---------------------------------------------------------------------------
;; The DST bug (fixed by the refactor, owner decision 2026-11-17)

(deftest dst-bug-spring-switch
  (if-not (dst-zone?)
    (is true "process TZ has no DST — DST bug demonstration skipped")
    (testing "7 local nights across 2026-03-29 count as 6 (167 h floored)"
      (let [lo (preis local 1 "2026-03-25" "2026-04-01" 2 0 (preise))
            ut (preis utc   1 "2026-03-25" "2026-04-01" 2 0 (preise))]
        (is (= 7 (:tageszahl ut)))
        (is (= 6 (:tageszahl lo)) "the bug")
        (is (= 610 (:gesamtsumme ut)) "weekly tariff 520/7 × 7 + 90")
        (is (= 650 (:gesamtsumme lo)) "7 days at day tariff 80 + 90 (tariff switched by the 6)")
        (is (= 30 (:gaestebeitrag lo)) "levies use the floored count")
        (testing "min-stay check uses the floored count, too (whg 8, min 5 → 5 local nights fail)"
          (is (= (msg-min 8 5) (err local 8 "2026-03-27" "2026-04-01")))
          (is (nil? (err utc 8 "2026-03-27" "2026-04-01"))))))))

(deftest autumn-switch-is-harmless
  (testing "a 25 h night still floors to the right count"
    (is (= 7 (:tageszahl (preis local 1 "2026-10-21" "2026-10-28" 2 0 (preise)))))))

;; ---------------------------------------------------------------------------
;; Price admin (preise.*)

(defn- admin-saisonen []
  (:saisonen (pu/timify-daten (fx/preise))))

(deftest weighted-average-on-raw-strings
  (testing "count-days: whole days between beginn and ende (ende exclusive)"
    (is (= 41 (pu/count-days (utc "2025-01-27") (utc "2025-03-09")))))
  (testing "weighted by season length, on the unparsed string values"
    (let [ss [{:beginn (utc "2030-01-01") :ende (utc "2030-01-11") :felder {1 {:preis_tag "50"}}}
              {:beginn (utc "2030-02-01") :ende (utc "2030-02-21") :felder {1 {:preis_tag "80"}}}]]
      (is (= 70 (calc/gewichte-preise ss 1 :preis_tag)))))
  (testing "empty string and missing values are skipped"
    (let [ss [{:beginn (utc "2030-01-01") :ende (utc "2030-01-11") :felder {1 {:preis_tag ""}}}
              {:beginn (utc "2030-02-01") :ende (utc "2030-02-21") :felder {1 {:preis_tag "80"}}}
              {:beginn (utc "2030-03-01") :ende (utc "2030-03-21") :felder {}}]]
      (is (= 80 (calc/gewichte-preise ss 1 :preis_tag)))))
  (testing "no data → NaN"
    (is (js/isNaN (calc/gewichte-preise [] 1 :preis_tag)))
    (is (js/isNaN (calc/gewichte-preise (admin-saisonen) 99 :preis_tag)))))

(deftest admin-shape-round-trip
  (testing "timify-daten → serialize-daten restores the on-disk dates"
    (is (= (fx/preise) (pu/serialize-daten (pu/timify-daten (fx/preise)))))))

(deftest admin-season-helpers
  (let [s (fn [b e] {:beginn (utc b) :ende (utc e)})]
    (testing "touching seasons (beginn of one == ende of other) overlap"
      (is (comps/seasons-overlap? (s "2030-01-01" "2030-01-10") (s "2030-01-10" "2030-01-20")))
      (is (not (comps/seasons-overlap? (s "2030-01-01" "2030-01-10") (s "2030-01-11" "2030-01-20")))))
    (testing "saison-overlaps? ignores the season itself (by value)"
      (let [a (s "2030-01-01" "2030-01-10")]
        (is (false? (comps/saison-overlaps? a [a (s "2030-01-11" "2030-01-20")])))
        (is (true? (comps/saison-overlaps? a [a (s "2030-01-05" "2030-01-20")])))))
    (testing "the fixture's seasons are pairwise disjoint"
      (let [ss (admin-saisonen)]
        (is (not-any? #(comps/saison-overlaps? % ss) ss))))
    (testing "next-saison: day after the latest ende, two weeks long"
      (let [n (comps/next-saison (admin-saisonen))]
        (is (t/= (utc "2028-01-07") (:beginn n)))
        (is (t/= (utc "2028-01-21") (:ende n)))))
    (testing "copy-shift: only :selected seasons, shifted by one year"
      (let [shifted (comps/shift-saisonen-by-one-year (admin-saisonen))]
        (is (= [39 40] (map :id shifted)))
        (is (t/= (utc "2026-01-27") (:beginn (first shifted))))
        (is (true? (:selected (first shifted))) ":selected is copied along")))
    (testing "unselect-all"
      (is (not-any? :selected (comps/unselect-all-saisonen (admin-saisonen)))))))
