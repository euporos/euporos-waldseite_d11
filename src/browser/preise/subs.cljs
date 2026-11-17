(ns preise.subs
  "Subscriptions of the price admin. Derived data (sorted seasons, season
   errors, weighted averages) is computed here from the raw app-db."
  (:require [preise.calculations :as calc]
            [preise.db :as db]
            [re-frame.core :as rf]))

(rf/reg-sub ::status       (fn [db _] (:status db)))
(rf/reg-sub ::heute        (fn [db _] (:heute db)))
(rf/reg-sub ::wohnungen    (fn [db _] (:wohnungen db)))
(rf/reg-sub ::daten        (fn [db _] (:daten db)))
(rf/reg-sub ::selected     (fn [db _] (get-in db [:ui :selected])))
(rf/reg-sub ::save-state   (fn [db _] (get-in db [:ui :save-state])))
(rf/reg-sub ::trash-armed  (fn [db _] (get-in db [:ui :trash-armed])))
(rf/reg-sub ::datum-fehler (fn [db _] (get-in db [:ui :datum-fehler])))

(rf/reg-sub ::allgdaten
  :<- [::daten]
  (fn [d _] (:allgdaten d)))

(rf/reg-sub ::basisdaten
  :<- [::daten]
  (fn [d _] (:basisdaten d)))

(rf/reg-sub ::saisonen
  :<- [::daten]
  (fn [d _] (:saisonen d)))

(rf/reg-sub ::sortierte-saisonen
  :<- [::saisonen]
  (fn [ss _] (db/sortierte-saisonen ss)))

(rf/reg-sub ::saison-fehler
  ;; {id {:dates-bad? bool :overlaps? bool}} for seasons with a problem.
  :<- [::saisonen]
  :<- [::heute]
  (fn [[ss heute] _] (db/saison-fehler ss heute)))

(rf/reg-sub ::durchschnittspreise
  ;; {wohnungs-id {:preis_tag x :preis_woche y}}; NaN where no season has a
  ;; positive value (legacy).
  :<- [::saisonen]
  :<- [::wohnungen]
  (fn [[ss wohnungen] _]
    (into (sorted-map)
          (for [id (keys wohnungen)]
            [id {:preis_tag   (calc/gewichte-preise ss id :preis_tag)
                 :preis_woche (calc/gewichte-preise ss id :preis_woche)}]))))
