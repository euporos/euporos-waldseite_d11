(ns buchung.ical
  "Server-side iCal parser. Wraps ical.js and exposes a tiny helper
   that turns a feed string into a vector of {:dtstart :dtend :summary}
   maps with ISO date strings — the shape the booking SPA expects."
  (:require ["ical.js" :as ical]))

(def ^:private datefields
  #{"dtstart" "dtend" "dtstamp"})

(defn- vevents-from-jcal
  "ical.js' .parse returns jCal: [\"vcalendar\" props components]. We want
   the vevent components (each: [\"vevent\" props sub-components])."
  [jcal]
  (->> (nth jcal 2 [])
       (filter #(= "vevent" (first %)))))

(defn- props->map
  "ical.js property is [name params type value]. We keep the value as-is
   (string for date/datestring/dtstart, etc.) — no time-coercion server-side."
  [props]
  (reduce (fn [m [name _params _type value]]
            (assoc m (keyword name) value))
          {}
          props))

(defn parse-vevents
  "Parses an ICS feed string into [{:dtstart :dtend :summary :uid}].
   Date values stay as strings (typically ISO date or date-time).
   Returns nil on parse failure."
  [ics-string]
  (try
    (let [jcal (.parse ical ics-string)
          jcal (js->clj jcal)]
      (->> (vevents-from-jcal jcal)
           (map (comp props->map second))
           vec))
    (catch :default _ nil)))
