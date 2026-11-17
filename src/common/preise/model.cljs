(ns preise.model
  "The preise.edn price structure: malli schema of the on-disk format and
   its in-memory normalisation, shared by the booking SPA, the price admin
   and the server.

   On disk (owner decision 6, format stays as is) numbers are strings as the
   admin's `<input type=number>` delivers them: \"78\", \"115.00\", \"\" (empty
   field). Real numbers are accepted too, since a saved file may contain
   them. Season dates are ISO days. `:felder` and `:basisdaten` are keyed by
   integer wohnung id. Seasons may carry the UI's `:selected` flag (the
   legacy admin persists it; see doc/frontend-refactor-quirks.md)."
  (:require [kalender.core :as kal]
            [malli.core :as m]))

;; ---------------------------------------------------------------------------
;; Schema (on-disk shape)

(def number-string-regex
  "What an `<input type=number>` can hand over: empty, or a decimal number
   (optional sign, fraction, exponent)."
  #"^\s*$|^\s*[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?\s*$")

(def zahl
  "A numeric field value: number or number-string (\"\" allowed)."
  [:or number? [:re number-string-regex]])

(def iso-day
  [:and [:re kal/iso-day-regex] [:fn {:error/message "no valid calendar day"} kal/valid-iso?]])

(def wohnungs-id [:int {:min 1}])

(def saison-feld
  [:map
   [:preis_tag         {:optional true} zahl]
   [:preis_woche       {:optional true} zahl]
   [:mindestaufenthalt {:optional true} zahl]])

(def saison
  [:map
   [:id       :int]
   [:beginn   iso-day]
   [:ende     iso-day]
   [:felder   [:map-of wohnungs-id saison-feld]]
   [:selected {:optional true} :boolean]])

(def allgdaten
  [:map
   [:reinigung-unter      {:optional true} zahl]
   [:gaestebeitrag        {:optional true} zahl]
   [:aufschlag_zus_person {:optional true} zahl]
   [:aufschlag_haustier   {:optional true} zahl]
   [:energieaufschlag     {:optional true} zahl]])

(def basisdaten-wohnung
  [:map
   [:grundpreis                 {:optional true} zahl]
   [:mindestaufenthalt_standard {:optional true} zahl]
   [:maximalbelegung            {:optional true} zahl]
   [:standardbelegung           {:optional true} zahl]
   [:reinigung                  {:optional true} zahl]])

(def schema
  [:map
   [:saisonen   [:vector saison]]
   [:allgdaten  allgdaten]
   [:basisdaten [:map-of wohnungs-id basisdaten-wohnung]]])

(def ^:private validator (m/validator schema))
(def ^:private explainer (m/explainer schema))

(defn valid? [preise] (validator preise))

(defn explain [preise] (explainer preise))

;; ---------------------------------------------------------------------------
;; Normalisation (in memory only)

(def numeric-fields
  "Keys whose string values get parsed to numbers — same set as legacy
   `buchung.ajaxing/numeric-fields`."
  #{:mindestaufenthalt :mindestaufenthalt_standard :grundpreis
    :preis_woche :preis_tag :reinigung-unter :standardbelegung
    :maximalbelegung :reinigung :aufschlag_zus_person
    :aufschlag_haustier :gaestebeitrag :energieaufschlag})

(defn- numparse
  "Legacy semantics: a non-empty string at a numeric key is `js/parseFloat`ed
   unless that gives NaN; \"\", non-numbers and non-strings stay untouched.
   Walks maps and vectors at any depth."
  [v]
  (cond
    (map? v)        (into {} (map (fn [[k x]]
                                    [k (if (and (numeric-fields k) (string? x) (seq x))
                                         (let [n (js/parseFloat x)]
                                           (if (js/isNaN n) x n))
                                         (numparse x))]))
                          v)
    (sequential? v) (mapv numparse v)
    :else           v))

(defn normalize
  "On-disk preise → in-memory: numeric fields parsed (legacy `rework-preise`
   semantics) and season `:beginn`/`:ende` as UTC-midnight cljs-time
   instants (nil when unparseable). Unlike legacy no `:interval` is added."
  [preise]
  (-> preise
      (update :saisonen
              (fn [ss] (mapv (fn [s] (-> s
                                         (update :beginn kal/iso->date)
                                         (update :ende kal/iso->date)))
                             ss)))
      numparse))

;; ---------------------------------------------------------------------------
;; Season helpers

(defn next-id
  "Id for a new season: max existing id + 1, or 1 (legacy `add-saison!`)."
  [saisonen]
  (let [ids (keep :id saisonen)]
    (if (seq ids) (inc (apply max ids)) 1)))

(defn saison-by-id [saisonen id]
  (first (filter #(= id (:id %)) saisonen)))
