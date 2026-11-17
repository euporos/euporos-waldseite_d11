(ns kalender-core-test
  (:require [cljs.test :refer [deftest is testing]]
            [cljs-time.core :as t]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [kalender.core :as kal]
            [legacy.buchung.utils :as u]))

(defn- local
  "Local goog.date.Date the way the legacy picker builds one."
  [iso]
  (let [[y m d] (map js/parseInt (.split iso "-"))]
    (t/plus- (t/local-date y m 1) (t/days (dec d)))))

(def gen-iso
  (gen/fmap #(kal/plus-days "2020-01-01" %) (gen/choose 0 (* 366 10))))

(deftest iso-conversion
  (let [d (kal/iso->date "2026-03-29")]
    (is (= 0 (.getUTCHours (js/Date. (.getTime d)))) "UTC midnight")
    (is (= "2026-03-29" (kal/date->iso d))))
  (is (= "2026-03-29" (kal/date->iso (local "2026-03-29"))) "local dates keep their calendar day")
  (is (= "2026-03-29" (kal/date->iso "2026-03-29")))
  (is (nil? (kal/iso->date "")))
  (is (nil? (kal/iso->date nil)))
  (is (nil? (kal/date->iso nil)))
  (testing "same instant as the legacy parse path"
    (is (t/= (u/parse-iso "2025-10-26") (kal/iso->date "2025-10-26")))))

(deftest valid-iso
  (is (kal/valid-iso? "2024-02-29"))
  (is (not (kal/valid-iso? "2025-02-29")))
  (is (not (kal/valid-iso? "2025-13-01")))
  (is (not (kal/valid-iso? "2025-1-01")))
  (is (not (kal/valid-iso? nil))))

(deftest nights-across-dst
  (testing "spring switch 2026-03-29 (Europe/Berlin): exact, never floored"
    (is (= 7 (kal/nights "2026-03-25" "2026-04-01")))
    (is (= 1 (kal/nights "2026-03-28" "2026-03-29")))
    (is (= 1 (kal/nights "2026-03-29" "2026-03-30")))
    (is (= 7 (kal/nights (local "2026-03-25") (local "2026-04-01"))) "local dates")
    (is (= 7 (kal/nights (kal/iso->date "2026-03-25") (kal/iso->date "2026-04-01")))))
  (testing "autumn switch 2025-10-26"
    (is (= 7 (kal/nights "2025-10-22" "2025-10-29")))
    (is (= 7 (kal/nights (local "2025-10-22") (local "2025-10-29")))))
  (testing "mixed representations agree"
    (is (= 3 (kal/nights (local "2026-03-28") (kal/iso->date "2026-03-31")))))
  (is (= 0 (kal/nights "2026-01-01" "2026-01-01")))
  (is (= -2 (kal/nights "2026-01-03" "2026-01-01")))
  (is (= 366 (kal/nights "2024-01-01" "2025-01-01"))))

(defspec nights-equal-legacy-on-utc-dates 500
  ;; legacy t/in-days is correct on UTC dates; kal/nights must agree, in every TZ
  (prop/for-all [a gen-iso n (gen/choose 0 60)]
    (let [b (kal/plus-days a n)]
      (= n
         (kal/nights a b)
         (kal/nights (local a) (local b))
         (t/in-days (t/interval (u/parse-iso a) (u/parse-iso b)))))))

(deftest days-between
  (is (= ["2026-03-28" "2026-03-29" "2026-03-30"]
         (kal/days-between "2026-03-28" "2026-03-31")))
  (is (= [] (kal/days-between "2026-03-28" "2026-03-28")))
  (testing "date input yields UTC-midnight instants, same as legacy on UTC"
    (let [a (kal/iso->date "2026-03-27") b (kal/iso->date "2026-04-02")]
      (is (= (map kal/date->iso (u/days-in-interval a b))
             (map kal/date->iso (kal/days-between a b))))
      (is (every? #(zero? (.getUTCHours (js/Date. (.getTime %))))
                  (kal/days-between a b)))))
  (testing "local DST-spanning input still gives every calendar day once"
    (is (= ["2026-03-28" "2026-03-29" "2026-03-30"]
           (map kal/date->iso (kal/days-between (local "2026-03-28") (local "2026-03-31")))))))

(deftest weekday
  (is (= 1 (kal/weekday "2026-11-16")))
  (is (= 7 (kal/weekday "2026-03-29")))
  (is (= 4 (kal/weekday "1970-01-01")))
  (is (= 3 (kal/weekday "1969-12-31")))
  (is (= 7 (kal/weekday (local "2026-03-29")))))

(defspec weekday-matches-cljs-time 300
  (prop/for-all [a gen-iso]
    (= (t/day-of-week (u/parse-iso a)) (kal/weekday a))))

(deftest days-in-month
  (is (= 29 (count (kal/days-in-month 2024 2))))
  (is (= 28 (count (kal/days-in-month "2025-02-14"))))
  (is (= "2025-12-31" (last (kal/days-in-month 2025 12))))
  (is (= (map kal/date->iso (u/days-in-month (local "2026-03-15")))
         (kal/days-in-month 2026 3))))

(defn- legacy-grid
  "Cells of legacy datepicker/format-month (prev-tail, month, next-head,
   first 42), as [iso outside?]."
  [y m]
  (let [shown     (t/local-date y m 15)
        f-day     (t/first-day-of-the-month- shown)
        f-weekday (dec (t/day-of-week f-day))
        days      (u/days-in-month shown)
        prev-tail (reverse (take f-weekday (reverse (u/days-in-month (t/minus- shown (t/months 1))))))
        next-head (u/days-in-month (t/plus- shown (t/months 1)))]
    (take 42 (concat (map #(vector (kal/date->iso %) true) prev-tail)
                     (map #(vector (kal/date->iso %) false) days)
                     (map #(vector (kal/date->iso %) true) next-head)))))

(deftest month-grid
  (let [g (kal/month-grid 2026 6)]          ; 2026-06-01 is a Monday
    (is (= 6 (count g)))
    (is (every? #(= 7 (count %)) g))
    (is (= {:iso "2026-06-01" :outside? false} (ffirst g)))
    (is (= {:iso "2026-07-12" :outside? true} (last (last g)))))
  (let [g (kal/month-grid "2026-03-10")]    ; 2026-03-01 is a Sunday
    (is (= {:iso "2026-02-23" :outside? true} (ffirst g)))
    (is (= "2026-03-01" (:iso (nth (first g) 6)))))
  (testing "matches legacy format-month for every month 2024–2028"
    (doseq [y (range 2024 2029) m (range 1 13)]
      (is (= (legacy-grid y m)
             (map (juxt :iso :outside?) (apply concat (kal/month-grid y m))))
          (str y "-" m)))))

(deftest today-iso
  (is (= "2026-03-29" (kal/today-iso (js/Date. 2026 2 29 0 30))) "local fields, just after midnight")
  (is (= "2026-12-31" (kal/today-iso (js/Date. 2026 11 31 23 59))))
  (is (re-matches kal/iso-day-regex (kal/today-iso))))
