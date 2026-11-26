(ns buchung-preis-test
  "Parity of the shared `buchung.preis/berechnen` with the frozen legacy
   `berechnen` (roadmap step 6).

   Oracle A: legacy fed UTC-midnight dates (`legacy.utils/parse-iso`) — the
             new code must be `=` exactly, always.
   Oracle B: legacy fed process-local goog.date.Dates built like the picker
             builds them; only run when the process TZ has DST. Equal except
             for the sanctioned differences (doc/frontend-refactor-quirks.md,
             \"Time zone\"): stays spanning the spring switch (DST bug, fixed)
             and stays arriving on a season's first day (TZ artefact). For
             those, oracle A is the reference."
  (:require [cljs.test :refer [deftest is testing]]
            [cljs-time.core :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [buchung.preis :as preis]
            [kalender.core :as kal]
            [legacy.buchung.ajaxing :as aj]
            [legacy.buchung.preisberechnung :as prs]
            [legacy.buchung.utils :as u]
            [preise.model :as pm]
            [test-support.fixtures :as fx]))

(def num-tests 20000)

;; ---------------------------------------------------------------------------
;; Inputs

(def new-preise    (memoize #(pm/normalize (fx/preise))))
(def legacy-preise (memoize #(aj/rework-preise (fx/preise))))

(defn- utc [iso] (u/parse-iso iso))

(defn- local
  "ISO day → local goog.date.Date as the legacy picker makes one."
  [iso]
  (let [[y m d] (map js/parseInt (.split iso "-"))]
    (t/plus- (t/local-date y m 1) (t/days (dec d)))))

(defn- dst-zone? []
  (not= (.getTimezoneOffset (js/Date. 2026 0 15))
        (.getTimezoneOffset (js/Date. 2026 6 15))))

(defn- new-preis
  ([a] (new-preis a (new-preise)))
  ([{:keys [anreise abreise gaeste tiere whg]} p]
   (preis/berechnen {:anreise anreise :abreise abreise :erwachsene gaeste
                     :haustierzahl tiere :wohnungsid whg}
                    p)))

(defn- legacy-preis
  ([mk a] (legacy-preis mk a (legacy-preise)))
  ([mk {:keys [anreise abreise gaeste tiere whg]} p]
   (prs/berechnen (t/interval (mk anreise) (mk abreise)) gaeste tiere whg p)))

;; ---------------------------------------------------------------------------
;; Sanctioned Berlin-local differences

(defn- local-offset [iso]
  (let [[y m d] (map js/parseInt (.split iso "-"))]
    (.getTimezoneOffset (js/Date. y (dec m) d))))

(defn- spans-spring-switch?
  "The local nights [anreise, abreise) lose an hour: the UTC offset grows."
  [{:keys [anreise abreise]}]
  (> (local-offset anreise) (local-offset abreise)))

(defn- arrives-on-season-first-day? [{:keys [anreise]}]
  (some #(= anreise (:beginn %)) (:saisonen (fx/preise))))

(defn- sanctioned-difference? [a]
  (or (spans-spring-switch? a) (arrives-on-season-first-day? a)))

;; ---------------------------------------------------------------------------
;; Generators

(def ^:private range-start "2024-10-01")
(def ^:private range-end   "2028-03-31")

(def ^:private boundaries
  "Days where the price model changes: every season's beginn, the day after
   every ende, and the spring/autumn DST Sundays."
  (vec (distinct
        (concat (mapcat (fn [{:keys [beginn ende]}] [beginn (kal/plus-days ende 1)])
                        (:saisonen (fx/preise)))
                ["2025-03-30" "2026-03-29" "2027-03-28"
                 "2025-10-26" "2026-10-25" "2027-10-31"]))))

(def gen-nights
  (gen/frequency [[5 (gen/choose 1 35)]
                  [3 (gen/elements [6 7 8])]
                  [2 (gen/elements [21 22 23])]]))

(defn- gen-anreise [nights]
  (gen/frequency
   [[6 (gen/fmap #(kal/plus-days range-start %)
                 (gen/choose 0 (kal/nights range-start range-end)))]
    ;; a stay crossing (or starting on / ending on) a boundary
    [4 (gen/let [b   (gen/elements boundaries)
                 off (gen/choose 0 nights)]
         (kal/plus-days b (- off)))]]))

(defn- max-gaeste [whg]
  (js/parseInt (get-in (fx/preise) [:basisdaten whg :maximalbelegung])))

(def gen-anfrage
  (gen/let [whg     (gen/choose 1 8)
            nights  gen-nights
            anreise (gen-anreise nights)
            gaeste  (gen/choose 1 (max-gaeste whg))
            tiere   (gen/choose 0 3)]
    {:whg whg :anreise anreise :abreise (kal/plus-days anreise nights)
     :gaeste gaeste :tiere tiere}))

(defn- check [prop]
  (let [r (tc/quick-check num-tests prop :seed (.now js/Date))]
    (is (true? (:pass? r))
        (pr-str (select-keys r [:seed :num-tests :fail :shrunk])))
    r))

;; ---------------------------------------------------------------------------
;; Property tests

(deftest parity-with-legacy-on-utc-dates
  (check (prop/for-all [a gen-anfrage]
           (= (legacy-preis utc a) (new-preis a)))))

(deftest parity-with-legacy-on-local-dates
  (if-not (dst-zone?)
    (is true "process TZ has no DST — oracle B skipped (it equals oracle A)")
    (let [differing (atom 0)]
      (check (prop/for-all [a gen-anfrage]
               (let [n (new-preis a)]
                 (if (sanctioned-difference? a)
                   (do (when (not= n (legacy-preis local a)) (swap! differing inc))
                       (= n (legacy-preis utc a)))
                   (= n (legacy-preis local a))))))
      (is (pos? @differing)
          "the generator must hit the sanctioned differences (else the exemption is untested)"))))

;; ---------------------------------------------------------------------------
;; Explicit regression cases (the step-4 characterization cases)

(defn- p [whg von bis gaeste tiere & [prs]]
  (new-preis {:whg whg :anreise von :abreise bis :gaeste gaeste :tiere tiere}
             (or prs (new-preise))))

(defn- with-felder [season-id whg field v]
  (pm/normalize
   (update (fx/preise) :saisonen
           (fn [ss] (mapv #(if (= season-id (:id %)) (assoc-in % [:felder whg field] v) %) ss)))))

(deftest regression-cases
  (testing "weekly tariff from 7 nights (season 51, whg 1: 500 / week, 85 / day)"
    (let [r (p 1 "2026-07-04" "2026-07-11" 2 0)]
      (is (= 7 (:tageszahl r)))
      (is (< (js/Math.abs (- 500 (:einzeltage-summe r))) 1e-9))
      (is (= 590 (:gesamtsumme r))))
    (is (= 600 (:gesamtsumme (p 1 "2026-07-04" "2026-07-10" 2 0)))))
  (testing "season value ≤ 10 or missing → grundpreis (75)"
    (is (= 225 (:einzeltage-summe (p 1 "2026-07-04" "2026-07-07" 2 0 (with-felder 51 1 :preis_tag "10")))))
    (is (= 31.5 (:einzeltage-summe (p 1 "2026-07-04" "2026-07-07" 2 0 (with-felder 51 1 :preis_tag "10.5")))))
    (is (= 225 (:einzeltage-summe (p 1 "2027-11-10" "2027-11-13" 2 0))) "gap between seasons"))
  (testing "empty preis_woche → grundpreis, not preis_tag (whg 4, season 39)"
    (is (= 525 (:einzeltage-summe (p 4 "2025-02-01" "2025-02-08" 2 0))))
    (is (= 498 (:einzeltage-summe (p 4 "2025-02-01" "2025-02-07" 2 0)))))
  (testing "season lookup inclusive of ende, first match wins"
    (let [prs (pm/normalize
               (assoc (fx/preise) :saisonen
                      [{:id 1 :beginn "2030-01-01" :ende "2030-01-10" :felder {1 {:preis_tag "50"}}}
                       {:id 2 :beginn "2030-01-10" :ende "2030-01-20" :felder {1 {:preis_tag "60"}}}]))]
      (is (= 50 (:einzeltage-summe (p 1 "2030-01-01" "2030-01-02" 2 0 prs))))
      (is (= 50 (:einzeltage-summe (p 1 "2030-01-10" "2030-01-11" 2 0 prs))))
      (is (= 60 (:einzeltage-summe (p 1 "2030-01-11" "2030-01-12" 2 0 prs))))))
  (testing "surcharges, cleaning fee, levies"
    (let [r (p 1 "2026-07-04" "2026-07-11" 6 2)]
      (is (= 210 (:belegungsaufschlag r)))
      (is (= 140 (:haustieraufschlag r)))
      (is (= 90 (:reinigungsaufschlag r)))
      (is (= 940 (:gesamtsumme r)))
      (is (= 105 (:gaestebeitrag r)))
      (is (= 63 (:energieaufschlag r))))
    (is (= 1590 (:gesamtsumme (p 1 "2026-07-01" "2026-07-22" 2 0))))
    (is (= 1571 (:gesamtsumme (p 1 "2026-07-01" "2026-07-23" 2 0))) "22 nights: no cleaning fee")
    (let [r (p 1 "2026-07-04" "2026-07-07" 1 0)]
      (is (= 8 (:gaestebeitrag r)))
      (is (= 5 (:energieaufschlag r)))))
  (testing "arrival on a season's first day uses that season (TZ artefact fixed)"
    (is (= 240 (:einzeltage-summe (p 1 "2026-09-11" "2026-09-14" 2 0)))))
  (testing "DST fixed: 7 nights across 2026-03-29 are 7 nights, weekly tariff"
    (let [r (p 1 "2026-03-25" "2026-04-01" 2 0)]
      (is (= 7 (:tageszahl r)))
      (is (= 610 (:gesamtsumme r)))
      (is (= 35 (:gaestebeitrag r)))))
  (testing "nil when dates missing, invalid or not ordered"
    (is (nil? (p 1 nil "2026-07-07" 2 0)))
    (is (nil? (p 1 "2026-07-04" nil 2 0)))
    (is (nil? (p 1 "2026-07-04" "2026-07-04" 2 0)))
    (is (nil? (p 1 "2026-07-07" "2026-07-04" 2 0)))
    (is (nil? (p 1 "2026-02-30" "2026-03-04" 2 0)))))

;; ---------------------------------------------------------------------------
;; Guest categories (owner decision 2026-11-26): children 7–16 pay the child
;; Gästebeitrag (adult rate when empty), up to 6 none; everyone counts
;; towards Belegungs- and Energieaufschlag.

(defn- familie [preise]
  (preis/berechnen {:anreise "2026-07-04" :abreise "2026-07-11"
                    :erwachsene 2 :kinder-7-16 2 :kinder-bis-6 1
                    :haustierzahl 0 :wohnungsid 1}
                   (pm/normalize preise)))

(deftest gaestekategorien
  (testing "no child rate: children 7–16 pay the adult rate, up to 6 nothing"
    (let [r (familie (fx/preise))]
      (is (= 5 (:gaestezahl r)))
      (is (= 70 (:gaestebeitrag r)) "7 × (2 × 2.5 + 2 × 2.5)")
      (is (= 53 (:energieaufschlag r)) "7 × 5 × 1.5 = 52.5, rounded")
      (is (= 105 (:belegungsaufschlag r)) "7 × (5 − 4) × 15")))
  (testing "empty child rate falls back to the adult rate"
    (is (= 70 (:gaestebeitrag (familie (assoc-in (fx/preise) [:allgdaten :gaestebeitrag_kind] ""))))))
  (testing "child rate set"
    (is (= 49 (:gaestebeitrag (familie (assoc-in (fx/preise) [:allgdaten :gaestebeitrag_kind] "1"))))
        "7 × (2 × 2.5 + 2 × 1)")
    (is (= 35 (:gaestebeitrag (familie (assoc-in (fx/preise) [:allgdaten :gaestebeitrag_kind] "0"))))
        "a child rate of 0 is a rate, not a fallback"))
  (testing "gaestezahl sums the categories, missing ones as 0"
    (is (= 3 (preis/gaestezahl {:erwachsene 1 :kinder-bis-6 2})))))
