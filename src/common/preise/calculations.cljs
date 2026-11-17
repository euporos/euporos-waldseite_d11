(ns preise.calculations
  "Weighted average prices of the price admin, shared (moved from
   src/browser/preise in roadmap step 12). Works on the on-disk season shape
   (ISO day strings, string-valued prices) as well as on date objects."
  (:require [kalender.core :as kal]))

(defn- als-zahl
  "JS numeric coercion of a field value, as the legacy code got implicitly
   from `*`/`>` on the raw strings: \"78\" → 78, \"\" → 0, junk → NaN."
  [v]
  (if (number? v) v (js/Number v)))

(defn gewichte-preise
  "Average of `tag-or-woche` (`:preis_tag`/`:preis_woche`) for wohnung
   `wohnungs-id`, weighted by season length in days (`ende` exclusive).
   Seasons without a positive value are skipped (\"\" counts as 0).
   Returns NaN (0/0) when no season qualifies — legacy behaviour, see
   doc/frontend-refactor-quirks.md."
  [saisonen wohnungs-id tag-or-woche]
  (let [[summe tage]
        (reduce
         (fn [[summe tage :as acc] saison]
           (let [preis       (get-in saison [:felder wohnungs-id tag-or-woche])
                 anzahl-tage (kal/nights (:beginn saison) (:ende saison))]
             (if (and preis (> (als-zahl preis) 0))
               [(+ summe (* anzahl-tage (als-zahl preis))) (+ tage anzahl-tage)]
               acc)))
         [0 0]
         saisonen)]
    (/ summe tage)))
