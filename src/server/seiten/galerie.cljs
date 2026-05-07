(ns seiten.galerie
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [db.setup :as db]
            [db.queries :as q]
            [seiten.templates :as templates]
            [directus.core :as d]))

(defn- thumb [{:keys [bild beschreibung width height]}]
  [:a.galerie-item
   {:href             (d/image-by-preset "1200" bild)
    :data-pswp-width  (or width 1920)
    :data-pswp-height (or height 1280)
    :target           "_blank"
    :rel              "noreferrer"}
   [:img {:src     (d/image-by-preset "600" bild)
          :alt     (or beschreibung "")
          :loading "lazy"}]])

(defn- gruppe [[haus-name bilder]]
  [:div.galerie-gruppe
   (when haus-name
     [:h2.title.is-3.has-text-centered haus-name])
   [:div.galerie-grid {:data-photoswipe-gallery true}
    (map thumb bilder)]])

(defn- page-body [bilder]
  [:section
   [:div.panel.is-primary.mainpanel
    [:div.block.py-4.px-4
     [:h1.title.is-2.has-text-centered "Galerie"]
     (->> bilder
          (group-by :haus_name)
          (sort-by (fn [[k _]] (or k "")))
          (map gruppe))]]])

(defhandler handler [req]
  (p/let [locale (:locale req)
          bilder (db/query (q/galerie-overview locale))]
    (templates/render-page
     req
     {:titel        "Galerie — Bickels Ferienwohnungen"
      :beschreibung "Bildergalerie unserer Ferienhäuser im Bayerischen Wald."}
     (page-body bilder))))
