;; FROZEN LEGACY ORACLE (roadmap step 4). Verbatim excerpt of src/browser/preise/calculations.cljs @ ede36db:
;; only the ns name, defn- -> defn, ^:private dropped and requires re-pointed
;; to the legacy copies. Never edit the logic below — it defines "old behaviour".
(ns legacy.preise.calculations
  (:require [legacy.preise.util :as u]))

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
