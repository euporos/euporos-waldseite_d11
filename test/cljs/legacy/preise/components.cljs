;; FROZEN LEGACY ORACLE (roadmap step 4). Verbatim excerpt of src/browser/preise/components.cljs @ ede36db:
;; only the ns name, defn- -> defn, ^:private dropped and requires re-pointed
;; to the legacy copies. Never edit the logic below — it defines "old behaviour".
(ns legacy.preise.components
  "Pure functions of the legacy preise.components (season interval/overlap
   checks, next-saison, copy-shift helpers)."
  (:require
   [cljs-time.core :as tm]
   [legacy.preise.util :as u]))

(defn interval-sn
  [saison]
  (if (and (tm/date? (:beginn saison))
           (tm/date? (:ende saison))
           (tm/before? (:beginn saison) (:ende saison)))
    (tm/interval (:beginn saison) (:ende saison))
    (do (js/console.warn (str "Cannot compute interval from dates: " saison))
        (tm/interval (tm/now) (tm/now)))))

(defn seasons-overlap?
  [s1 s2]
  (or (tm/= (:beginn s1) (:ende s2))
      (tm/= (:beginn s2) (:ende s1))
      (tm/overlaps? (interval-sn s1) (interval-sn s2))))

(defn saison-overlaps?
  [checksaison saisonen]
  (reduce (fn [sofar saison] (or sofar (seasons-overlap? checksaison saison)))
          false
          (filter #(not= % checksaison) saisonen)))

(defn next-saison
  [saisonen]
  (let [beginn (if (seq saisonen)
                 (-> (sort tm/before? (map :ende saisonen))
                     (last)
                     (tm/plus- (tm/days 1)))
                 (tm/today))]
    {:beginn beginn :ende (tm/plus- beginn (tm/weeks 2))}))


(defn shift-saison-by-years
  [years saison]
  (u/update-multiple saison [:beginn :ende] #(tm/plus- % (tm/years years))))

(defn shift-saisonen-by-one-year
  [saisonen]
  (map (partial shift-saison-by-years 1) (filter :selected saisonen)))

(defn unselect-all-saisonen
  [saisonen]
  (map #(assoc % :selected false) saisonen))
