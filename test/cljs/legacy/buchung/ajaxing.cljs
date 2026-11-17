;; FROZEN LEGACY ORACLE (roadmap step 4). Verbatim excerpt of src/browser/buchung/ajaxing.cljs @ ede36db:
;; only the ns name, defn- -> defn, ^:private dropped and requires re-pointed
;; to the legacy copies. Never edit the logic below — it defines "old behaviour".
(ns legacy.buchung.ajaxing
  "Pure data-reworking parts of the legacy buchung.ajaxing (the response
   post-processing of /api/buchungsproxy)."
  (:require [cljs-time.core :as t]
            [legacy.buchung.utils :as u]))

(defn timify-saisonen [saisonen]
  (->> saisonen
       (map (fn [{:keys [beginn ende] :as s}]
              (let [b (u/parse-iso beginn)
                    e (u/parse-iso ende)]
                (-> s
                    (assoc :beginn b :ende e)
                    (assoc :interval (when (and b e) (t/interval b e)))))))
       vec))

(defn numparse-recursively
  "Walks `data` and parses values at any of `ks` from string to number.
   Mirrors the legacy edn-proc/numparse-recursively-selective behaviour
   that the SPA's preisberechnung relies on."
  [data ks]
  (let [keyset (set ks)]
    (letfn [(walk [v]
              (cond
                (map? v)        (into {} (map (fn [[k v]]
                                                [k (if (and (keyset k) (string? v) (seq v))
                                                     (let [n (js/parseFloat v)]
                                                       (if (js/isNaN n) v n))
                                                     (walk v))])
                                              v))
                (sequential? v) (mapv walk v)
                :else           v))]
      (walk data))))

(def numeric-fields
  [:mindestaufenthalt :mindestaufenthalt_standard :grundpreis
   :preis_woche :preis_tag :reinigung-unter :standardbelegung
   :maximalbelegung :reinigung :aufschlag_zus_person
   :aufschlag_haustier :gaestebeitrag :energieaufschlag])

(defn rework-preise [preise]
  (-> preise
      (update :saisonen timify-saisonen)
      (numparse-recursively numeric-fields)))

(defn rework-wohnungen
  "Filter out single-night vevents and coerce dates to cljs-time."
  [wohnungen]
  (mapv
   (fn [w]
     (update w :belegung
             (fn [vs]
               (->> (u/timify-vevents vs)
                    (filter (fn [{:keys [dtstart dtend]}]
                              (> (t/in-hours (t/interval dtstart dtend)) 47)))
                    vec))))
   wohnungen))
