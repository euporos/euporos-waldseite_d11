(ns preise.lookup
  "Read-side helpers over the in-memory preise.edn snapshot.

   Public pages (apartment detail, future 'from €' indicators) need a
   compact per-wohnung price summary without re-parsing the EDN on every
   request — this layer pulls from preise.cache and shapes the data."
  (:require [preise.cache :as cache]))

(defn- ->num
  "Parses a price/min-stay value from preise.edn (strings like \"78\",
   \"115.00\", or \"\") into a positive number, or nil."
  [s]
  (cond
    (number? s) (when (pos? s) s)
    (string? s) (let [n (js/parseFloat s)]
                  (when (and (not (js/isNaN n)) (pos? n)) n))
    :else       nil))

(defn- today-iso []
  (subs (.toISOString (js/Date.)) 0 10))

(defn- current-or-future? [today saison]
  (let [ende (:ende saison)]
    (and (string? ende) (>= ende today))))

(defn- min-pos [xs]
  (when-let [vs (seq (keep ->num xs))]
    (apply min vs)))

(defn wohnung-summary
  "Returns {:grundpreis :maximalbelegung :standardbelegung
            :mindestaufenthalt_standard :tag-min :woche-min} for the given
   wohnung-id, or nil if the cache has no data for it.

   tag-min / woche-min are the lowest day/week prices across current and
   future seasons; nil if no usable values exist."
  [wohnung-id]
  (when-let [{:keys [basisdaten saisonen]} (cache/read-prices)]
    (let [basis (get basisdaten wohnung-id)]
      (when basis
        (let [today    (today-iso)
              relevant (filter (partial current-or-future? today) saisonen)
              felder   (keep #(get-in % [:felder wohnung-id]) relevant)]
          {:grundpreis                 (->num (:grundpreis basis))
           :maximalbelegung            (->num (:maximalbelegung basis))
           :standardbelegung           (->num (:standardbelegung basis))
           :mindestaufenthalt_standard (->num (:mindestaufenthalt_standard basis))
           :tag-min                    (min-pos (map :preis_tag felder))
           :woche-min                  (min-pos (map :preis_woche felder))})))))
