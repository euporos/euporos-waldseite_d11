(ns buchung.datepicker
  "Calendar widget. Renders a month, lets the user pick a day, marks
   blocked / past / contained-in-current-selection days. Ported from the
   old waldseite SPA."
  (:require [cljs-time.core :as t]
            [reagent.core :as r]
            [buchung.utils :as u]))

(defn- handovers
  "Same-day check-out / check-in days produced by adjacent vevents.
   These can't be picked as anreise OR abreise."
  [vevents]
  (->> vevents
       (sort-by :dtstart t/before?)
       (partition 2 1)
       (keep #(when (t/= (:dtend (first %)) (:dtstart (second %)))
                (:dtend (first %))))))

(defn- sunday-and-anabreise? [day vevent]
  (and (= 7 (t/day-of-week day))
       (or (t/= day (:dtend vevent))
           (t/= day (:dtstart vevent)))))

(defn- blocked? [day vevents handover-days]
  (or (some #(t/= day %) handover-days)
      (some (fn [vevent]
              (or (sunday-and-anabreise? day vevent)
                  ;; Abreisetage selbst sind frei
                  (and (t/before? day (:dtend vevent))
                       (t/after? day (:dtstart vevent)))))
            vevents)))

(defn- day-cell [selected-date opts handover-days day]
  (let [{:keys [counterdate belegung daterror]} opts
        d         (t/floor day t/day)
        is-blk?   (blocked? d belegung handover-days)
        past?     (t/before? day (t/today))
        selected? (and @selected-date (t/equal? day @selected-date))
        in-span?  (and @selected-date counterdate (not selected?) (not daterror)
                       (u/unordered-within? @selected-date counterdate day true))]
    [:td
     {:key      (str day)
      :class    (cond-> ["picker__cell" "picker__cell--day"]
                  past?     (conj "picker__cell--past")
                  is-blk?   (conj "picker__cell--blocked")
                  in-span?  (conj "picker__cell--contained")
                  (and (not past?) (not is-blk?) (not in-span?))
                  (conj "picker__cell--free")
                  selected? (conj "picker__cell--selected")
                  daterror  (conj "picker__cell--bad"))
      :on-click (when-not (or past? is-blk?)
                  #(reset! selected-date day))}
     (t/day day)]))

(defn- outside-cell [day]
  [:td.picker__cell.picker__cell--day.picker__cell--outside
   {:key (str day)} (t/day day)])

(defn- weekday-row []
  [:tr (for [w u/weekday-short-de] [:td.picker__cell.picker__cell--weekday {:key w} w])])

(defn- format-month [shown-date selected-date opts]
  (let [{:keys [belegung]} opts
        f-day      (t/first-day-of-the-month- shown-date)
        f-weekday  (dec (t/day-of-week f-day))
        days       (u/days-in-month shown-date)
        prev-tail  (reverse (take f-weekday (reverse (u/days-in-month (t/minus- shown-date (t/months 1))))))
        next-head  (u/days-in-month (t/plus- shown-date (t/months 1)))
        rows       6
        cols       7
        cells      (concat (map outside-cell prev-tail)
                           (map (partial day-cell selected-date opts (handovers belegung)) days)
                           (map outside-cell next-head))]
    [:table.picker__monthdays
     [:thead (weekday-row)]
     [:tbody
      (for [[i week] (map-indexed vector (partition cols (take (* cols rows) cells)))]
        ^{:key i} [:tr.picker__week week])]]))

(defn- nav-button [shown-date op unit label]
  [:button.picker__button
   {:on-click #(swap! shown-date (fn [d] (op d (unit 1))))}
   label])

(defn- header [shown-date]
  [:div.picker__head
   [:table
    [:tbody
     [:tr
      [:td (nav-button shown-date t/minus t/years  "⟨⟨")]
      [:td (nav-button shown-date t/minus t/months "⟨")]
      [:td.picker__indicators
       [:div.picker__indicator.picker__indicator--year (t/year @shown-date)]
       [:div.picker__indicator.picker__indicator--month (u/month-label-de @shown-date)]]
      [:td (nav-button shown-date t/plus t/months "⟩")]
      [:td (nav-button shown-date t/plus t/years  "⟩⟩")]]]]])

(defn datepicker [selected-date _opts]
  (let [shown-date (r/atom (or @selected-date (t/today)))]
    (fn [selected-date opts]
      [:div.picker
       (header shown-date)
       (format-month @shown-date selected-date opts)])))
