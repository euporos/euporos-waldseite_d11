(ns seiten.components.common
  (:require             [cljs-time.coerce :as time.coerce]
                        [comp.snippets :as snip]
                        [directus.core :as d]
                        [garden.core :as garden]
                        [psite-routing.core :as routing]
                        [comp.localization :as loc]
                        [psite-datetime.core :as td]))

(defn musician-card [{:keys [locale] :as req} {:keys [card_crop name id image bio_short subtitle]}]
  [:div.column.is-half-tablet
   [:a
    {:href (routing/reverse-match req :musician {:musician-id id})}
    [:div.card.is-fullheight
     [:div.card-image
      [:figure.image.is-3by2
       [:img
        {:style (garden/style {:object-fit "cover"
                               :margin-top (str (- card_crop) "px")
                               :height "auto"
                               :object-position "top"})

         :src (d/image-by-preset 600 image)
         :alt (str name ", " subtitle)}]]]
     [:div.card-content
      [:div.media
       [:div.has-text-centered
        {:style "width: 100%;"}
        [:p.title.is-4 name]
        [:p.subtitle.is-6 subtitle]]]
      [:div.content.has-text-justified
       {:style (garden/style {:color "white"})}
       bio_short]
      [:div
       {:style (garden/style {:height "20px"})}]
      [:div.columns.is-mobile
       {:style (garden/style {:position "absolute"
                              :width "100%"
                              :bottom "25px"})}
       [:div.column]
       [:div.column.has-text-right
        [:a
         {:style (garden/style {:margin-right "24px"
                                :color ""})
          :href (routing/reverse-match req :musician {:musician-id id})}
         (snip/mehrerfahren locale)]]]]]]])

(defn musicians-c [req title musicians]
  [:section.section
   [:div.container
    [:h2#musicians.title.has-text-centered title]
    [:div.columns.is-centered.is-multiline
     (map (partial musician-card req) musicians)]]])

(defn concert-card [{:keys [locale] :as req} {:keys [id
                                                     title
                                                     venue_city
                                                     datetime
                                                     venue_image
                                                     image
                                                     description_short
                                                     sold_out
                                                     supplemented_concert_id
                                                     past]
                                              :as _concert}]
  (let [image (or image venue_image)]
    [:div.column.is-half-tablet
     [:a
      {:href (routing/reverse-match req :concert {:concert-id id})}
      [:div.card.is-fullheight
       {:style (garden/style {:filter (when (or sold_out #_past) "grayscale(100%)")
                              :position "relative"
                              :overflow "hidden"})}
       (let [badge (cond
                     past (snip/past-event locale)
                     sold_out (snip/fully-booked locale)
                     supplemented_concert_id (snip/extra-concert locale)
                     :else nil)]
         (when badge
           [:div
            {:style (garden/style {:position "absolute"
                                   :z-index 1
                                   :transform "rotate(-45deg)"
                                   :left "-120px"
                                   :top "60px"
                                   :background-color "black"
                                   :text-align "center"
                                   :width "400px"
                                   :font-size "2rem"})}
            badge]))
       (when image
         [:div.card-image
          [:figure.image.is-3by2
           [:img
            {:style (garden/style {:object-fit "cover"
                                   :object-position "top"})
             :src (d/image-by-preset 600 image)
             :alt title}]]])
       [:div.card-content
        [:div.media
         [:div.media-content.has-text-centered
          [:p.title.is-4 title]
          [:p.subtitle.is-6
           (loc/by-locale locale (td/format-dates-wordy datetime 1)) " "
           (loc/by-locale :uk (td/format-times datetime))
           (when venue_city `(", " ~venue_city))]]]

        [:div.content.has-text-justified
         {:style (garden/style {:color "white"})}
         description_short]
        [:div
         {:style (garden/style {:height "20px"})}]
        [:div.columns.is-mobile
         {:style (garden/style {:position "absolute"
                                :width "100%"
                                :bottom "25px"})}
         [:div.column
          [:span (snip/mehrerfahren locale)]]
         [:div.column.has-text-right
          [:a
           {:style (garden/style {:margin-right "24px"
                                  :color ""})
            :href (str (routing/reverse-match
                        req :concert {:concert-id id}) "#book")}
           (snip/reserve-seats locale)]]]]]]]))

(defn concerts-c [req title concerts]
  [:section.section
   [:div.container
    [:h2#concerts.title.has-text-centered title]
    [:div.columns.is-centered.is-multiline
     (map (partial concert-card req) concerts)]]])

