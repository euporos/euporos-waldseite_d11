(ns specs.kontakt
  "Schema of the no-JS contact form (POST /:locale/kontaktform), checked on
   the server by `kontakt.pruefung`. Values arrive trimmed (see
   `kontakt.pruefung/formular-werte`)."
  (:require [malli.core :as m]
            [specs.common :refer [email-regex]]))

(def max-name 256)
(def max-email 256)
(def max-nachricht 5000)

(def formular
  [:map
   [:name             [:string {:min 1 :max max-name}]]
   [:email            [:and [:string {:max max-email}] [:re email-regex]]]
   [:kontaktnachricht [:string {:min 1 :max max-nachricht}]]
   [:datenschutz?     [:= true]]])

(def ^:private explainer (m/explainer formular))

(defn fehler-felder
  "Set of the top-level keys of `form` that violate the schema (empty when
   valid). A non-map yields all keys."
  [form]
  (if-not (map? form)
    #{:name :email :kontaktnachricht :datenschutz?}
    (into #{}
          (keep (fn [{:keys [in path]}] (first (or (seq in) path))))
          (:errors (explainer form)))))

(defn valid? [form] (empty? (fehler-felder form)))
