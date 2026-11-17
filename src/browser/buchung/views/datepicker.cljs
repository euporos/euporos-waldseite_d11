(ns buchung.views.datepicker
  "Calendar widget: one month, pick a day. Pure — everything comes in as
   props (the `buchung.subs/::picker` model plus callbacks); no atoms, no
   subscriptions. DOM and classes as the legacy `legacy.buchung.datepicker`, so the
   `picker__*` styles keep applying."
  (:require [buchung.views.texte :as tx]))

(defn- nav-button [test-id label on-click]
  [:button.picker__button
   {:type "button" :data-test-id test-id :on-click on-click}
   label])

(defn- header [{:keys [locale year month on-shift test-id]}]
  [:div.picker__head
   [:table
    [:tbody
     [:tr
      [:td [nav-button (str test-id "-jahr-zurueck")  "⟨⟨" #(on-shift :year -1)]]
      [:td [nav-button (str test-id "-monat-zurueck") "⟨"  #(on-shift :month -1)]]
      [:td.picker__indicators
       [:div.picker__indicator.picker__indicator--year year]
       [:div.picker__indicator.picker__indicator--month (tx/month-label locale month)]]
      [:td [nav-button (str test-id "-monat-vor") "⟩"  #(on-shift :month 1)]]
      [:td [nav-button (str test-id "-jahr-vor")  "⟩⟩" #(on-shift :year 1)]]]]]])

(defn- day-cell [on-pick test-id {:keys [iso tag outside? selectable? classes]}]
  (if outside?
    [:td {:key iso :class classes} tag]
    [:td {:key          iso
          :class        classes
          :data-test-id (str test-id "-tag-" iso)
          :on-click     (when selectable? #(on-pick iso))}
     tag]))

(defn datepicker
  "`props`: the picker model (`:year :month :weeks`, cells as built by
   `buchung.subs/cell`), `:locale`, `:on-pick` (fn [iso]), `:on-shift`
   (fn [unit n], unit `:month`/`:year`) and a `:test-id` prefix."
  [{:keys [locale weeks on-pick test-id] :as props}]
  [:div.picker {:data-test-id test-id}
   [header props]
   [:table.picker__monthdays
    [:thead
     [:tr (for [w (tx/weekday-short locale)]
            [:td.picker__cell.picker__cell--weekday {:key w} w])]]
    [:tbody
     (for [[i week] (map-indexed vector weeks)]
       ^{:key i} [:tr.picker__week (map (partial day-cell on-pick test-id) week)])]]])
