(ns buchung.utils
  "Date/collection helpers used across the booking SPA. Trimmed-down port
   of the old waldseite utils.helpers + utils.tdstuff."
  (:require [cljs-time.coerce :as t.coerce]
            [cljs-time.core :as t]
            [cljs-time.format :as t.format]))

;; ---------- Collection helpers ----------

(defn get-by-id
  "First map in `coll` whose :id matches `id`; returns its `k` value."
  [coll id k]
  (some-> (first (filter #(= id (:id %)) coll)) (get k)))

(defn map-vals [f m]
  (into {} (for [[k v] m] [k (f v)])))

(defn update-multiple [m ks f]
  (reduce #(update %1 %2 f) m ks))

;; ---------- Date helpers ----------

(def ^:private iso-day-fmt (t.format/formatter "yyyy-MM-dd"))

(defn to-iso-day [d]
  (t.format/unparse iso-day-fmt d))

(defn parse-iso [s]
  (when (and (string? s) (seq s))
    (t.coerce/from-string s)))

(defn days-in-month [date-with-month]
  (let [first-day (t/first-day-of-the-month- date-with-month)]
    (take-while #(= (t/month %) (t/month first-day))
                (iterate #(t/plus- % (t/days 1)) first-day))))

(defn days-in-interval
  "Day-instances 00:00 covered by [start, end)."
  [start end]
  (take-while
   #(t/before? % end)
   (iterate #(-> % (t/plus- (t/days 1)) (t/floor t/day)) start)))

(defn within-inclusive? [d1 d2 testdate]
  (or (t/within? d1 d2 testdate)
      (t/= d2 testdate)))

(defn unordered-within?
  "t/within? with the bookend dates reordered so the caller doesn't have to."
  ([d1 d2 testdate] (unordered-within? d1 d2 testdate nil))
  ([d1 d2 testdate inclusive?]
   (let [args (concat (sort t/before? [d1 d2]) [testdate])]
     (apply (if inclusive? within-inclusive? t/within?) args))))

(defn within-left? [start end date]
  (or (t/within? start end date) (t/= end date)))

;; ---------- iCal vevent post-processing ----------

(defn timify-vevents
  "Server-parsed vevents arrive as {:dtstart <ISO string> :dtend <…>}.
   Coerce to cljs-time instants and drop any with invalid dates."
  [vevents]
  (->> vevents
       (keep (fn [{:keys [dtstart dtend] :as v}]
               (let [d1 (parse-iso dtstart)
                     d2 (parse-iso dtend)]
                 (when (and d1 d2)
                   (assoc v :dtstart d1 :dtend d2)))))
       vec))

;; ---------- German calendar labels ----------

(def weekday-short-de
  ["Mo" "Di" "Mi" "Do" "Fr" "Sa" "So"])

(def month-long-de
  ["Januar" "Februar" "März" "April" "Mai" "Juni"
   "Juli" "August" "September" "Oktober" "November" "Dezember"])

(defn month-label-de [date]
  (nth month-long-de (dec (t/month date))))

(defn format-date-de [d]
  (when d (t.format/unparse (t.format/formatter "DD.MM.y") d)))
