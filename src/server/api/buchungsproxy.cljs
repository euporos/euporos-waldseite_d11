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
            [buchung.fetch :as fetch]
            [preise.cache :as cache]))

(defn- with-image-url
  "Resolve hauptbild → URL server-side so the browser never has to know
   the Directus origin or preset name."
  [w]
  (cond-> (dissoc w :hauptbild)
    (:hauptbild w) (assoc :hauptbild-url
                          (directus/image-by-preset "600" (:hauptbild w)))))

(defhandler handler [_req]
  (p/let [rows         (db/query (q/wohnungen-with-ical))
          ;; wohnungen.id arrives from the driver as a string; preise.edn
          ;; keys :basisdaten/:felder by integer id, so coerce here to
          ;; keep client-side lookups type-aligned.
          wohnungen-in (mapv #(-> % (update :id js/parseInt) with-image-url) rows)
          wohnungen    (fetch/fetch-ical-feeds wohnungen-in)]
    (-> (r/ok (pr-str {:wohnungen (vec (sort-by :id wohnungen))
                       :preise    (cache/read-prices)}))
        (r/content-type "application/edn"))))
