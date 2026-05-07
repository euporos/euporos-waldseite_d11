(ns buchung.preisberechnung
  "Computes the total stay price from the cached preise structure.
   Matches the legacy waldseite logic: per-night base or season day/week
   rate, plus extra-guest, pet, cleaning, and energy surcharges. Returns
   nil if `aufenthalt` is missing."
  (:require [cljs-time.core :as t]
            [buchung.utils :as u]))

(defn- season-rate-for-day
  "Picks the applicable season for `tag` and returns its day rate (or
   week-rate / 7 when the stay qualifies for the weekly tariff)."
  [tag saisonen woche? whg-id]
  (when-let [s (first (filter #(u/within-left? (:beginn %) (:ende %) tag) saisonen))]
    (let [field (get-in s [:felder whg-id])
          val   (if woche?
                  (some-> (:preis_woche field) (/ 7))
                  (:preis_tag field))]
      (when (and val (> val 10)) val))))

(defn- per-day-cost [tag saisonen woche? whg-id grundpreis]
  (or (season-rate-for-day tag saisonen woche? whg-id)
      grundpreis))

(defn berechnen
  [aufenthalt gaestezahl haustierzahl whg-id preise]
  (when aufenthalt
    (let [{:keys [reinigung-unter gaestebeitrag
                  aufschlag_zus_person aufschlag_haustier
                  energieaufschlag]} (:allgdaten preise)
          basis           (get-in preise [:basisdaten whg-id])
          {:keys [standardbelegung grundpreis reinigung]} basis
          tageszahl       (t/in-days aufenthalt)
          woche?          (>= tageszahl 7)
          days            (u/days-in-interval (:start aufenthalt) (:end aufenthalt))
          einzeltage-summe (reduce + 0 (map #(per-day-cost % (:saisonen preise) woche? whg-id grundpreis) days))
          belegungsaufschlag
          (* tageszahl
             (max 0 (- gaestezahl (or standardbelegung 0)))
             (or aufschlag_zus_person 0))
          haustieraufschlag
          (* tageszahl haustierzahl (or aufschlag_haustier 0))
          reinigungsaufschlag
          (if (and reinigung-unter (< tageszahl reinigung-unter))
            (or reinigung 0) 0)
          gesamtsumme
          (+ einzeltage-summe belegungsaufschlag reinigungsaufschlag haustieraufschlag)
          gaestebeitrag
          (* tageszahl gaestezahl (js/parseFloat (or gaestebeitrag 0)))
          energieaufschlag-total
          (-> (or energieaufschlag 0) float
              (* tageszahl gaestezahl)
              Math/round)]
      {:tageszahl            tageszahl
       :gaestezahl           gaestezahl
       :einzeltage-summe     einzeltage-summe
       :belegungsaufschlag   belegungsaufschlag
       :haustieraufschlag    haustieraufschlag
       :reinigungsaufschlag  reinigungsaufschlag
       :gaestebeitrag        (.round js/Math gaestebeitrag)
       :energieaufschlag     energieaufschlag-total
       :gesamtsumme          (.round js/Math gesamtsumme)})))
