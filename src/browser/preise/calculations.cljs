(ns preise.calculations
  (:require [preise.util :as u]))

(defn gewichte-preise
  [saisonen wohnungs-id tag-or-woche]
  (apply /
         (reduce
          (fn [acc saison]
            (let [preis       (get-in saison [:felder wohnungs-id tag-or-woche])
                  anzahl-tage (u/count-days (:beginn saison) (:ende saison))]
              (if (and preis (> preis 0))
                [(+ (first acc) (* anzahl-tage preis))
                 (+ (second acc) anzahl-tage)]
                acc)))
          [0 0]
          saisonen)))
