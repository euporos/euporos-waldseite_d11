(ns kalender.core
  "Calendar-day helpers shared by browser and server.

   Canonical representation is the ISO day string \"yyyy-MM-dd\". When a date
   object is needed it is a cljs-time UTC-midnight instant (what
   `cljs-time.coerce/from-string` makes of an ISO day — the same type seasons
   and iCal dates had in the legacy code).

   All arithmetic works on calendar fields (year/month/day), never on elapsed
   hours, so results are independent of the process time zone and exact
   across DST switches (the legacy `t/in-days` floored a 167 h spring-switch
   stay to N-1 nights — fixed, owner decision 2026-11-17)."
  (:require [cljs-time.coerce :as t.coerce]
            [cljs-time.core :as t]))

(def iso-day-regex #"^\d{4}-\d{2}-\d{2}$")

(def ^:private ms-per-day 86400000)

(defn- pad2 [n] (if (< n 10) (str "0" n) (str n)))

(defn- ymd->iso [y m d]
  (str y "-" (pad2 m) "-" (pad2 d)))

(defn- fields
  "[y m d] (m 1-based) of an ISO day string or any cljs-time date object
   (its own calendar fields: UTC fields for UTC instants, local fields for
   goog.date.Date)."
  [d]
  (if (string? d)
    (let [[y m dd] (map #(js/parseInt % 10) (.split d "-"))]
      [y m dd])
    [(t/year d) (t/month d) (t/day d)]))

(defn- epoch-day
  "Days since 1970-01-01 of the calendar date `d` (ISO or date)."
  [d]
  (let [[y m dd] (fields d)]
    (/ (js/Date.UTC y (dec m) dd) ms-per-day)))

(defn- epoch-day->iso [n]
  (let [js-d (js/Date. (* n ms-per-day))]
    (ymd->iso (.getUTCFullYear js-d) (inc (.getUTCMonth js-d)) (.getUTCDate js-d))))

;; ---------------------------------------------------------------------------
;; Conversion

(defn iso->date
  "ISO day → UTC-midnight cljs-time instant; nil for nil/blank/invalid
   input (same as legacy `parse-iso`)."
  [s]
  (when (and (string? s) (seq s))
    (t.coerce/from-string s)))

(defn date->iso
  "Date object (UTC instant or local goog.date.Date) → ISO day of its own
   calendar fields. ISO strings pass through; nil → nil."
  [d]
  (cond
    (nil? d)    nil
    (string? d) d
    :else       (apply ymd->iso (fields d))))

(defn valid-iso?
  "True for a well-formed ISO day naming a real calendar date."
  [s]
  (boolean
   (and (string? s)
        (re-matches iso-day-regex s)
        (let [[y m d] (fields s)]
          (= s (epoch-day->iso (/ (js/Date.UTC y (dec m) d) ms-per-day)))))))

;; ---------------------------------------------------------------------------
;; Arithmetic

(defn plus-days
  "ISO day `d` (or date) shifted by `n` calendar days, as ISO."
  [d n]
  (epoch-day->iso (+ (epoch-day d) n)))

(defn nights
  "Number of nights from `anreise` to `abreise` (calendar-day difference;
   ISO strings or date objects). Negative when abreise is before anreise."
  [anreise abreise]
  (- (epoch-day abreise) (epoch-day anreise)))

(defn days-between
  "Calendar days in [start, end). With ISO input returns ISO strings,
   with date input UTC-midnight instants."
  [start end]
  (let [isos (map #(epoch-day->iso %) (range (epoch-day start) (epoch-day end)))]
    (if (string? start)
      (vec isos)
      (mapv iso->date isos))))

(defn weekday
  "ISO weekday of a day: 1 = Monday … 7 = Sunday."
  [d]
  ;; 1970-01-01 was a Thursday (4)
  (inc (mod (+ (epoch-day d) 3) 7)))

(defn days-in-month
  "ISO days of the month (`year`, `month` 1-based), or of the month
   containing day `d`."
  ([d] (let [[y m] (fields d)] (days-in-month y m)))
  ([year month]
   (let [first-day (epoch-day (ymd->iso year month 1))
         [ny nm]   (if (= 12 month) [(inc year) 1] [year (inc month)])
         next-first (epoch-day (ymd->iso ny nm 1))]
     (mapv epoch-day->iso (range first-day next-first)))))

(defn month-grid
  "Picker grid for a month: 6 weeks × 7 days, Monday first, as a vector of
   6 rows of `{:iso \"yyyy-MM-dd\" :outside? bool}`. Matches legacy
   `format-month`: the tail of the previous month up to the 1st (none when
   the 1st is a Monday), the month, then the next month's head."
  ([d] (let [[y m] (fields d)] (month-grid y m)))
  ([year month]
   (let [first-iso (ymd->iso year month 1)
         start     (- (epoch-day first-iso) (dec (weekday first-iso)))]
     (->> (range start (+ start 42))
          (map (fn [n]
                 (let [iso (epoch-day->iso n)
                       [y m] (fields iso)]
                   {:iso iso :outside? (not (and (= y year) (= m month)))})))
          (partition 7)
          (mapv vec)))))

(defn today-iso
  "The runtime's **local** calendar date as ISO (from js/Date getters).
   Optionally of a given js/Date."
  ([] (today-iso (js/Date.)))
  ([^js js-date]
   (ymd->iso (.getFullYear js-date) (inc (.getMonth js-date)) (.getDate js-date))))
