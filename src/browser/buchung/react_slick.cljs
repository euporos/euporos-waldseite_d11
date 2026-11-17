(ns buchung.react-slick
  "Apartment carousel. Shows one apartment per slide. Controlled from
   outside: receives the apartments and the selected id as props and
   reports a swipe through `on-select`; when the selected id changes from
   elsewhere (the dropdown) it scrolls to that slide. The only mutable
   thing is the handle on the slick instance."
  (:require ["react-slick" :as react-slick-slider]
            [reagent.core :as r]))

(def ^:private slider
  (r/adapt-react-class (or (.-default react-slick-slider) react-slick-slider)))

(defn- carousel-card [{:keys [id name hauptbild-url haus-name wohnung-url]}]
  [:div.card.has-text-centered.carousel-card {:key (str id) :data-test-id (str "wohnung-slide-" id)}
   [:div.card-image
    ;; is-4by3 forces a uniform aspect across slides so slick's
    ;; top:50% arrow positioning lands on the image instead of being
    ;; pulled down by a portrait-orientation slide.
    [:figure.image.is-4by3
     (when hauptbild-url
       [:img {:src hauptbild-url :alt name}])]]
   [:div.content.panel-background
    (if wohnung-url
      [:a {:href wohnung-url :target "_blank" :rel "noopener noreferrer"}
       [:strong name]]
      [:strong name])
    (when haus-name [:<> [:br] haus-name])]])

(defn- slide-index [wohnungen selected-id]
  (or (some (fn [[i w]] (when (= selected-id (:id w)) i))
            (map-indexed vector wohnungen))
      0))

(defn wohnungspicker
  "`props`: `:wohnungen` (vector of apartment maps), `:selected-id`,
   `:on-select` (fn [wohnungsid])."
  [_props]
  (let [!slick (atom nil)]
    (r/create-class
     {:display-name "wohnungspicker"
      :reagent-render
      (fn [{:keys [wohnungen selected-id on-select]}]
        [slider
         {:dots           false
          :ref            #(reset! !slick %)
          :initialSlide   (slide-index wohnungen selected-id)
          :accessibility  true
          :speed          250
          :infinite       false
          :slidesToShow   1
          :slidesToScroll 1
          :afterChange    (fn [idx]
                            (when-let [w (nth wohnungen idx nil)]
                              (on-select (:id w))))}
         (for [w wohnungen] (carousel-card w))])
      :component-did-update
      (fn [this [_ old-props]]
        (let [{:keys [wohnungen selected-id]} (r/props this)]
          (when (and @!slick (not= selected-id (:selected-id old-props)))
            (.slickGoTo ^js @!slick (slide-index wohnungen selected-id)))))})))
