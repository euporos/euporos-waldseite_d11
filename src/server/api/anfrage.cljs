(ns api.anfrage
  "Receive a booking-request POST from the browser SPA, validate the
   payload, send the confirmation email."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.util.response :as r]
            [kitchen-async.promise :as p]
            [cljs.reader :as reader]
            [config.env :as env]
            [db.setup :as db]
            [db.queries :as q]
            [buchung.mailer :as mailer]
            [specs.anfrage :as specs]
            [taoensso.timbre :refer [warn warnf]]))

(defn- read-payload
  "The SPA posts EDN as the value of the :anfragedaten form param so the
   anti-forgery middleware sees a normal form POST. Accept either a
   form-encoded string or an already-parsed map (when callers post EDN
   directly via wrap-edn-params)."
  [req]
  (let [s (or (get-in req [:params :anfragedaten])
              (get-in req [:form-params "anfragedaten"]))]
    (cond
      (map? s)    s
      (string? s) (try (reader/read-string s)
                       (catch :default _ nil))
      :else       nil)))

(defn- recipient-from-db []
  (-> (db/query (q/einstellungen-content))
      (.then (fn [rows]
               (or (some-> rows first :email_buchung_empfang not-empty)
                   (env/setting :admin-email))))
      (.catch (fn [_]
                (env/setting :admin-email)))))

(defhandler handler [req]
  (let [data (read-payload req)]
    (cond
      (nil? data)
      {:status 400 :headers {"Content-Type" "text/plain"} :body "missing anfragedaten"}

      (not (specs/valid? data))
      (do (warn "buchung anfrage rejected: schema mismatch")
          {:status 400 :headers {"Content-Type" "text/plain"} :body "invalid"})

      :else
      (-> (p/let [recipient (recipient-from-db)
                  _         (mailer/send! data :recipient recipient)]
            (-> (r/ok "ok") (r/content-type "text/plain")))
          (.catch (fn [e]
                    (warnf "buchung anfrage mail failed: %s" (.-message e))
                    {:status 500 :headers {"Content-Type" "text/plain"} :body "send-failed"}))))))
