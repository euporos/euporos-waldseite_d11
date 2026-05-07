(ns seiten.components.gallery
  (:require [directus.core :as d]))

(defn bilder-gallery
  "Hauptbild + weitere Bilder als Splide-Karussell; Klick öffnet PhotoSwipe.
   Beide werden in js-src/index.js für jedes [data-gallery] aufgesetzt.
   `bilder` ist eine Sequenz von directus_files-IDs (Strings); falsy
   Einträge werden übersprungen."
  [dom-id bilder]
  [:div.gallery.splide
   {:id           dom-id
    :data-gallery true
    :aria-label   "Bildergalerie"}
   [:div.splide__track
    [:ul.splide__list
     (for [bild bilder
           :when bild]
       [:li.splide__slide
        [:a {:href             (d/image-by-preset "1600" bild)
             :data-pswp-width  1600
             :data-pswp-height 1067
             :target           "_blank"
             :rel              "noreferrer"}
         [:img {:data-splide-lazy (d/image-by-preset "1024" bild)
                :alt              ""}]]])]]])
