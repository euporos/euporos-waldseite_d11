(ns api.buchungsproxy
  "Aggregator for the booking SPA: returns all apartments together with
   their iCal-derived blocked dates and the cached price structure.

   Two deviations from the legacy waldseite implementation:

   1. Prices come from the in-memory `preise.cache` snapshot of preise.edn
      — the public side never re-reads from disk per request.
   2. iCal feeds are fetched in parallel via Promise.all and parsed
      server-side, so the browser receives plain {:dtstart :dtend} maps
      and never needs ical.js."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.util.response :as r]
            [kitchen-async.promise :as p]
            [db.setup :as db]
            [db.queries :as q]
            [directus.core :as directus]
            [serving.routing :as rt]
            [buchung.fetch :as fetch]
            [preise.cache :as cache]))

(defn- with-derived
  "Resolve hauptbild → URL, attach the apartment's permalink and the
   parent house name. The browser never needs to know the Directus
   origin or how the route is built."
  [haeuser-by-id req w]
  (cond-> (dissoc w :hauptbild)
    (:hauptbild w) (assoc :hauptbild-url
                          (directus/image-by-preset "600" (:hauptbild w)))
    (:name w)      (assoc :wohnung-url
                          (rt/path-wohnung req (:id w) (:name w)))
    (:haus w)      (assoc :haus-name
                          (get haeuser-by-id (:haus w)))))

(defhandler handler [req]
  (p/let [locale       (:locale req)
          rows         (db/query (q/wohnungen-with-ical))
          haeuser      (db/query (q/haeuser-overview locale))
          haeuser-by-id (into {} (map (juxt (comp str :id) :name)) haeuser)
          ;; wohnungen.id arrives from the driver as a string; preise.edn
          ;; keys :basisdaten/:felder by integer id, so coerce here to
          ;; keep client-side lookups type-aligned.
          wohnungen-in (mapv #(-> % (update :id js/parseInt)
                                  ((partial with-derived haeuser-by-id req)))
                             rows)
          wohnungen    (fetch/fetch-ical-feeds wohnungen-in)]
    (-> (r/ok (pr-str {:wohnungen (vec (sort-by :id wohnungen))
                       :preise    (cache/read-prices)}))
        (r/content-type "application/edn"))))
