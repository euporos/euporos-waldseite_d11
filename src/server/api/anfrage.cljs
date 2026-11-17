(ns api.anfrage
  "POST /api/anfrage — booking request from the browser SPA.

   Transport: the SPA POSTs an `application/edn` body (CSRF token in the
   `X-CSRF-Token` header); `psite-middleware.core/wrap-edn-params` on the
   /api/ tree puts it on `:edn-params`. Responses are EDN. Rate-limited per
   IP (bucket `:anfrage`, see `api.routes`).

   All checks live in `buchung.anfrage-pruefung`; this namespace only wires
   in the real price cache, occupancy fetch, mailer and analytics."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [analytics.posthog :as ph]
            [buchung.anfrage-pruefung :as pruefung]
            [buchung.fetch :as fetch]
            [buchung.mailer :as mailer]
            [db.queries :as q]
            [db.setup :as db]
            [preise.cache :as cache]
            [taoensso.timbre :refer [warn]]))

(defn- fetch-wohnung
  "Promise of the wohnung's `{:name … :belegung [vevents] …}` (iCal feeds
   fetched like /api/buchungsproxy does), nil if it is not in the DB."
  [wohnungsid]
  (-> (db/query (q/wohnungen-with-ical))
      (.then (fn [rows]
               ;; wohnungen.id arrives from the driver as a string
               (if-let [w (first (filter #(= (str wohnungsid) (str (:id %))) rows))]
                 (.then (fetch/fetch-ical-feeds [w]) first)
                 nil)))))

(defn- deps [req]
  {:read-prices   cache/read-prices
   :fetch-wohnung fetch-wohnung
   :send-mail!    mailer/send!
   :track!        (fn [event props]
                    (ph/capture! req event (assoc props :locale (some-> (:locale req) name))))
   :warn!         (fn [msg] (warn msg))})

(defhandler handler [req]
  (pruefung/respond (deps req) (:edn-params req)))
