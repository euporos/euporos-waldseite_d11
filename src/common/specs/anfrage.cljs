(ns specs.anfrage
  (:require [malli.core :as m]
            [specs.book :refer [email-regex]]))

(def date-regex #"^\d{4}-\d{2}-\d{2}$")

(def gast
  [:map
   [:vorname        [:string {:min 1 :max 256}]]
   [:nachname       [:string {:min 1 :max 256}]]
   [:email          [:re email-regex]]
   [:telefonnummer  [:string {:min 1 :max 256}]]])

(def payload
  [:map
   [:anreise                  [:re date-regex]]
   [:abreise                  [:re date-regex]]
   [:gaestezahl               [:int {:min 1 :max 20}]]
   [:haustierzahl             [:int {:min 0 :max 10}]]
   [:wohnungsid               [:int {:min 1}]]
   [:wohnungsname             [:string {:min 1 :max 256}]]
   [:gast                     gast]
   [:datenschutz-gelesen?     [:= true]]
   [:mietbedingungen-gelesen? [:= true]]
   [:zusatznachricht          {:optional true} [:string {:max 2048}]]
   [:preis                    {:optional true} [:map-of :keyword [:maybe :any]]]])

(defn valid? [data] (m/validate payload data))
