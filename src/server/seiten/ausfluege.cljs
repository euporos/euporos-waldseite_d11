(ns seiten.ausfluege
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [clojure.string :as str]
            [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [psite-hiccup.core :as ph]
            [db.setup :as db]
            [db.queries :as q]
            [seiten.templates :as templates]
            [directus.core :as d]))

(defn- format-ausflug [{:keys [id bild titel beschreibung]}]
  [:div.ausflug.block
   [:div.columns
    {:id (str "ausflug-" id)}
    (when bild
      [:div.column.is-one-third
       [:img {:src (d/image-by-preset "1024" bild)}]])
    [:div.column
     [:div.textabschnitt
      [:h2.title.is-3 titel]
      [:div.content (ph/dangerous-html (or beschreibung ""))]]]]])

(defn- ueberschrift [haus]
  (str "Ausflugtips"
       (when haus (str " für " (str/lower-case (:name haus))))))

(defn- page-body [haus ausfluege]
  [:section
   [:div.panel.is-primary.mainpanel
    [:div.panel.py-4.px-4
     [:h2.title.is-2.has-text-centered (ueberschrift haus)]
     (map format-ausflug ausfluege)]]])

(defhandler handler [req]
  (p/let [locale  (:locale req)
          raw-id  (-> req :path-params :hausid)
          haus-id (when (and raw-id (re-matches #"\d+" raw-id))
                    (js/parseInt raw-id 10))
          haus    (when haus-id
                    (-> (db/query (q/haus-detail locale haus-id)) (.then first)))
          ausfluege (db/query
                     (if haus-id
                       (q/ausfluege-by-haus locale haus-id)
                       (q/ausfluege-overview locale)))]
    (templates/render-page
     req
     {:titel        (ueberschrift haus)
      :beschreibung "Ausflugtips rund um den Bayerischen Wald."}
     (page-body haus ausfluege))))
