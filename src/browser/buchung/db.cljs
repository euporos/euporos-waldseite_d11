(ns buchung.db
  "app-db of the booking SPA: plain data only (dates as ISO days, prices as
   delivered by /api/buchungsproxy, UI state under :ui), plus the pure
   functions from app-db to derived data that both events and subs need.
   Domain rules come from `buchung.*` under src/common.

   Defaults equal the legacy `buchung.core/state` (wohnung 1, 2 guests —
   now 2 adults, no children — 0 pets)."
  (:require [buchung.preis :as preis]
            [kalender.core :as kal]
            [preise.model :as pm]))

(def default-wohnungsid 1)
(def default-erwachsene 2)
(def default-haustierzahl 0)

(def default-maximalbelegung
  "Guests allowed when preise.edn has no `maximalbelegung` for the wohnung
   (legacy `maximalbelegung`)."
  6)

(def pickers #{:anreise :abreise})

(def feld-pfade
  "Paths `::set-field` may write to."
  #{[:gast :vorname]
    [:gast :nachname]
    [:gast :email]
    [:gast :telefonnummer]
    [:zusatznachricht]
    [:datenschutz-gelesen?]
    [:mietbedingungen-gelesen?]})

;; ---------------------------------------------------------------------------
;; Months

(defn month-of
  "ISO first day of the month containing ISO day `d`."
  [d]
  (first (kal/days-in-month d)))

(defn shift-month
  "ISO first day of the month `n` months (`:month`) or years (`:year`) away
   from the month of ISO day `d`."
  [d unit n]
  (let [[y m]  (map #(js/parseInt % 10) (.split d "-"))
        months (+ (* 12 y) (dec m) (if (= unit :year) (* 12 n) n))]
    (first (kal/days-in-month (quot months 12) (inc (mod months 12))))))

;; ---------------------------------------------------------------------------
;; Default state

(defn default-db
  "`locale` keyword; `heute` the local calendar date as ISO (both pickers
   start on its month, as the legacy picker did with `t/today`)."
  [locale heute]
  {:locale                   locale
   :heute                    heute
   :status                   :loading
   :wohnungen                []
   :preise                   nil
   :auswahl                  {:wohnungsid   default-wohnungsid
                              :erwachsene   default-erwachsene
                              :kinder-7-16  0
                              :kinder-bis-6 0
                              :haustierzahl default-haustierzahl
                              :anreise      nil
                              :abreise      nil}
   :gast                     {}
   :zusatznachricht          nil
   :datenschutz-gelesen?     false
   :mietbedingungen-gelesen? false
   :ui                       {:picker            {:anreise {:shown-month (month-of heute)}
                                                  :abreise {:shown-month (month-of heute)}}
                              :ueberbelegung?    false
                              :post              {:state :idle :error nil}
                              :submit-attempted? false}})

;; ---------------------------------------------------------------------------
;; Derived data (pure; subs memoize these, events call them directly)

(defn normalized-preise [db]
  (some-> (:preise db) pm/normalize))

(defn wohnung
  "The selected wohnung's map from /api/buchungsproxy, or nil."
  [db]
  (let [id (get-in db [:auswahl :wohnungsid])]
    (first (filter #(= id (:id %)) (:wohnungen db)))))

(defn maximalbelegung
  "Guests allowed in wohnung `wohnungsid`: legacy `maximalbelegung` on the
   reworked prices — the numparsed value through `int`, 6 when absent.
   `preise` normalized (nil before the data is loaded)."
  [preise wohnungsid]
  (or (some-> (get-in preise [:basisdaten wohnungsid :maximalbelegung]) int)
      default-maximalbelegung))

(defn optionen
  "Selectable counts for guest category `kategorie`: up to what the other
   categories leave of `max-n`; adults at least 1, children from 0."
  [auswahl max-n kategorie]
  (let [andere (- (preis/gaestezahl auswahl) (or (get auswahl kategorie) 0))
        von    (if (= kategorie :erwachsene) 1 0)]
    (range von (inc (max von (- max-n andere))))))

(defn begrenze-gaeste
  "`auswahl` with at most `max-n` guests in total, removing the youngest
   first (children up to 6, then 7–16, then adults down to 1). Returns
   `[auswahl gekuerzt?]`."
  [auswahl max-n]
  (loop [a auswahl [k & ks] (reverse preis/gaestekategorien)]
    (let [zuviel (- (preis/gaestezahl a) max-n)]
      (if (or (<= zuviel 0) (nil? k))
        [a (not= a auswahl)]
        (let [min-k (if (= k :erwachsene) 1 0)
              n     (or (get a k) 0)]
          (recur (assoc a k (max min-k (- n zuviel))) ks))))))

(defn- round-preis [preis]
  (into {} (for [[k v] preis] [k (when (number? v) (.round js/Math v))])))

(defn anfrage
  "The request payload (`specs.anfrage/payload`) for the current state —
   legacy `build-anfrage` (buchung/core.cljs:67-80), with `:gaestezahl`
   replaced by the three guest categories.
   `preise` normalized."
  [db preise]
  (let [{:keys [anreise abreise wohnungsid erwachsene kinder-7-16 kinder-bis-6
                haustierzahl] :as auswahl} (:auswahl db)
        {:keys [gast zusatznachricht datenschutz-gelesen? mietbedingungen-gelesen?]} db
        preis (when preise (preis/berechnen auswahl preise))]
    (cond-> {:anreise                  (when (kal/valid-iso? anreise) anreise)
             :abreise                  (when (kal/valid-iso? abreise) abreise)
             :erwachsene               erwachsene
             :kinder-7-16              kinder-7-16
             :kinder-bis-6             kinder-bis-6
             :haustierzahl             haustierzahl
             :wohnungsid               wohnungsid
             :wohnungsname             (or (:name (wohnung db)) "")
             :gast                     (or gast {})
             :datenschutz-gelesen?     (boolean datenschutz-gelesen?)
             :mietbedingungen-gelesen? (boolean mietbedingungen-gelesen?)}
      (seq zusatznachricht) (assoc :zusatznachricht zusatznachricht)
      preis                 (assoc :preis (round-preis preis)))))
