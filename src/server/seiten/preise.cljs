(ns seiten.preise
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.util.response :as r])
  (:require-macros [hiccups.core :refer [html5]]))

(defhandler handler [req]
  (-> (r/ok
       (html5
        [:head
         [:meta {:charset "utf-8"}]
         [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
         [:meta {:name "robots" :content "noindex"}]
         [:title "Preisstruktur"]
         [:link {:rel "stylesheet" :href "/compiled/bundle/preise.css"}]]
        [:body
         [:div.afg {:id "ifg" :token (:af-token req)}]
         [:h1.display-1 "Preisstruktur"]
         [:div#mainframe]
         [:script {:src "/compiled/admin/preise.js"}]]))
      (r/content-type "text/html; charset=utf-8")))
