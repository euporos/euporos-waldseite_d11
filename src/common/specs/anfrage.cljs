(ns specs.anfrage
  "Schema of the booking request the SPA POSTs to /api/anfrage (EDN body).
   Shared: the SPA enables its submit button on it, the server rejects
   anything that does not match (`api.anfrage`)."
  (:require [malli.core :as m]
            [kalender.core :as kal]
            [specs.common :refer [email-regex]]))

(def date-regex #"^\d{4}-\d{2}-\d{2}$")

(def iso-day
  "\"yyyy-MM-dd\" that is a real calendar day (no 2026-02-30)."
  [:and [:re date-regex] [:fn {:error/message "invalid date"} kal/valid-iso?]])

(def gast
  [:map
   [:vorname        [:string {:min 1 :max 256}]]
   [:nachname       [:string {:min 1 :max 256}]]
   [:email          [:re email-regex]]
   [:telefonnummer  [:string {:min 1 :max 256}]]])

(def payload
  [:map
   [:anreise                  iso-day]
   [:abreise                  iso-day]
   [:erwachsene               [:int {:min 1 :max 20}]]
   [:kinder-7-16              [:int {:min 0 :max 20}]]
   [:kinder-bis-6             [:int {:min 0 :max 20}]]
   [:haustierzahl             [:int {:min 0 :max 10}]]
   [:wohnungsid               [:int {:min 1}]]
   [:wohnungsname             [:string {:min 1 :max 256}]]
   [:gast                     gast]
   [:datenschutz-gelesen?     [:= true]]
   [:mietbedingungen-gelesen? [:= true]]
   [:zusatznachricht          {:optional true} [:string {:max 2048}]]
   ;; The client's own price calculation. Ignored by the server (which
   ;; recomputes it), only compared for logging.
   [:preis                    {:optional true} :any]
   ;; Honeypot: a field humans never fill. Non-empty → silently dropped.
   [:website                  {:optional true} :any]])

(def ^:private validator (m/validator payload))
(def ^:private explainer (m/explainer payload))

(defn valid? [data] (validator data))

(defn invalid-fields
  "Nil if `data` matches `payload`, else the distinct paths (vectors of
   keys, e.g. `[:gast :email]`) of the offending values, in schema order."
  [data]
  (when-let [{:keys [errors]} (explainer data)]
    (->> errors (map :in) distinct vec)))
