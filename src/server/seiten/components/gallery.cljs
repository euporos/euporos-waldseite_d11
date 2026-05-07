(ns seiten.components.gallery
  (:require [directus.core :as d]))

(defn bilder-gallery
  "Hauptbild + weitere Bilder als responsives Thumbnail-Grid; Klick öffnet
   PhotoSwipe (siehe app.gallery). `bilder` ist eine Sequenz von
   directus_files-IDs (Strings); falsy Einträge werden übersprungen."
  [dom-id bilder]
  [:div.gallery.columns.is-multiline.is-centered
   {:id dom-id :data-gallery true}
   (for [bild bilder
         :when bild]
     [:div.column.is-half-tablet.is-one-third-desktop
      [:a {:href             (d/image-by-preset "1600" bild)
           :data-pswp-width  1600
           :data-pswp-height 1067
           :target           "_blank"
           :rel              "noreferrer"}
       [:figure.image
        [:img {:src     (d/image-by-preset "600" bild)
               :loading "lazy"
               :alt     ""}]]]])])
