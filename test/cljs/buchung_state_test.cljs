(ns buchung-state-test
  "The booking SPA's re-frame state layer (roadmap step 10):
   `buchung.db` / `buchung.events` / `buchung.subs` / `buchung.fx`, run in
   Node without DOM. Effects (`:http-xhrio`, `::fx/warn`) and coeffects
   (`::fx/heute`, `::fx/csrf-token`) are replaced by stubs that record what
   the events asked for; the server's answers are dispatched by hand.

   Subscriptions are read inside a throw-away reaction so re-frame's
   subscription cache behaves as in a view."
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [cljs-time.core :as t]
            [re-frame.core :as rf]
            [re-frame.db :as rfdb]
            [reagent.ratom :as ratom]
            [buchung.db :as db]
            [buchung.events :as ev]
            [buchung.fx :as fx]
            [buchung.preis :as preis]
            [buchung.subs :as subs]
            [buchung.verfuegbarkeit :as vf]
            [kalender.core :as kal]
            [legacy.buchung.ajaxing :as laj]
            [legacy.buchung.preisberechnung :as lprs]
            [legacy.buchung.utils :as lu]
            [preise.model :as pm]
            [specs.anfrage :as specs]
            [test-support.fixtures :as fxt]))

;; ---------------------------------------------------------------------------
;; Harness

(def heute "2026-07-01")
(def token "csrf-test-token")

(defonce requests (atom []))
(defonce warnings (atom []))

(defn- install-stubs! []
  (rf/clear-fx :http-xhrio)
  (rf/reg-fx :http-xhrio (fn [req] (swap! requests conj req)))
  (rf/clear-fx ::fx/warn)
  (rf/reg-fx ::fx/warn (fn [args] (swap! warnings conj args)))
  (rf/clear-cofx ::fx/heute)
  (rf/reg-cofx ::fx/heute (fn [cofx _] (assoc cofx :heute heute)))
  (rf/clear-cofx ::fx/csrf-token)
  (rf/reg-cofx ::fx/csrf-token (fn [cofx _] (assoc cofx :csrf-token token))))

(use-fixtures :each
  {:before (fn []
             (install-stubs!)
             (reset! requests [])
             (reset! warnings [])
             (rf/clear-subscription-cache!)
             (reset! rfdb/app-db {}))})

(defn- sub
  "Value of subscription `q`, read inside a reaction and disposed."
  [q]
  (let [!v (atom nil)
        r  (ratom/run! (reset! !v @(rf/subscribe q)))]
    (ratom/dispose! r)
    @!v))

(defn- app-db [] @rfdb/app-db)

(defn- proxy-body
  "What /api/buchungsproxy answers, built from the fixtures."
  []
  {:wohnungen (vec (for [[id bs] (sort-by key (fxt/belegung))]
                     {:id id :name (str "Wohnung " id) :belegung bs}))
   :preise    (fxt/preise)})

(defn- boot!
  "init + data loaded, as after a successful page load."
  []
  (rf/dispatch-sync [::ev/init :de])
  (rf/dispatch-sync [::ev/data-loaded (proxy-body)]))

(def gast {:vorname "Erika" :nachname "Muster"
           :email "erika@example.com" :telefonnummer "012345"})

(defn- fill-form! []
  (doseq [[k v] gast] (rf/dispatch-sync [::ev/set-field [:gast k] v]))
  (rf/dispatch-sync [::ev/set-field [:datenschutz-gelesen?] true])
  (rf/dispatch-sync [::ev/set-field [:mietbedingungen-gelesen?] true]))

;; ---------------------------------------------------------------------------
;; Boot and defaults

(deftest init-defaults
  (rf/dispatch-sync [::ev/init "en"])
  (testing "legacy defaults: wohnung 1, 2 guests, 0 pets, no dates"
    (is (= {:wohnungsid 1 :gaestezahl 2 :haustierzahl 0 :anreise nil :abreise nil}
           (sub [::subs/auswahl]))))
  (is (= :en (sub [::subs/locale])))
  (is (= :loading (sub [::subs/status])))
  (is (= heute (sub [::subs/heute])))
  (is (= {:state :idle :error nil} (sub [::subs/post])))
  (is (false? (sub [::subs/submit-attempted?])))
  (is (false? (sub [::subs/ueberbelegung?])))
  (testing "both pickers show today's month"
    (is (= "2026-07-01" (sub [::subs/shown-month :anreise])))
    (is (= "2026-07-01" (sub [::subs/shown-month :abreise]))))
  (testing "init fetches the data over EDN"
    (is (= 1 (count @requests)))
    (let [{:keys [method uri on-success on-failure]} (first @requests)]
      (is (= :get method))
      (is (= "/api/buchungsproxy" uri))
      (is (= [::ev/data-loaded] on-success))
      (is (= [::ev/data-failed] on-failure))))
  (testing "nothing derived before the data is there"
    (is (nil? (sub [::subs/preise])))
    (is (nil? (sub [::subs/preis])))
    (is (nil? (sub [::subs/wohnung])))
    (is (= 6 (sub [::subs/maximalbelegung])))
    (is (false? (sub [::subs/can-submit?])))))

(deftest data-loading
  (rf/dispatch-sync [::ev/init :de])
  (rf/dispatch-sync [::ev/data-failed {:status 500}])
  (is (= :load-failed (sub [::subs/status])))
  (testing "retry"
    (rf/dispatch-sync [::ev/fetch-data])
    (is (= :loading (sub [::subs/status])))
    (is (= 2 (count @requests))))
  (rf/dispatch-sync [::ev/data-loaded (proxy-body)])
  (is (= :ready (sub [::subs/status])))
  (is (= 8 (count (sub [::subs/wohnungen]))))
  (testing "prices are stored raw and normalized in a sub"
    (is (= (fxt/preise) (:preise (app-db))))
    (let [iso-dates (fn [p] (update p :saisonen
                                    #(mapv (fn [s] (-> s (update :beginn kal/date->iso)
                                                       (update :ende kal/date->iso)))
                                           %)))]
      ;; cljs-time date objects are not `=` across instances; compare as ISO
      (is (= (iso-dates (pm/normalize (fxt/preise))) (iso-dates (sub [::subs/preise]))))))
  (testing "current wohnung and its prepared occupancy"
    (is (= "Wohnung 1" (sub [::subs/wohnungsname])))
    (is (= (vf/prepare-belegung (get (fxt/belegung) 1)) (sub [::subs/belegung])))
    (is (= 5 (sub [::subs/maximalbelegung])))))

;; ---------------------------------------------------------------------------
;; Apartment and guests

(deftest wohnung-switch-clamps-guests
  (boot!)
  (rf/dispatch-sync [::ev/select-wohnung 2])
  (rf/dispatch-sync [::ev/set-gaestezahl 7])
  (is (= 8 (sub [::subs/maximalbelegung])))
  (is (= 7 (get-in (sub [::subs/auswahl]) [:gaestezahl])))
  (testing "switching to a smaller wohnung clamps and warns"
    (rf/dispatch-sync [::ev/select-wohnung 3])
    (is (= 3 (:wohnungsid (sub [::subs/auswahl]))))
    (is (= 4 (:gaestezahl (sub [::subs/auswahl]))))
    (is (true? (sub [::subs/ueberbelegung?]))))
  (testing "choosing a guest count clears the warning"
    (rf/dispatch-sync [::ev/set-gaestezahl 3])
    (is (= 3 (:gaestezahl (sub [::subs/auswahl]))))
    (is (false? (sub [::subs/ueberbelegung?]))))
  (testing "switching without clamping clears the warning, guests stay"
    (rf/dispatch-sync [::ev/select-wohnung 2])
    (rf/dispatch-sync [::ev/set-gaestezahl 5])
    (rf/dispatch-sync [::ev/select-wohnung 3])
    (is (= 4 (:gaestezahl (sub [::subs/auswahl]))))
    (is (true? (sub [::subs/ueberbelegung?])))
    (rf/dispatch-sync [::ev/select-wohnung 2])
    (is (false? (sub [::subs/ueberbelegung?])))
    (is (= 4 (:gaestezahl (sub [::subs/auswahl])))))
  (testing "unknown wohnung: legacy default of 6"
    (rf/dispatch-sync [::ev/select-wohnung 99])
    (rf/dispatch-sync [::ev/set-gaestezahl 9])
    (rf/dispatch-sync [::ev/select-wohnung 99])
    (is (= 6 (sub [::subs/maximalbelegung])))
    (is (= 6 (:gaestezahl (sub [::subs/auswahl]))))
    (is (true? (sub [::subs/ueberbelegung?])))
    (is (nil? (sub [::subs/wohnungsname])))))

(deftest load-clamps-default-guests
  (testing "a wohnung allowing fewer guests than the default of 2"
    (rf/dispatch-sync [::ev/init :de])
    (rf/dispatch-sync [::ev/data-loaded
                       (assoc-in (proxy-body) [:preise :basisdaten 1 :maximalbelegung] "1")])
    (is (= 1 (:gaestezahl (sub [::subs/auswahl]))))
    (is (true? (sub [::subs/ueberbelegung?])))))

(deftest haustiere
  (boot!)
  (rf/dispatch-sync [::ev/set-haustierzahl 2])
  (is (= 2 (:haustierzahl (sub [::subs/auswahl])))))

;; ---------------------------------------------------------------------------
;; Dates, price, errors

(deftest date-flow-price
  (boot!)
  (is (nil? (sub [::subs/preis])))
  (rf/dispatch-sync [::ev/pick-date :anreise "2026-08-10"])
  (is (nil? (sub [::subs/preis])) "no price with one date")
  (is (nil? (sub [::subs/stay-error])))
  (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-17"])
  (rf/dispatch-sync [::ev/set-gaestezahl 5])
  (rf/dispatch-sync [::ev/set-haustierzahl 1])
  (let [auswahl {:anreise "2026-08-10" :abreise "2026-08-17"
                 :gaestezahl 5 :haustierzahl 1 :wohnungsid 1}]
    (is (= auswahl (sub [::subs/auswahl])))
    (is (= (preis/berechnen auswahl (pm/normalize (fxt/preise)))
           (sub [::subs/preis])))
    (is (= 7 (:tageszahl (sub [::subs/preis]))))
    (is (nil? (sub [::subs/stay-error])))))

(deftest date-error-codes
  (boot!)
  (testing "arrival not before departure"
    (rf/dispatch-sync [::ev/pick-date :anreise "2026-08-17"])
    (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-10"])
    (is (= {:error :anreise-nach-abreise} (sub [::subs/stay-error])))
    (is (nil? (sub [::subs/preis]))))
  (testing "shares a night with a booking (wohnung 1, 2026-07-04 … 07-18)"
    (rf/dispatch-sync [::ev/pick-date :anreise "2026-07-05"])
    (rf/dispatch-sync [::ev/pick-date :abreise "2026-07-12"])
    (is (= {:error :belegt} (sub [::subs/stay-error]))))
  (testing "too short (season 51, min 5 for wohnung 1)"
    (rf/dispatch-sync [::ev/pick-date :anreise "2026-08-10"])
    (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-12"])
    (is (= {:error :mindestaufenthalt
            :min   (vf/mindestaufenthalt {:anreise "2026-08-10" :abreise "2026-08-12" :wohnungsid 1}
                                         (pm/normalize (fxt/preise)))
            :wohnung "Wohnung 1"}
           (sub [::subs/stay-error])))
    (is (number? (:min (sub [::subs/stay-error])))))
  (testing "another wohnung is free on the blocked dates"
    (rf/dispatch-sync [::ev/pick-date :anreise "2026-07-05"])
    (rf/dispatch-sync [::ev/pick-date :abreise "2026-07-12"])
    (rf/dispatch-sync [::ev/select-wohnung 3])
    (is (nil? (sub [::subs/stay-error])))))

(deftest month-navigation
  (boot!)
  (rf/dispatch-sync [::ev/shift-month :anreise :month 1])
  (is (= "2026-08-01" (sub [::subs/shown-month :anreise])))
  (is (= "2026-07-01" (sub [::subs/shown-month :abreise])) "pickers are independent")
  (rf/dispatch-sync [::ev/shift-month :anreise :month -2])
  (is (= "2026-06-01" (sub [::subs/shown-month :anreise])))
  (rf/dispatch-sync [::ev/shift-month :abreise :month 6])
  (is (= "2027-01-01" (sub [::subs/shown-month :abreise])) "across the year end")
  (rf/dispatch-sync [::ev/shift-month :abreise :month -1])
  (is (= "2026-12-01" (sub [::subs/shown-month :abreise])))
  (rf/dispatch-sync [::ev/shift-month :abreise :year 1])
  (is (= "2027-12-01" (sub [::subs/shown-month :abreise])))
  (rf/dispatch-sync [::ev/shift-month :abreise :year -3])
  (is (= "2024-12-01" (sub [::subs/shown-month :abreise]))))

;; ---------------------------------------------------------------------------
;; Picker cells (legacy day-cell classes)

(defn- cells-by-iso [picker]
  (let [{:keys [weeks]} (sub [::subs/picker picker])]
    (into {} (for [c (apply concat weeks) :when (not (:outside? c))] [(:iso c) c]))))

(deftest picker-cells
  (boot!)
  (let [{:keys [shown-month year month weeks]} (sub [::subs/picker :anreise])]
    (is (= "2026-07-01" shown-month))
    (is (= [2026 7] [year month]))
    (is (= 6 (count weeks)))
    (is (every? #(= 7 (count %)) weeks))
    (is (= (kal/month-grid 2026 7) (mapv #(mapv (fn [c] (select-keys c [:iso :outside?])) %) weeks)))
    (testing "outside cells carry only the outside class"
      (let [c (ffirst weeks)]
        (is (= "2026-06-29" (:iso c)))
        (is (= 29 (:tag c)))
        (is (false? (:selectable? c)))
        (is (= ["picker__cell" "picker__cell--day" "picker__cell--outside"] (:classes c))))))
  (let [cells (cells-by-iso :anreise)
        classes #(:classes (get cells %))]
    (testing "free day"
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--free"] (classes "2026-07-02")))
      (is (true? (:selectable? (get cells "2026-07-02")))))
    (testing "today is not past"
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--free"] (classes heute))))
    (testing "blocked: handover day, interior day, Sunday arrival/departure"
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--blocked"] (classes "2026-07-11")))
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--blocked"] (classes "2026-07-07")))
      (is (false? (:selectable? (get cells "2026-07-07"))))
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--free"] (classes "2026-07-04"))
          "plain Saturday arrival day is free"))
    (testing "blocked days of the shown month agree with the shared rule"
      (let [belegung (sub [::subs/belegung])]
        (doseq [[iso c] cells]
          (is (= (vf/day-blocked? iso belegung) (:blocked? c)) iso)))))
  (testing "past days"
    (rf/dispatch-sync [::ev/shift-month :anreise :month -1])
    (let [cells (cells-by-iso :anreise)]
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--past"] (:classes (get cells "2026-06-30"))))
      (is (false? (:selectable? (get cells "2026-06-30"))))
      (is (= 30 (:tag (get cells "2026-06-30"))))))
  (testing "selection and the contained span (counterdate inclusive, selected excluded)"
    (rf/dispatch-sync [::ev/shift-month :anreise :month 2])
    (rf/dispatch-sync [::ev/shift-month :abreise :month 1])
    (rf/dispatch-sync [::ev/pick-date :anreise "2026-08-10"])
    (let [cells (cells-by-iso :anreise)]
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--free" "picker__cell--selected"]
             (:classes (get cells "2026-08-10"))))
      (is (= "2026-08-10" (:selected (sub [::subs/picker :anreise]))))
      (is (nil? (:selected (sub [::subs/picker :abreise])))))
    (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-17"])
    (let [a (cells-by-iso :anreise)
          b (cells-by-iso :abreise)]
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--free" "picker__cell--selected"]
             (:classes (get a "2026-08-10"))))
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--contained"]
             (:classes (get a "2026-08-13"))))
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--contained"]
             (:classes (get a "2026-08-17"))) "the counterdate itself is contained")
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--free"]
             (:classes (get a "2026-08-18"))))
      (is (true? (:selectable? (get a "2026-08-13"))) "contained days stay clickable")
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--free" "picker__cell--selected"]
             (:classes (get b "2026-08-17"))))
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--contained"]
             (:classes (get b "2026-08-10"))))))
  (testing "a stay error paints every cell bad and drops the span"
    (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-11"])
    (is (= {:error :mindestaufenthalt} (select-keys (sub [::subs/stay-error]) [:error])))
    (let [a (cells-by-iso :anreise)]
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--free" "picker__cell--selected" "picker__cell--bad"]
             (:classes (get a "2026-08-10"))))
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--free" "picker__cell--bad"]
             (:classes (get a "2026-08-11"))))
      (is (every? :bad? (vals a))))))

;; ---------------------------------------------------------------------------
;; Request payload — parity with legacy build-anfrage

(defn- legacy-build-anfrage
  "Verbatim logic of the legacy `buchung.core/build-anfrage`
   (buchung/core.cljs:67-80), which is private and not part of the frozen
   oracle; `state` in the legacy shape with cljs-time dates."
  [state preis wohnungsname]
  (let [{:keys [anreise abreise ausgew-whg-id gast gaestezahl haustierzahl
                datenschutz-gelesen? mietbedingungen-gelesen? zusatznachricht]} state]
    (cond-> {:anreise                  (when (t/date? anreise) (lu/to-iso-day anreise))
             :abreise                  (when (t/date? abreise) (lu/to-iso-day abreise))
             :gaestezahl               gaestezahl
             :haustierzahl             haustierzahl
             :wohnungsid               ausgew-whg-id
             :wohnungsname             (or wohnungsname "")
             :gast                     (or gast {})
             :datenschutz-gelesen?     (boolean datenschutz-gelesen?)
             :mietbedingungen-gelesen? (boolean mietbedingungen-gelesen?)}
      (seq zusatznachricht) (assoc :zusatznachricht zusatznachricht)
      preis                 (assoc :preis (lu/map-vals #(when (number? %) (.round js/Math %)) preis)))))

(defn- legacy-anfrage
  "Legacy payload for the same selection, legacy price on UTC dates."
  [{:keys [anreise abreise gaestezahl haustierzahl wohnungsid]} extra wohnungsname]
  (let [a (lu/parse-iso anreise)
        b (lu/parse-iso abreise)
        aufenthalt (when (and a b (t/before? a b)) (t/interval a b))
        preis (lprs/berechnen aufenthalt gaestezahl haustierzahl wohnungsid
                              (laj/rework-preise (fxt/preise)))]
    (legacy-build-anfrage (merge {:anreise a :abreise b :ausgew-whg-id wohnungsid
                                  :gaestezahl gaestezahl :haustierzahl haustierzahl}
                                 extra)
                          preis wohnungsname)))

(deftest anfrage-payload-parity
  (boot!)
  (testing "empty form, no dates"
    (is (= (legacy-anfrage (sub [::subs/auswahl]) {} "Wohnung 1")
           (sub [::subs/anfrage])))
    (is (= {:anreise nil :abreise nil :gaestezahl 2 :haustierzahl 0 :wohnungsid 1
            :wohnungsname "Wohnung 1" :gast {} :datenschutz-gelesen? false
            :mietbedingungen-gelesen? false}
           (sub [::subs/anfrage]))))
  (testing "complete form with price"
    (rf/dispatch-sync [::ev/select-wohnung 2])
    (rf/dispatch-sync [::ev/set-gaestezahl 6])
    (rf/dispatch-sync [::ev/set-haustierzahl 1])
    (rf/dispatch-sync [::ev/pick-date :anreise "2026-08-10"])
    (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-20"])
    (fill-form!)
    (rf/dispatch-sync [::ev/set-field [:zusatznachricht] "Bitte Kinderbett"])
    (let [anfrage (sub [::subs/anfrage])]
      (is (= (legacy-anfrage (sub [::subs/auswahl])
                             {:gast gast :datenschutz-gelesen? true
                              :mietbedingungen-gelesen? true
                              :zusatznachricht "Bitte Kinderbett"}
                             "Wohnung 2")
             anfrage))
      (is (specs/valid? anfrage))
      (is (every? integer? (vals (:preis anfrage))) "price values rounded")
      (is (= (.round js/Math (:einzeltage-summe (sub [::subs/preis])))
             (get-in anfrage [:preis :einzeltage-summe])))))
  (testing "an emptied message is left out, as legacy"
    (rf/dispatch-sync [::ev/set-field [:zusatznachricht] ""])
    (is (not (contains? (sub [::subs/anfrage]) :zusatznachricht))))
  (testing "inverted dates: no price key, as legacy"
    (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-01"])
    (is (= (legacy-anfrage (sub [::subs/auswahl])
                           {:gast gast :datenschutz-gelesen? true :mietbedingungen-gelesen? true}
                           "Wohnung 2")
           (sub [::subs/anfrage])))
    (is (not (contains? (sub [::subs/anfrage]) :preis)))))

(deftest set-field-allowed-paths
  (boot!)
  (rf/dispatch-sync [::ev/set-field [:gast :vorname] "Erika"])
  (is (= "Erika" (sub [::subs/field [:gast :vorname]])))
  (is (= {:vorname "Erika"} (sub [::subs/gast])))
  (testing "unknown paths are ignored"
    (let [before (app-db)]
      (rf/dispatch-sync [::ev/set-field [:auswahl :gaestezahl] 99])
      (rf/dispatch-sync [::ev/set-field [:gast :admin?] true])
      (is (= before (app-db))))))

;; ---------------------------------------------------------------------------
;; Submit

(deftest submit-invalid
  (boot!)
  (rf/dispatch-sync [::ev/pick-date :anreise "2026-08-10"])
  (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-17"])
  (is (= #{} (sub [::subs/field-errors])) "nothing shown before a submit attempt")
  (is (false? (sub [::subs/can-submit?])))
  (rf/dispatch-sync [::ev/submit])
  (is (true? (sub [::subs/submit-attempted?])))
  (is (= :idle (:state (sub [::subs/post]))))
  (is (= 1 (count @requests)) "only the data fetch, no POST")
  (testing "field errors as malli paths"
    (is (= #{[:gast :vorname] [:gast :nachname] [:gast :email] [:gast :telefonnummer]
             [:datenschutz-gelesen?] [:mietbedingungen-gelesen?]}
           (sub [::subs/field-errors]))))
  (testing "errors follow the input"
    (rf/dispatch-sync [::ev/set-field [:gast :email] "keine-mail"])
    (is (contains? (sub [::subs/field-errors]) [:gast :email]))
    (fill-form!)
    (is (= #{} (sub [::subs/field-errors])))
    (is (true? (sub [::subs/can-submit?])))))

(deftest submit-flow
  (boot!)
  (rf/dispatch-sync [::ev/pick-date :anreise "2026-08-10"])
  (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-17"])
  (fill-form!)
  (rf/dispatch-sync [::ev/submit])
  (is (= {:state :pending :error nil} (sub [::subs/post])))
  (is (false? (sub [::subs/can-submit?])) "no double submit")
  (testing "the POST"
    (is (= 2 (count @requests)))
    (let [{:keys [method uri params headers on-success on-failure format response-format]}
          (second @requests)]
      (is (= :post method))
      (is (= "/api/anfrage" uri))
      (is (= (sub [::subs/anfrage]) params))
      (is (= {"X-CSRF-Token" token} headers))
      (is (re-find #"application/edn" (pr-str (:content-type format))) "EDN body")
      (is (re-find #"application/edn" (pr-str (:content-type response-format))) "EDN answer")
      (is (= [::ev/submit-ok] on-success))
      (is (= [::ev/submit-failed] on-failure))))
  (testing "a second submit while pending is ignored"
    (rf/dispatch-sync [::ev/submit])
    (is (= 2 (count @requests))))
  (rf/dispatch-sync [::ev/submit-ok {:ok true}])
  (is (= {:state :success :error nil} (sub [::subs/post]))))

(deftest submit-server-errors
  (boot!)
  (rf/dispatch-sync [::ev/pick-date :anreise "2026-08-10"])
  (rf/dispatch-sync [::ev/pick-date :abreise "2026-08-17"])
  (fill-form!)
  (doseq [err [{:error :belegt}
               {:error :mindestaufenthalt :min 5}
               {:error :ueberbelegung}
               {:error :invalid :fields [[:gast :email]]}]]
    (rf/dispatch-sync [::ev/submit])
    (rf/dispatch-sync [::ev/submit-failed {:status 400 :status-text "Bad Request" :response err}])
    (is (= {:state :failed :error err} (sub [::subs/post])) (pr-str err))
    (is (true? (sub [::subs/can-submit?])) "retry possible"))
  (testing "failures without an EDN error body"
    (rf/dispatch-sync [::ev/submit])
    (rf/dispatch-sync [::ev/submit-failed {:status 500 :response "Internal Server Error"}])
    (is (= {:state :failed :error {:error :unbekannt :status 500}} (sub [::subs/post])))
    (rf/dispatch-sync [::ev/submit])
    (rf/dispatch-sync [::ev/submit-failed {:status 0 :failure :failed}])
    (is (= {:state :failed :error {:error :unbekannt :status 0}} (sub [::subs/post]))))
  (testing "rejections are logged"
    (is (= 6 (count @warnings)))
    (is (= "anfrage rejected" (ffirst @warnings))))
  (testing "a later success clears the error"
    (rf/dispatch-sync [::ev/submit])
    (rf/dispatch-sync [::ev/submit-ok {:ok true}])
    (is (= {:state :success :error nil} (sub [::subs/post])))))

;; ---------------------------------------------------------------------------
;; Pure helpers

(deftest db-helpers
  (is (= "2026-02-01" (db/month-of "2026-02-28")))
  (is (= "2027-01-01" (db/shift-month "2026-12-15" :month 1)))
  (is (= "2025-12-01" (db/shift-month "2026-01-15" :month -1)))
  (is (= "2028-02-01" (db/shift-month "2026-02-15" :year 2)))
  (is (= 6 (db/maximalbelegung nil 1)))
  (is (= 5 (db/maximalbelegung (pm/normalize (fxt/preise)) 1)))
  (is (= 6 (db/maximalbelegung (pm/normalize (fxt/preise)) 99)))
  (testing "legacy int on an empty maximalbelegung"
    (is (= 0 (db/maximalbelegung (pm/normalize (assoc-in (fxt/preise) [:basisdaten 1 :maximalbelegung] ""))
                                 1)))))
