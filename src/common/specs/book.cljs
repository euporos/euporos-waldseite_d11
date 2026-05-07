(ns specs.book
  (:require
   [malli.core :as m]))

(def email-regex #"^(([^<>()\[\]\\.,;:\s@\"]+(\.[^<>()\[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$")

(def message-max-length 500)

(def payload
  [:map
   [:language [:enum :de :en :uk]]
   [:concert [:int {:min 1}]]
   [:email [:re email-regex]]
   [:name [:string {:min 3, :max 70}]]
   [:attendees [:int {:min 1 :max 10}]]
   [:privacy-accepted? [:= true]]
   [:message {:optional true} [:string {:min 0, :max message-max-length}]]])

#_(m/validate payload {:language :de
                       :concert 1
                       :email "spike@olivermotz.com"
                       :name "Spike"
                       :attendees 2
                       :privacy-accepted? true})
