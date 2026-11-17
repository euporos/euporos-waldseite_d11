(ns buchung.verfuegbarkeit
  "Availability and date rules of a stay, shared by the booking SPA and the
   server: which picker days are blocked, which are selectable, and whether a
   chosen stay is acceptable.

   Same rules as the legacy `legacy.buchung.datepicker` (`handovers`, `blocked?`) and
   `legacy.buchung.datechecking/err-dates-bad?` (frozen copies under
   test/cljs/legacy/buchung/; rules and quirks in
   doc/frontend-refactor-quirks.md), except:
   - nights are counted as **calendar days**, so the minimum-stay check is no
     longer one night short across the spring DST switch (owner decision
     2026-11-17);
   - the time-zone artefacts of the legacy code are resolved the way
     doc/frontend-refactor-quirks.md \"Time zone\" describes.

   Pure: dates are ISO days \"yyyy-MM-dd\", results are data (keywords +
   params); only views localize."
  (:require [cljs-time.coerce :as t.coerce]
            [kalender.core :as kal]))

;; ---------------------------------------------------------------------------
;; Occupancy (iCal vevents)

(def ^:private ms-per-hour 3600000)

(defn- parse-ms
  "Epoch ms of an iCal date value as legacy `parse-iso` reads it
   (`cljs-time.coerce/from-string`; an ISO day is UTC midnight), nil if
   unparseable. Plain ISO days take a fast path — `from-string` tries its
   formatters by throwing, which is slow."
  [s]
  (when (and (string? s) (seq s))
    (if (kal/valid-iso? s)
      (.getTime (kal/iso->date s))
      (some-> (t.coerce/from-string s) .getTime))))

(defn- ms->iso [ms]
  (subs (.toISOString (js/Date. ms)) 0 10))

(defn prepare-belegung
  "Server-parsed vevents `[{:dtstart s :dtend s …}]` (ISO date or date-time
   strings) → the same maps with `:dtstart`/`:dtend` as ISO days.
   As legacy `rework-wohnungen`: vevents with an unparseable date are dropped,
   and so are vevents of ≤ 47 h (single nights) — measured on the parsed
   instants, before truncating them to their UTC day."
  [vevents]
  (->> vevents
       (keep (fn [{:keys [dtstart dtend] :as v}]
               (let [m1 (parse-ms dtstart)
                     m2 (parse-ms dtend)]
                 (when (and m1 m2
                            ;; t/in-hours: whole hours, truncated
                            (> (quot (- m2 m1) ms-per-hour) 47))
                   (assoc v :dtstart (ms->iso m1) :dtend (ms->iso m2))))))
       vec))

(defn handover-days
  "Days that are one booking's departure and the next one's arrival
   (after sorting by arrival, stable). Neither arrival nor departure is
   possible there. Returns a set of ISO days."
  [belegung]
  (->> belegung
       (sort-by :dtstart)
       (partition 2 1)
       (keep (fn [[a b]] (when (= (:dtend a) (:dtstart b)) (:dtend a))))
       set))

(defn day-blocked?
  "True if `tag` (ISO) cannot be picked: a handover day, a **Sunday** that is
   a booking's arrival or departure day, or a day strictly inside a booking.
   Plain arrival and departure days on other weekdays are free.
   `handovers` defaults to `(handover-days belegung)`; pass it when checking
   many days."
  ([tag belegung] (day-blocked? tag belegung (handover-days belegung)))
  ([tag belegung handovers]
   (boolean
    (or (contains? handovers tag)
        (let [sunday? (= 7 (kal/weekday tag))]
          (some (fn [{:keys [dtstart dtend]}]
                  (or (and sunday? (or (= tag dtstart) (= tag dtend)))
                      (and (neg? (compare dtstart tag)) (neg? (compare tag dtend)))))
                belegung))))))

(defn day-selectable?
  "True if `tag` can be picked: not before `today-iso` (the caller's local
   calendar date, `kalender.core/today-iso`) and not blocked."
  ([tag today-iso belegung] (day-selectable? tag today-iso belegung (handover-days belegung)))
  ([tag today-iso belegung handovers]
   (and (not (neg? (compare tag today-iso)))
        (not (day-blocked? tag belegung handovers)))))

;; ---------------------------------------------------------------------------
;; Stay checks

(defn- belegt?
  "Some booking shares a night with the stay: a booking blocks the nights
   [dtstart, dtend), so arriving on a departure day and departing on an
   arrival day are fine.

   Legacy `err-contains-blocked?` intersects the stay with
   `[dtstart, dtend − 1 day]` via `t/overlap`, which ignores touching
   intervals: on UTC dates, arriving on a booking's **last** night slipped
   through. On the Berlin-local dates the legacy picker produced (the
   behaviour guests saw) it was blocked, and the picker blocks that day
   anyway; the new rule blocks it too (see the quirks doc)."
  [anreise abreise belegung]
  (some (fn [{:keys [dtstart dtend]}]
          (and (neg? (compare dtstart abreise))
               (neg? (compare anreise dtend))))
        belegung))

(defn- season-overlaps?
  "Legacy `t/overlaps?` of the stay with `[beginn, ende]` on UTC dates:
   strict, touching does not count — a stay ending on a season's `beginn` or
   starting on its `ende` day ignores that season. A season with a missing
   or inverted date range never overlaps (legacy would throw)."
  [anreise abreise {:keys [beginn ende]}]
  (let [b (kal/date->iso beginn)
        e (kal/date->iso ende)]
    (and b e (not (pos? (compare b e)))
         (neg? (compare b abreise))
         (neg? (compare anreise e)))))

(defn mindestaufenthalt
  "Minimum number of nights for a stay in wohnung `wohnungsid`: the minimum
   `mindestaufenthalt` over all seasons overlapping the stay, else the
   wohnung's `mindestaufenthalt_standard`. Values go through `js/parseInt`
   like legacy (so a non-numeric value yields NaN, which never triggers).
   `preise` normalized (`preise.model/normalize`)."
  [{:keys [anreise abreise wohnungsid]} preise]
  (let [saisonal (->> (:saisonen preise)
                      (filter #(season-overlaps? anreise abreise %))
                      (map #(get (:felder %) wohnungsid))
                      (keep #(some-> (:mindestaufenthalt %) js/parseInt))
                      seq)]
    (or (when saisonal (apply min saisonal))
        (some-> (get-in preise [:basisdaten wohnungsid :mindestaufenthalt_standard])
                js/parseInt))))

(defn stay-error
  "Nil if the stay is acceptable, else the first problem as data, in legacy
   precedence:
   `{:error :anreise-nach-abreise}` — departure not after arrival;
   `{:error :belegt}`               — shares a night with a booking;
   `{:error :mindestaufenthalt :min n :wohnung name}` — too short.
   Nothing is checked unless both dates are set.

   `anfrage` `{:anreise iso :abreise iso :wohnungsid id}`; `wohnung`
   `{:name s :belegung prepared}` (`prepare-belegung`; `:name` may be nil,
   it is only carried into the error); `preise` normalized."
  [{:keys [anreise abreise] :as anfrage} wohnung preise]
  (when (and anreise abreise)
    (cond
      (not (pos? (kal/nights anreise abreise)))
      {:error :anreise-nach-abreise}

      (belegt? anreise abreise (:belegung wohnung))
      {:error :belegt}

      :else
      (let [n (mindestaufenthalt anfrage preise)]
        (when (and n (< (kal/nights anreise abreise) n))
          {:error :mindestaufenthalt :min n :wohnung (:name wohnung)})))))
