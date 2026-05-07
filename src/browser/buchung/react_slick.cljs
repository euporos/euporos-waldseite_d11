(ns buchung.react-slick
  "Apartment carousel. Shows one apartment per slide; selecting a slide
   updates the surrounding state."
  (:require ["react-slick" :as react-slick-slider]
            [reagent.core :as r]))

(def ^:private slider
  (r/adapt-react-class (or (.-default react-slick-slider) react-slick-slider)))

(defn- carousel-card [{:keys [id name hauptbild-url haus-name wohnung-url]}]
  [:div.card.has-text-centered.carousel-card {:key (str id)}
   [:div.card-image
    [:figure.image
     (when hauptbild-url
       [:img {:src hauptbild-url :alt name}])]]
   [:div.content.panel-background
    (if wohnung-url
      [:a {:href wohnung-url :target "_blank" :rel "noopener noreferrer"}
       [:strong name]]
      [:strong name])
    (when haus-name [:<> [:br] haus-name])]])

(defn- selected-slide-index [wohnungen ausgew-id]
  (or (some (fn [[i w]] (when (= ausgew-id (:id w)) i))
            (map-indexed vector wohnungen))
      0))

(defn wohnungspicker-stateful
  [_state-a wohnungen !ausgew-whg-id !ueberbelegung?]
  (let [!ref (atom nil)]
    (r/create-class
     {:reagent-render
      (fn [_state-a wohnungen !ausgew-whg-id _!ueberbelegung?]
        [slider
         {:dots           false
          :ref            #(reset! !ref %)
          :initialSlide   (selected-slide-index wohnungen @!ausgew-whg-id)
          :accessibility  true
          :speed          250
          :infinite       false
          :slidesToShow   1
          :slidesToScroll 1
          :afterChange    (fn [idx]
                            (reset! !ueberbelegung? nil)
                            (reset! !ausgew-whg-id (:id (nth wohnungen idx))))}
         (for [w wohnungen] (carousel-card w))])
      :component-did-update
      (fn [this]
        (let [[_ _ wohnungen !ausgew-whg-id] (r/argv this)]
          (when @!ref
            (.slickGoTo ^js @!ref (selected-slide-index wohnungen @!ausgew-whg-id)))))})))
