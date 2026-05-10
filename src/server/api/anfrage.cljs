(ns api.anfrage
  "Receive a booking-request POST from the browser SPA, validate the
   payload, send the confirmation email."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.util.response :as r]
            [cljs.reader :as reader]
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

(defhandler handler [req]
  (let [data (read-payload req)]
    (cond
      (nil? data)
      {:status 400 :headers {"Content-Type" "text/plain"} :body "missing anfragedaten"}

      (not (specs/valid? data))
      (do (warn "buchung anfrage rejected: schema mismatch")
          {:status 400 :headers {"Content-Type" "text/plain"} :body "invalid"})

      :else
      (-> (mailer/send! data)
          (.then (fn [_] (-> (r/ok "ok") (r/content-type "text/plain"))))
          (.catch (fn [e]
                    (warnf "buchung anfrage mail failed: %s" (.-message e))
                    {:status 500 :headers {"Content-Type" "text/plain"} :body "send-failed"}))))))
