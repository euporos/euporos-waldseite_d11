(ns buchung.subs
  "Subscriptions of the booking SPA. Everything derived from app-db is
   computed here with the shared domain code (`buchung.preis`,
   `buchung.verfuegbarkeit`, `preise.model`); results are data, views
   localize."
  (:require [buchung.db :as db]
            [buchung.preis :as preis]
            [buchung.verfuegbarkeit :as vf]
            [kalender.core :as kal]
            [re-frame.core :as rf]
            [specs.anfrage :as specs]))

;; ---------------------------------------------------------------------------
;; Plain extractors

(rf/reg-sub ::db       (fn [db _] db))
(rf/reg-sub ::locale   (fn [db _] (:locale db)))
(rf/reg-sub ::heute    (fn [db _] (:heute db)))
(rf/reg-sub ::status   (fn [db _] (:status db)))
(rf/reg-sub ::wohnungen (fn [db _] (:wohnungen db)))
(rf/reg-sub ::preise-raw (fn [db _] (:preise db)))
(rf/reg-sub ::auswahl  (fn [db _] (:auswahl db)))
(rf/reg-sub ::gast     (fn [db _] (:gast db)))
(rf/reg-sub ::ueberbelegung?    (fn [db _] (get-in db [:ui :ueberbelegung?])))
(rf/reg-sub ::post              (fn [db _] (get-in db [:ui :post])))
(rf/reg-sub ::submit-attempted? (fn [db _] (get-in db [:ui :submit-attempted?])))

(rf/reg-sub ::field
  (fn [db [_ path]] (get-in db path)))

(rf/reg-sub ::shown-month
  (fn [db [_ picker]] (get-in db [:ui :picker picker :shown-month])))

;; ---------------------------------------------------------------------------
;; Derived

(rf/reg-sub ::preise
  :<- [::preise-raw]
  (fn [raw _] (db/normalized-preise {:preise raw})))

(rf/reg-sub ::wohnungsid
  :<- [::auswahl]
  (fn [a _] (:wohnungsid a)))

(rf/reg-sub ::wohnung
  :<- [::wohnungen]
  :<- [::wohnungsid]
  (fn [[wohnungen id] _]
    (first (filter #(= id (:id %)) wohnungen))))

(rf/reg-sub ::wohnungsname
  :<- [::wohnung]
  (fn [w _] (:name w)))

(rf/reg-sub ::maximalbelegung
  :<- [::preise]
  :<- [::wohnungsid]
  (fn [[preise id] _] (db/maximalbelegung preise id)))

(rf/reg-sub ::gaeste-optionen
  :<- [::auswahl]
  :<- [::maximalbelegung]
  (fn [[auswahl max-n] [_ kategorie]] (db/optionen auswahl max-n kategorie)))

(rf/reg-sub ::belegung
  :<- [::wohnung]
  (fn [w _] (vf/prepare-belegung (:belegung w))))

(rf/reg-sub ::stay-error
  :<- [::auswahl]
  :<- [::wohnung]
  :<- [::belegung]
  :<- [::preise]
  (fn [[auswahl wohnung belegung preise] _]
    (vf/stay-error auswahl {:name (:name wohnung) :belegung belegung} preise)))

(rf/reg-sub ::preis
  :<- [::auswahl]
  :<- [::preise]
  (fn [[auswahl preise] _]
    (when preise (preis/berechnen auswahl preise))))

(rf/reg-sub ::anfrage
  :<- [::preise]
  :<- [::db]
  (fn [[preise db] _] (db/anfrage db preise)))

(rf/reg-sub ::field-errors
  :<- [::anfrage]
  :<- [::submit-attempted?]
  (fn [[anfrage attempted?] _]
    (if attempted?
      (set (specs/invalid-fields anfrage))
      #{})))

(rf/reg-sub ::can-submit?
  :<- [::anfrage]
  :<- [::post]
  (fn [[anfrage post] _]
    (and (specs/valid? anfrage)
         (not= :pending (:state post)))))

;; ---------------------------------------------------------------------------
;; Picker cells

(defn- between-inclusive?
  "Legacy `unordered-within?` inclusive: `d` in [min(a,b), max(a,b)]."
  [a b d]
  (let [[lo hi] (sort [a b])]
    (and (not (neg? (compare d lo))) (not (neg? (compare hi d))))))

(defn cell
  "Model of one picker day, classes as legacy `day-cell` / `outside-cell`.
   `selected` and `counter` are the picker's own and the other picker's
   ISO date; `bad?` the presence of a stay error (legacy paints every cell
   bad then)."
  [{:keys [iso outside?]} {:keys [heute belegung handovers selected counter bad?]}]
  (let [tag (js/parseInt (subs iso 8 10) 10)]
    (if outside?
      {:iso iso :tag tag :outside? true :selectable? false
       :classes ["picker__cell" "picker__cell--day" "picker__cell--outside"]}
      (let [blocked?   (vf/day-blocked? iso belegung handovers)
            past?      (neg? (compare iso heute))
            selected?  (= iso selected)
            contained? (boolean (and selected counter (not selected?) (not bad?)
                                     (between-inclusive? selected counter iso)))
            free?      (not (or past? blocked? contained?))]
        {:iso         iso
         :tag         tag
         :outside?    false
         :past?       past?
         :blocked?    blocked?
         :contained?  contained?
         :free?       free?
         :selected?   selected?
         :bad?        (boolean bad?)
         :selectable? (not (or past? blocked?))
         :classes     (cond-> ["picker__cell" "picker__cell--day"]
                        past?      (conj "picker__cell--past")
                        blocked?   (conj "picker__cell--blocked")
                        contained? (conj "picker__cell--contained")
                        free?      (conj "picker__cell--free")
                        selected?  (conj "picker__cell--selected")
                        bad?       (conj "picker__cell--bad"))}))))

(rf/reg-sub ::picker
  (fn [[_ picker] _]
    [(rf/subscribe [::shown-month picker])
     (rf/subscribe [::auswahl])
     (rf/subscribe [::belegung])
     (rf/subscribe [::stay-error])
     (rf/subscribe [::heute])])
  (fn [[shown-month auswahl belegung stay-error heute] [_ picker]]
    (let [counter-key (if (= picker :anreise) :abreise :anreise)
          ctx {:heute     heute
               :belegung  belegung
               :handovers (vf/handover-days belegung)
               :selected  (get auswahl picker)
               :counter   (get auswahl counter-key)
               :bad?      (some? stay-error)}
          [y m] (map #(js/parseInt % 10) (.split shown-month "-"))]
      {:shown-month shown-month
       :year        y
       :month       m
       :selected    (get auswahl picker)
       :weeks       (mapv #(mapv (fn [c] (cell c ctx)) %) (kal/month-grid y m))})))
