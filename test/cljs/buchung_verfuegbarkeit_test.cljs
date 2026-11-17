(ns buchung-verfuegbarkeit-test
  "Parity of the shared `buchung.verfuegbarkeit` with the frozen legacy
   date rules (roadmap step 7): picker blocking (`legacy.buchung.datepicker`)
   and the stay check (`legacy.buchung.datechecking/err-dates-bad?`).

   Oracle A: legacy fed UTC-midnight dates — the new code must agree exactly,
             except for stays arriving on a booking's last night (legacy-UTC
             let them through; the new rule blocks them like legacy did on
             the Berlin-local dates guests actually produced).
   Oracle B: legacy fed process-local goog.date.Dates built like the picker
             builds them; only run when the process TZ has DST. Equal except
             for the sanctioned differences (doc/frontend-refactor-quirks.md,
             \"Time zone\"): stays spanning the spring switch (DST bug, fixed)
             and stays arriving on a season's `ende` day (min-stay TZ
             artefact). For those, oracle A is the reference."
  (:require [cljs.test :refer [deftest is testing]]
            [cljs-time.core :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [buchung.verfuegbarkeit :as vf]
            [comp.snippets :as snip]
            [kalender.core :as kal]
            [legacy.buchung.ajaxing :as aj]
            [legacy.buchung.datechecking :as dc]
            [legacy.buchung.datepicker :as dp]
            [legacy.buchung.utils :as u]
            [preise.model :as pm]
            [test-support.fixtures :as fx]))

;; Cases per stay-check property and TZ run; the random-occupancy picker
;; properties run a third of that (each case checks ~50 days, and legacy
;; date parsing is slow). VF_NUM_TESTS overrides for quick local runs.
(def num-tests (or (some-> js/process.env.VF_NUM_TESTS js/parseInt) 10000))

;; ---------------------------------------------------------------------------
;; Inputs

(def new-preise    (memoize #(pm/normalize (fx/preise))))
(def legacy-preise (memoize #(aj/rework-preise (fx/preise))))

(defn- utc [iso] (u/parse-iso iso))

(defn- local
  "ISO day → local goog.date.Date as the legacy picker makes one."
  [iso]
  (let [[y m d] (map js/parseInt (.split iso "-"))]
    (t/plus- (t/local-date y m 1) (t/days (dec d)))))

(defn- dst-zone? []
  (not= (.getTimezoneOffset (js/Date. 2026 0 15))
        (.getTimezoneOffset (js/Date. 2026 6 15))))

(defn- whg-name [id] (str "W" id))

(defn- legacy-wohnungen
  "`belegung-map` {id [vevent…]} in the /api/buchungsproxy shape, reworked."
  [belegung-map]
  (aj/rework-wohnungen
   (vec (for [[id bs] (sort-by key belegung-map)]
          {:id id :name (whg-name id) :belegung bs}))))

(defn- new-wohnung [belegung-map id]
  {:name (whg-name id) :belegung (vf/prepare-belegung (get belegung-map id))})

;; ---------------------------------------------------------------------------
;; Legacy messages → data

(def ^:private min-re
  (re-pattern (str "^"
                   (-> (snip/err-mindestaufenthalt :de)
                       (.replace (js/RegExp. "[.*+?^${}()|\\[\\]\\\\]" "g") "\\$&")
                       (.replace "%s" "(.*)")
                       (.replace "%s" "(-?\\d+)"))
                   "$")))

(defn- legacy->data [msg]
  (cond
    (nil? msg) nil
    (= msg (snip/err-anreise-vor-abreise :de)) {:error :anreise-nach-abreise}
    (= msg (snip/err-belegt :de)) {:error :belegt}
    :else (if-let [[_ name n] (re-matches min-re msg)]
            {:error :mindestaufenthalt :min (js/parseInt n)
             :wohnung (when-not (= "null" name) name)}
            {:error :unmapped :msg msg})))

(defn- legacy-error [mk belegung-map {:keys [whg anreise abreise]}]
  (reset! u/locale :de)
  (let [a (mk anreise) b (mk abreise)
        aufenthalt (when (t/before? a b) (t/interval a b))]
    ;; only the stay's wohnung matters; reworking all eight is wasted time
    (legacy->data (dc/err-dates-bad? a b aufenthalt whg
                                     (legacy-wohnungen (select-keys belegung-map [whg]))
                                     (legacy-preise)))))

(defn- new-error [belegung-map {:keys [whg anreise abreise]}]
  (vf/stay-error {:anreise anreise :abreise abreise :wohnungsid whg}
                 (new-wohnung belegung-map whg)
                 (new-preise)))

;; ---------------------------------------------------------------------------
;; Sanctioned differences

(defn- local-offset [iso]
  (let [[y m d] (map js/parseInt (.split iso "-"))]
    (.getTimezoneOffset (js/Date. y (dec m) d))))

(defn- spans-spring-switch? [{:keys [anreise abreise]}]
  (> (local-offset anreise) (local-offset abreise)))

(defn- arrives-on-season-ende? [{:keys [anreise]}]
  (some #(= anreise (:ende %)) (:saisonen (fx/preise))))

(defn- arrives-on-last-booked-night?
  "Stay arriving on the last night of a (kept) booking — legacy-UTC missed
   it, the new rule blocks it."
  [belegung-map {:keys [whg anreise abreise]}]
  (and (pos? (kal/nights anreise abreise))
       (some #(= anreise (kal/plus-days (:dtend %) -1))
             (vf/prepare-belegung (get belegung-map whg)))))

;; ---------------------------------------------------------------------------
;; Generators

(def ^:private range-start "2024-10-01")
(def ^:private range-end   "2028-03-31")

(def gen-iso
  (gen/fmap #(kal/plus-days range-start %)
            (gen/choose 0 (kal/nights range-start range-end))))

(def gen-vevent
  "A booking of 1–21 nights (1-nighters get filtered); often starting on a
   Sunday or right where another booking ends (built by gen-belegung)."
  (gen/let [start  gen-iso
            nights (gen/frequency [[3 (gen/choose 1 3)] [5 (gen/choose 4 21)]])]
    {:dtstart start :dtend (kal/plus-days start nights)}))

(def gen-belegung
  "Vevents for one wohnung: random ones, plus chains of adjacent bookings
   (handover days) and Sunday-aligned ones."
  (gen/let [randoms (gen/vector gen-vevent 0 12)
            chains  (gen/vector (gen/let [start  gen-iso
                                          lens   (gen/vector (gen/choose 1 10) 2 4)]
                                  (vec (map (fn [from to] {:dtstart from :dtend to})
                                            (reductions kal/plus-days start lens)
                                            (rest (reductions kal/plus-days start lens)))))
                                0 3)
            sundays (gen/vector (gen/let [d gen-iso n (gen/choose 1 14)]
                                  (let [sun (kal/plus-days d (- 7 (kal/weekday d)))]
                                    {:dtstart sun :dtend (kal/plus-days sun n)}))
                                0 3)]
    ;; no gen/shuffle (its shrink tree is costly): legacy and new sort anyway
    (vec (concat randoms (apply concat chains) sundays))))

(def gen-belegung-map
  "Either the fixture or a fully random occupancy for wohnungen 1–8."
  (gen/one-of [(gen/return :fixture)
               (gen/fmap #(zipmap (range 1 9) %) (gen/vector gen-belegung 8))]))

(defn- resolve-map [m] (if (= :fixture m) (fx/belegung) m))

(def ^:private season-days
  (vec (distinct (mapcat (fn [{:keys [beginn ende]}] [beginn ende (kal/plus-days ende 1)])
                         (:saisonen (fx/preise))))))

(def ^:private dst-days
  ["2025-03-30" "2026-03-29" "2027-03-28" "2025-10-26" "2026-10-25" "2027-10-31"])

(defn- booking-days [bm whg]
  (mapcat (fn [{:keys [dtstart dtend]}]
            [dtstart dtend (kal/plus-days dtend -1) (kal/plus-days dtstart 1)])
          (get bm whg)))

(defn- gen-stay [bm]
  (gen/let [whg    (gen/choose 1 8)
            nights (gen/frequency [[6 (gen/choose 1 21)]
                                   [2 (gen/choose -3 0)]
                                   [2 (gen/elements [2 3 4 5 6 7 8])]])
            anreise (let [anchors (concat season-days dst-days (booking-days bm whg))]
                      (gen/frequency
                       [[3 gen-iso]
                        [7 (gen/let [b   (gen/elements (vec anchors))
                                     off (gen/choose -2 (max 2 nights))]
                             (kal/plus-days b (- off)))]]))]
    {:whg whg :anreise anreise :abreise (kal/plus-days anreise nights)}))

(def gen-case
  (gen/let [m gen-belegung-map
            s (gen-stay (resolve-map m))]
    [(resolve-map m) s]))

(defn- check
  "quick-check with JS stack traces off: legacy `parse-iso`
   (`cljs-time.coerce/from-string`) throws once per formatter it tries, and
   building + source-mapping those stacks dominates the run time otherwise."
  ([prop] (check num-tests prop))
  ([n prop]
   (let [limit (.-stackTraceLimit js/Error)
         r     (try (set! (.-stackTraceLimit js/Error) 0)
                    (tc/quick-check n prop :seed (.now js/Date))
                    (finally (set! (.-stackTraceLimit js/Error) limit)))]
     (is (true? (:pass? r))
         (pr-str (select-keys r [:seed :num-tests :fail :shrunk])))
     r)))

;; ---------------------------------------------------------------------------
;; Picker blocking

(defn- legacy-blocked? [mk legacy-belegung handovers iso]
  (boolean (dp/blocked? (t/floor (mk iso) t/day) legacy-belegung handovers)))

(deftest prepare-belegung-matches-rework-wohnungen
  (doseq [[id bs] (fx/belegung)]
    (is (= (mapv (fn [{:keys [dtstart dtend]}] [(kal/date->iso dtstart) (kal/date->iso dtend)])
                 (:belegung (first (legacy-wohnungen {id bs}))))
           (mapv (juxt :dtstart :dtend) (vf/prepare-belegung bs)))))
  (testing "invalid dates dropped, date-times filtered on hours then truncated"
    (is (= [{:dtstart "2026-07-04" :dtend "2026-07-07" :summary "x"}]
           (vf/prepare-belegung
            [{:dtstart "2026-07-04" :dtend "2026-07-07" :summary "x"}
             {:dtstart "" :dtend "2026-07-07"}
             {:dtstart "garbage" :dtend "2026-07-07"}
             {:dtstart nil :dtend "2026-07-07"}
             {:dtstart "2026-07-04T15:00:00Z" :dtend "2026-07-06T10:00:00Z"}])))
    (is (= [{:dtstart "2026-07-04" :dtend "2026-07-06"}]
           (vf/prepare-belegung [{:dtstart "2026-07-04T10:00:00Z" :dtend "2026-07-06T10:00:00Z"}])))))

(deftest day-blocked-parity-fixture-every-day
  (let [days  (kal/days-between "2025-01-01" "2028-01-01")
        lw    (legacy-wohnungen (fx/belegung))
        mks   (if (dst-zone?) {:utc utc :local local} {:utc utc})]
    (doseq [id (range 1 9)]
      (let [lb   (u/get-by-id lw id :belegung)
            lh   (dp/handovers lb)
            nb   (vf/prepare-belegung (get (fx/belegung) id))
            nh   (vf/handover-days nb)
            mism (for [[kind mk] mks
                       d days
                       :when (not= (legacy-blocked? mk lb lh d) (vf/day-blocked? d nb nh))]
                   [kind d])]
        (is (empty? mism) (str "wohnung " id ": " (pr-str (take 5 mism))))))))

(deftest day-blocked-parity-random-belegung
  (check (quot num-tests 3) (prop/for-all [bs gen-belegung
                        ds (gen/vector gen-iso 1 30)]
           (let [lb (u/get-by-id (legacy-wohnungen {1 bs}) 1 :belegung)
                 lh (dp/handovers lb)
                 nb (vf/prepare-belegung bs)
                 nh (vf/handover-days nb)
                 ;; plus the days around every booking edge
                 ds (concat ds (mapcat (fn [{:keys [dtstart dtend]}]
                                         [(kal/plus-days dtstart -1) dtstart (kal/plus-days dtstart 1)
                                          (kal/plus-days dtend -1) dtend (kal/plus-days dtend 1)])
                                       nb))]
             (every? #(= (legacy-blocked? utc lb lh %) (vf/day-blocked? % nb nh)) ds)))))

(deftest handover-days-parity
  (check (quot num-tests 3) (prop/for-all [bs gen-belegung]
           (let [lb (u/get-by-id (legacy-wohnungen {1 bs}) 1 :belegung)]
             (= (set (map kal/date->iso (dp/handovers lb)))
                (vf/handover-days (vf/prepare-belegung bs)))))))

(deftest day-selectable
  (let [bs (vf/prepare-belegung (get (fx/belegung) 1))]
    (is (vf/day-selectable? "2026-07-04" "2026-07-01" bs) "free arrival day")
    (is (vf/day-selectable? "2026-07-01" "2026-07-01" bs) "today is selectable")
    (is (not (vf/day-selectable? "2026-06-30" "2026-07-01" bs)) "past")
    (is (not (vf/day-selectable? "2026-07-05" "2026-07-01" bs)) "blocked interior")
    (is (not (vf/day-selectable? "2026-07-11" "2026-07-01" bs)) "handover")
    (is (not (vf/day-selectable? "2026-08-02" "2026-07-01" bs)) "Sunday arrival day")
    (is (vf/day-selectable? "2026-11-29" "2026-07-01" bs) "filtered single night")))

;; ---------------------------------------------------------------------------
;; Stay check

(deftest stay-error-parity-with-legacy-on-utc-dates
  (let [exempt (atom 0)]
    (check (prop/for-all [[bm s] gen-case]
             (let [n (new-error bm s)]
               (if (arrives-on-last-booked-night? bm s)
                 (do (swap! exempt inc)
                     (= {:error :belegt} n))
                 (= (legacy-error utc bm s) n)))))
    (is (pos? @exempt) "the generator must hit arrivals on a booking's last night")))

(deftest stay-error-parity-with-legacy-on-local-dates
  (if-not (dst-zone?)
    (is true "process TZ has no DST — oracle B skipped (it equals oracle A)")
    (let [differing (atom 0)]
      (check (prop/for-all [[bm s] gen-case]
               (let [n (new-error bm s)]
                 (cond
                   (arrives-on-last-booked-night? bm s)
                   (= (legacy-error local bm s) n)

                   (or (spans-spring-switch? s) (arrives-on-season-ende? s))
                   (do (when (not= n (legacy-error local bm s)) (swap! differing inc))
                       (= (legacy-error utc bm s) n))

                   :else
                   (= (legacy-error local bm s) n)))))
      (is (pos? @differing)
          "the generator must hit the sanctioned differences (else the exemption is untested)"))))

;; ---------------------------------------------------------------------------
;; Explicit regression cases (the step-4 characterization cases)

(defn- err [whg von bis]
  (new-error (fx/belegung) {:whg whg :anreise von :abreise bis}))

(defn- min-err [whg n] {:error :mindestaufenthalt :min n :wohnung (whg-name whg)})

(deftest regression-cases
  (testing "min stay = min over overlapping seasons (whg 2: 48 min 5 + 49 min 7)"
    (is (= (min-err 2 5) (err 2 "2026-04-05" "2026-04-09")))
    (is (nil? (err 2 "2026-04-04" "2026-04-09")))
    (is (= (min-err 2 7) (err 2 "2026-04-10" "2026-04-16")))
    (is (nil? (err 2 "2026-04-10" "2026-04-17"))))
  (testing "touching does not overlap (whg 3: 48 min 3, 49 min 5)"
    (is (nil? (err 3 "2026-04-05" "2026-04-08")))
    (is (= (min-err 3 5) (err 3 "2026-04-08" "2026-04-11")))
    (is (= (min-err 3 5) (err 3 "2026-04-07" "2026-04-10"))
        "arrival on season 48's ende ignores it (Berlin TZ artefact fixed)"))
  (testing "no season → mindestaufenthalt_standard (whg 1: 5)"
    (is (= (min-err 1 5) (err 1 "2027-11-10" "2027-11-13"))))
  (testing "blocked stays"
    (is (nil? (err 1 "2026-07-18" "2026-07-25")) "arrive on departure day")
    (is (nil? (err 1 "2026-06-26" "2026-07-04")) "depart on arrival day")
    (is (= {:error :belegt} (err 1 "2026-06-28" "2026-07-05")))
    (is (= {:error :belegt} (err 1 "2026-07-11" "2026-07-16")) "handover day as arrival")
    (is (= {:error :belegt} (err 1 "2026-07-17" "2026-07-25"))
        "arrival on a booking's last night (legacy-UTC let it through)")
    (is (nil? (err 1 "2026-07-27" "2026-08-02")) "Sunday rule is a picker rule only")
    (is (nil? (err 1 "2026-08-15" "2026-08-24")) "single-night booking (08-20) was filtered"))
  (testing "precedence: order, then blocked, then min stay"
    (is (= {:error :anreise-nach-abreise} (err 1 "2026-07-05" "2026-07-05")))
    (is (= {:error :anreise-nach-abreise} (err 1 "2026-07-06" "2026-07-05")))
    (is (= {:error :belegt} (err 1 "2026-07-03" "2026-07-05"))))
  (testing "nothing checked unless both dates set"
    (is (nil? (err 1 nil "2026-07-05")))
    (is (nil? (err 1 "2026-07-05" nil))))
  (testing "DST fixed: 5 nights across 2026-03-29 satisfy min 5 (whg 1 standard, no seasons)"
    (let [prs (assoc (new-preise) :saisonen [])
          e   #(vf/stay-error {:anreise %1 :abreise %2 :wohnungsid 1} {:name "W1" :belegung []} prs)]
      (is (nil? (e "2026-03-27" "2026-04-01")))
      (is (= (min-err 1 5) (e "2026-03-27" "2026-03-31")))))
  (testing "unknown wohnung name → nil :wohnung"
    (is (= {:error :mindestaufenthalt :min 5 :wohnung nil}
           (vf/stay-error {:anreise "2027-11-10" :abreise "2027-11-13" :wohnungsid 1}
                          {:belegung []} (new-preise))))))
