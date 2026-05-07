(ns buchung.fetch
  "Fetch the per-apartment iCal feeds in parallel and attach the parsed
   blocked-date events as :belegung on each apartment map."
  (:require [clojure.string :as str]
            [kitchen-async.promise :as p]
            [buchung.ical :as ical]
            [taoensso.timbre :refer [warnf]]))

(defn- split-urls
  "An apartment's :ical column may carry several URLs separated by spaces
   or commas. Split, trim, drop empties."
  [s]
  (when (string? s)
    (->> (str/split s #"[\s,]+")
         (map str/trim)
         (remove str/blank?)
         vec)))

(defn- today-iso []
  (subs (.toISOString (js/Date.)) 0 10))

(defn- past?
  "Drop events whose end is before today — we don't need historical
   blocks on the booking calendar."
  [today {:keys [dtend]}]
  (and (string? dtend) (< dtend today)))

(defn- fetch-one
  "Fetches a single ICS URL and returns a Promise of [{:dtstart :dtend :summary} …].
   Resolves to [] on any failure (parse, network, non-2xx)."
  [url]
  (-> (js/fetch url)
      (.then (fn [resp]
               (if (.-ok resp)
                 (.text resp)
                 (do (warnf "ical fetch %s -> HTTP %s" url (.-status resp))
                     ""))))
      (.then (fn [body]
               (or (ical/parse-vevents body) [])))
      (.catch (fn [e]
                (warnf "ical fetch %s failed: %s" url (.-message e))
                []))))

(defn- fetch-for-apartment
  "Each apartment may have several iCal URLs. Fetch all of them in parallel
   and concatenate the events."
  [{:keys [ical] :as wohnung}]
  (let [urls (split-urls ical)]
    (if (empty? urls)
      (p/resolve (assoc wohnung :belegung []))
      (-> (p/all (mapv fetch-one urls))
          (.then (fn [results]
                   (let [today (today-iso)
                         events (->> results
                                     (apply concat)
                                     (remove (partial past? today))
                                     vec)]
                     (-> wohnung
                         (dissoc :ical)
                         (assoc :belegung events)))))))))

(defn fetch-ical-feeds
  "Given a seq of apartment maps with :id and :ical (URL string), returns a
   Promise of a vector of apartment maps with :belegung attached and :ical
   stripped. All apartments and all URLs are fetched in parallel."
  [wohnungen]
  (p/all (mapv fetch-for-apartment wohnungen)))
