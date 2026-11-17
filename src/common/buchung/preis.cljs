(ns buchung.preis
  "The stay price calculation, shared by the booking SPA and the server.

   Same rules, map shape and numeric results as the legacy
   `legacy.buchung.preisberechnung/berechnen` (frozen copy:
   test/cljs/legacy/buchung/preisberechnung.cljs; rules and quirks in
   doc/frontend-refactor-quirks.md), with one sanctioned difference: nights
   are counted as **calendar days**, so a stay across the spring DST switch
   is no longer one night short (owner decision 2026-11-17).

   Pure: no locale, no cljs-time types in the signature. Dates are ISO days
   \"yyyy-MM-dd\"; `preise` is the output of `preise.model/normalize`."
  (:require [kalender.core :as kal]))

(defn- in-season?
  "Legacy `within-left?` on UTC dates: `beginn` and `ende` both inclusive
   (`t/within?` is `(= start d)` or strictly between, plus `(= end d)`).
   ISO days compare lexicographically. A season with an unparseable date
   never matches."
  [beginn ende tag]
  (and beginn ende
       (or (= beginn tag)
           (and (neg? (compare beginn tag)) (neg? (compare tag ende)))
           (= ende tag))))

(defn- season-rate-for-day
  "Rate of the first season covering `tag` (vector order wins): `preis_tag`,
   or `preis_woche / 7` for the weekly tariff. Only a value `> 10` counts.
   The arithmetic is kept in the legacy form on purpose: an empty
   `preis_woche` \"\" survives normalisation and `(\"\" / 7)` is 0 in JS."
  [tag saisonen woche? whg-id]
  (when-let [s (first (filter #(in-season? (:beginn-iso %) (:ende-iso %) tag) saisonen))]
    (let [field (get-in s [:felder whg-id])
          val   (if woche?
                  (some-> (:preis_woche field) (/ 7))
                  (:preis_tag field))]
      (when (and val (> val 10)) val))))

(defn- per-day-cost [tag saisonen woche? whg-id grundpreis]
  (or (season-rate-for-day tag saisonen woche? whg-id)
      grundpreis))

(defn- saisonen-with-iso [saisonen]
  (mapv #(assoc % :beginn-iso (kal/date->iso (:beginn %))
                  :ende-iso   (kal/date->iso (:ende %)))
        saisonen))

(defn berechnen
  "Price of a stay. `anfrage` is
   `{:anreise iso :abreise iso :gaestezahl n :haustierzahl n :wohnungsid id}`,
   `preise` normalized (`preise.model/normalize`). Returns
   `{:tageszahl :gaestezahl :einzeltage-summe :belegungsaufschlag
     :haustieraufschlag :reinigungsaufschlag :gaestebeitrag
     :energieaufschlag :gesamtsumme}` — values and rounding as legacy
   (`:einzeltage-summe` unrounded, levies and total `Math.round`ed, levies
   not part of the total). Nil when a date is missing/invalid or the
   departure is not after the arrival."
  [{:keys [anreise abreise gaestezahl haustierzahl wohnungsid]} preise]
  (when (and (kal/valid-iso? anreise) (kal/valid-iso? abreise)
             (pos? (kal/nights anreise abreise)))
    (let [{:keys [reinigung-unter gaestebeitrag
                  aufschlag_zus_person aufschlag_haustier
                  energieaufschlag]} (:allgdaten preise)
          {:keys [standardbelegung grundpreis reinigung]}
          (get-in preise [:basisdaten wohnungsid])
          saisonen         (saisonen-with-iso (:saisonen preise))
          tageszahl        (kal/nights anreise abreise)
          woche?           (>= tageszahl 7)
          einzeltage-summe (reduce + 0 (map #(per-day-cost % saisonen woche? wohnungsid grundpreis)
                                            (kal/days-between anreise abreise)))
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
          gaestebeitrag-total
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
       :gaestebeitrag        (.round js/Math gaestebeitrag-total)
       :energieaufschlag     energieaufschlag-total
       :gesamtsumme          (.round js/Math gesamtsumme)})))
