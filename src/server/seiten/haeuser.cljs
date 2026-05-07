(ns seiten.haeuser
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [db.setup :as db]
            [db.queries :as q]
            [seiten.templates :as templates]
            [serving.routing :as rt]
            [directus.core :as d]))

(defn- haus-card [req {:keys [id name hauptbild]}]
  [:div.column.is-half-tablet.is-one-third-desktop
   [:a {:href (rt/path-haus req id name)}
    [:div.card.base-background
     [:div.card-image.card-image--hoverable
      [:figure.image.is-4by3
       [:img {:width "100%"
              :src   (d/image-by-preset "600" hauptbild)}]]]
     [:div.content.has-text-centered
      [:span.is-size-3 name]]]]])

(defn- page-body [req haeuser]
  [:section
   [:div.panel.is-primary.mainpanel
    [:div.py-4.px-4
     [:h1.title.is-2.has-text-centered "Unsere Ferienhäuser"]
     [:div.columns.is-multiline.is-centered
      (map (partial haus-card req) haeuser)]]]])

(defhandler handler [req]
  (p/let [locale  (:locale req)
          haeuser (db/query (q/haeuser-overview locale))]
    (templates/render-page
     req
     {:titel "Unsere Ferienhäuser — Bickels"
      :beschreibung "Übersicht unserer Ferienhäuser im Bayerischen Wald."}
     (page-body req haeuser))))
