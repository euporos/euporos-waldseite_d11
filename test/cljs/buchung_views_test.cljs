(ns buchung-views-test
  "Booking SPA views (roadmap step 11), without DOM: the localized texts in
   `buchung.views.texte` against the frozen legacy views/messages, and the
   pure `buchung.views.datepicker` hiccup fed with the `buchung.subs`
   cell model."
  (:require [cljs.test :refer [deftest is testing]]
            [cljs-time.core :as t]
            [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [buchung.subs :as subs]
            [buchung.verfuegbarkeit :as vf]
            [buchung.views.datepicker :as dp]
            [buchung.views.texte :as tx]
            [comp.snippets :as snip]
            [kalender.core :as kal]
            [legacy.buchung.ajaxing :as laj]
            [legacy.buchung.datechecking :as ldc]
            [legacy.buchung.utils :as lu]
            [preise.model :as pm]
            [test-support.fixtures :as fxt]))

(def locales [:de :en :nl])

;; ---------------------------------------------------------------------------
;; Dates and calendar labels

(deftest format-datum-matches-legacy
  (doseq [loc locales]
    (reset! lu/locale loc)
    (doseq [iso (concat (kal/days-between "2026-01-01" "2027-01-01")
                        ["2025-12-31" "2030-02-28" "2032-02-29"])]
      (is (= (lu/format-date (lu/parse-iso iso)) (tx/format-datum loc iso))
          (str loc " " iso))))
  (is (= "05.03.2027" (tx/format-datum :de "2027-03-05")))
  (is (= "05/03/2027" (tx/format-datum :en "2027-03-05")))
  (is (= "05-03-2027" (tx/format-datum :nl "2027-03-05")))
  (is (nil? (tx/format-datum :de nil))))

(deftest calendar-labels-match-legacy
  (doseq [loc locales]
    (reset! lu/locale loc)
    (is (= (lu/weekday-short) (tx/weekday-short loc)))
    (doseq [m (range 1 13)]
      (is (= (lu/month-label (lu/parse-iso (gstring/format "2026-%02d-01" m)))
             (tx/month-label loc m))))))

;; ---------------------------------------------------------------------------
;; Stay errors: legacy `err-dates-bad?` strings == texts of the shared data

(def ^:private legacy-preise (memoize #(laj/rework-preise (fxt/preise))))
(def ^:private new-preise (memoize #(pm/normalize (fxt/preise))))

(defn- whg-name [id] (str "Wohnung " id))

(def ^:private legacy-wohnungen
  (memoize (fn [whg]
             (laj/rework-wohnungen
              [{:id whg :name (whg-name whg) :belegung (get (fxt/belegung) whg)}]))))

(def ^:private new-wohnung
  (memoize (fn [whg]
             {:name     (whg-name whg)
              :belegung (vf/prepare-belegung (get (fxt/belegung) whg))})))

(defn- legacy-text [loc whg anreise abreise]
  (reset! lu/locale loc)
  (let [a (lu/parse-iso anreise)
        b (lu/parse-iso abreise)]
    (ldc/err-dates-bad? a b (when (t/before? a b) (t/interval a b))
                        whg (legacy-wohnungen whg) (legacy-preise))))

(defn- new-text [loc whg anreise abreise]
  (tx/stay-error loc (vf/stay-error {:anreise anreise :abreise abreise :wohnungsid whg}
                                    (new-wohnung whg)
                                    (new-preise))))

(defn- arrives-on-last-booked-night?
  "Sanctioned difference (doc/frontend-refactor-quirks.md, \"Arrival on a
   booking's last night\"): legacy on UTC dates let it through, the shared
   rule blocks it."
  [whg anreise abreise]
  (and (pos? (kal/nights anreise abreise))
       (some #(= anreise (kal/plus-days (:dtend %) -1))
             (:belegung (new-wohnung whg)))))

(deftest stay-error-texts-match-legacy
  (let [cases (for [whg    (sort (keys (fxt/belegung)))
                    anreise (take-nth 13 (kal/days-between "2026-01-02" "2027-12-20"))
                    n      [-1 0 1 2 3 5 7 14]
                    :let   [abreise (kal/plus-days anreise n)]
                    :when  (not (arrives-on-last-booked-night? whg anreise abreise))]
                [whg anreise abreise])
        seen  (atom #{})]
    (doseq [loc locales
            [whg a b] cases]
      (let [expected (legacy-text loc whg a b)]
        (when expected (swap! seen conj (re-find #"^\S+ \S+" expected)))
        (is (= expected (new-text loc whg a b)) (str loc " " whg " " a "→" b))))
    (testing "the sample hits more than one kind of error"
      (is (< 2 (count @seen))))))

;; ---------------------------------------------------------------------------
;; Price line

(deftest preiszeile-as-legacy-panel
  (let [p {:tageszahl 7 :gesamtsumme 543.5 :gaestebeitrag 21 :energieaufschlag 14}]
    (doseq [loc locales]
      (is (= (str (gstring/format (snip/preiszeile loc) 7 "Ahorn" 543.5)
                  (gstring/format (snip/gaestebeitrag-teil loc) 21)
                  (gstring/format (snip/energieaufschlag-teil loc) 14)
                  ")")
             (tx/preiszeile loc "Ahorn" p)))))
  (testing "no energy surcharge part when zero or missing"
    (is (not (str/includes? (tx/preiszeile :de "A" {:tageszahl 3 :gesamtsumme 300 :gaestebeitrag 9 :energieaufschlag 0})
                            "Energieaufschlag")))
    (is (str/ends-with? (tx/preiszeile :de "A" {:tageszahl 3 :gesamtsumme 300 :gaestebeitrag 9}) "Gästebeitrag)"))))

;; ---------------------------------------------------------------------------
;; Field and server errors

(deftest field-error-texts
  (doseq [loc locales]
    (is (= (snip/err-email-ungueltig loc) (tx/field-error loc [:gast :email])))
    (is (= (snip/err-zustimmung-fehlt loc) (tx/field-error loc [:datenschutz-gelesen?])))
    (is (= (snip/err-zustimmung-fehlt loc) (tx/field-error loc [:mietbedingungen-gelesen?])))
    (is (= (snip/err-nachricht-zu-lang loc) (tx/field-error loc [:zusatznachricht])))
    (is (= (snip/err-feld-leer loc) (tx/field-error loc [:gast :vorname])))
    (is (seq (tx/formular-unvollstaendig loc)))))

(deftest server-error-texts
  (doseq [loc locales]
    (is (= (snip/err-belegt loc) (tx/server-error loc "Ahorn" {:error :belegt})))
    (is (= (snip/err-anreise-vor-abreise loc)
           (tx/server-error loc "Ahorn" {:error :anreise-nach-abreise})))
    (is (= (gstring/format (snip/err-mindestaufenthalt loc) "Ahorn" 5)
           (tx/server-error loc "Ahorn" {:error :mindestaufenthalt :min 5})))
    (is (= (gstring/format (snip/ueberbelegung-warnung loc) "Ahorn" 4)
           (tx/server-error loc "Ahorn" {:error :ueberbelegung :max 4})))
    (is (= (snip/err-server-ungueltig loc)
           (tx/server-error loc "Ahorn" {:error :invalid :fields [[:gast :email]]})))
    (doseq [e [{:error :unbekannt :status 0} {:error :send-failed} {:error :mindestaufenthalt} nil]]
      (is (= (snip/err-server-allgemein loc) (tx/server-error loc "Ahorn" e)) (pr-str e)))))

(deftest new-snippets-in-all-locales
  (doseq [f [snip/buchungsdaten-nicht-geladen snip/err-feld-leer snip/err-email-ungueltig
             snip/err-zustimmung-fehlt snip/err-nachricht-zu-lang
             snip/err-formular-unvollstaendig snip/err-server-ungueltig
             snip/err-server-allgemein]
          loc locales]
    (is (and (string? (f loc)) (seq (f loc))))
    (is (not= (f :de) (f :en)))))

;; ---------------------------------------------------------------------------
;; Datepicker hiccup

(defn- tds [hiccup]
  (->> (tree-seq #(or (vector? %) (seq? %)) seq hiccup)
       (filter #(and (vector? %) (= :td (first %)) (map? (second %))))))

(defn- picker-model [{:keys [selected counter heute belegung]}]
  (let [ctx {:heute heute :belegung belegung :handovers (vf/handover-days belegung)
             :selected selected :counter counter :bad? false}]
    {:year 2026 :month 7
     :weeks (mapv #(mapv (fn [c] (subs/cell c ctx)) %) (kal/month-grid 2026 7))}))

(deftest datepicker-renders-cell-model
  (let [picked   (atom [])
        shifted  (atom [])
        belegung (vf/prepare-belegung [{:dtstart "2026-07-10" :dtend "2026-07-15"}])
        model    (picker-model {:heute "2026-07-05" :belegung belegung
                                :selected "2026-07-20" :counter "2026-07-25"})
        h        (dp/datepicker (assoc model :locale :de :test-id "anreise"
                                       :on-pick #(swap! picked conj %)
                                       :on-shift #(swap! shifted conj [%1 %2])))
        cells    (tds h)
        by-iso   (into {} (for [[_ attrs] cells :when (:key attrs)] [(:key attrs) attrs]))]
    (testing "42 day cells, outside days without test id or click"
      (is (= 42 (count by-iso)))
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--outside"]
             (:class (by-iso "2026-06-29"))))
      (is (nil? (:on-click (by-iso "2026-06-29")))))
    (testing "classes come from the model; only selectable days are clickable"
      (is (= ["picker__cell" "picker__cell--day" "picker__cell--past"]
             (:class (by-iso "2026-07-01"))))
      (is (nil? (:on-click (by-iso "2026-07-01"))))
      (is (some #{"picker__cell--blocked"} (:class (by-iso "2026-07-12"))))
      (is (nil? (:on-click (by-iso "2026-07-12"))))
      (is (some #{"picker__cell--selected"} (:class (by-iso "2026-07-20"))))
      (is (some #{"picker__cell--contained"} (:class (by-iso "2026-07-22"))))
      (is (= "anreise-tag-2026-07-22" (:data-test-id (by-iso "2026-07-22")))))
    (testing "clicks report ISO days"
      ((:on-click (by-iso "2026-07-22")))
      (is (= ["2026-07-22"] @picked)))
    (testing "weekday header from the snippets"
      (is (= (snip/weekday-short :de)
             (->> (tree-seq #(or (vector? %) (seq? %)) seq h)
                  (filter #(and (vector? %) (= :td.picker__cell.picker__cell--weekday (first %))))
                  (map #(nth % 2))))))
    (testing "header: navigation and month label"
      (let [[_ head-props] (nth h 2)
            hh (#'dp/header head-props)
            buttons (->> (tree-seq #(or (vector? %) (seq? %)) seq hh)
                         (filter #(and (vector? %) (fn? (first %)))))]
        (is (str/includes? (pr-str hh) "Juli"))
        (doseq [[_ _ _ on-click] buttons] (on-click))
        (is (= [[:year -1] [:month -1] [:month 1] [:year 1]] @shifted))))))
