;; FROZEN LEGACY ORACLE (roadmap step 4). Verbatim excerpt of src/browser/buchung/datepicker.cljs @ ede36db:
;; only the ns name, defn- -> defn, ^:private dropped and requires re-pointed
;; to the legacy copies. Never edit the logic below — it defines "old behaviour".
(ns legacy.buchung.datepicker
  "Pure parts (handovers, sunday-and-anabreise?, blocked?) of the legacy
   buchung.datepicker calendar widget."
  (:require [cljs-time.core :as t]))

(defn handovers
  "Same-day check-out / check-in days produced by adjacent vevents.
   These can't be picked as anreise OR abreise."
  [vevents]
  (->> vevents
       (sort-by :dtstart t/before?)
       (partition 2 1)
       (keep #(when (t/= (:dtend (first %)) (:dtstart (second %)))
                (:dtend (first %))))))

(defn sunday-and-anabreise? [day vevent]
  (and (= 7 (t/day-of-week day))
       (or (t/= day (:dtend vevent))
           (t/= day (:dtstart vevent)))))

(defn blocked? [day vevents handover-days]
  (or (some #(t/= day %) handover-days)
      (some (fn [vevent]
              (or (sunday-and-anabreise? day vevent)
                  ;; Abreisetage selbst sind frei
                  (and (t/before? day (:dtend vevent))
                       (t/after? day (:dtstart vevent)))))
            vevents)))
