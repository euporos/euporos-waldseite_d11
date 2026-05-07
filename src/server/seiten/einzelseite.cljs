(ns seiten.einzelseite
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [psite-hiccup.core :as ph]
            [db.setup :as db]
            [db.queries :as q]
            [seiten.templates :as templates]))

(defn- page-body [{:keys [titel text]}]
  [:section
   [:div.panel.mainpanel
    [:div.textabschnitt.py-4.px-4
     [:h1.title.is-2.block titel]
     [:div.content (ph/dangerous-html (or text ""))]]]])

(defhandler handler [req]
  (p/let [locale  (:locale req)
          raw-id  (-> req :path-params :einzelseitid)
          es-id   (if (string? raw-id) (js/parseInt raw-id 10) raw-id)
          page    (-> (db/query (q/einzelseite-detail locale es-id)) (.then first))]
    (templates/render-page
     req
     {:titel        (:titel page)
      :beschreibung (:meta_description page)}
     (page-body page))))
