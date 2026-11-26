(ns buchung.views.texte
  "Localized texts of the booking SPA, as pure functions of the locale and
   the data the state layer produces (ISO dates, error maps, field paths).
   The only place where domain data becomes user-visible text; views call
   these and render the strings. No React, so tests run in Node."
  (:require [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [comp.snippets :as snip]))

;; ---------------------------------------------------------------------------
;; Calendar labels

(defn weekday-short [loc]
  (snip/weekday-short loc))

(defn month-label
  "Long month name, `month` 1-based."
  [loc month]
  (nth (snip/month-long loc) (dec month)))

(defn format-datum
  "ISO day → the locale's `datum-format` (legacy `legacy.buchung.utils/format-date`).
   cljs-time read that pattern as `DD` = two-digit day, `MM` = two-digit
   month, `y` = full year — e.g. \"DD.MM.y\" → \"05.03.2027\". Nil for nil."
  [loc iso]
  (when (string? iso)
    (let [[y m d] (str/split iso #"-")]
      (-> (snip/datum-format loc)
          (str/replace "DD" d)
          (str/replace "MM" m)
          (str/replace "y" y)))))

;; ---------------------------------------------------------------------------
;; Selectors

(defn gaeste-option
  "Option label of a guest-category dropdown, e.g. \"2 Erwachsene (17+)\"."
  [loc kategorie n]
  (let [[eins mehr alter] (case kategorie
                            :erwachsene   [snip/erwachsener snip/erwachsene snip/alter-erwachsene]
                            :kinder-7-16  [snip/kind snip/kinder snip/alter-7-16]
                            :kinder-bis-6 [snip/kind snip/kinder snip/alter-bis-6])]
    (str n " " (if (= n 1) (eins loc) (mehr loc)) " (" (alter loc) ")")))

(defn haustiere-option [loc n]
  (str n " " (if (= n 1) (snip/haustier loc) (snip/haustiere loc))))

(defn wohnung-option [loc wohnungsname]
  (str (snip/wohnung loc) " " wohnungsname))

(defn ueberbelegung [loc wohnungsname max-n]
  (gstring/format (snip/ueberbelegung-warnung loc) wohnungsname max-n))

(defn belegung-nicht-geladen [loc wohnungsname]
  (gstring/format (snip/belegung-nicht-geladen loc) wohnungsname))

;; ---------------------------------------------------------------------------
;; Stay errors (shared `buchung.verfuegbarkeit/stay-error` data)

(defn stay-error
  "The legacy `err-dates-bad?` message for a stay error, or nil."
  [loc {:keys [error min wohnung]}]
  (case error
    nil                   nil
    :anreise-nach-abreise (snip/err-anreise-vor-abreise loc)
    :belegt               (snip/err-belegt loc)
    :mindestaufenthalt    (gstring/format (snip/err-mindestaufenthalt loc) wohnung min)
    (snip/err-server-allgemein loc)))

;; ---------------------------------------------------------------------------
;; Price line

(defn preiszeile
  "Legacy price summary text: nights, apartment and total, the guest
   contribution, the energy surcharge when positive, closing paren."
  [loc wohnungsname {:keys [tageszahl gesamtsumme gaestebeitrag energieaufschlag]}]
  (str (gstring/format (snip/preiszeile loc) tageszahl wohnungsname gesamtsumme)
       (gstring/format (snip/gaestebeitrag-teil loc) gaestebeitrag)
       (when (and energieaufschlag (> energieaufschlag 0))
         (gstring/format (snip/energieaufschlag-teil loc) energieaufschlag))
       ")"))

;; ---------------------------------------------------------------------------
;; Field validation (paths from `specs.anfrage/invalid-fields`)

(defn field-error
  "Message for an invalid field path, e.g. `[:gast :email]`."
  [loc path]
  (case path
    [:gast :email]              (snip/err-email-ungueltig loc)
    [:datenschutz-gelesen?]     (snip/err-zustimmung-fehlt loc)
    [:mietbedingungen-gelesen?] (snip/err-zustimmung-fehlt loc)
    [:zusatznachricht]          (snip/err-nachricht-zu-lang loc)
    (snip/err-feld-leer loc)))

(defn formular-unvollstaendig [loc]
  (snip/err-formular-unvollstaendig loc))

;; ---------------------------------------------------------------------------
;; Server answers (`buchung.events/server-error`)

(defn server-error
  "Message for the EDN error a rejected request came back with.
   `wohnungsname` fills the apartment placeholders (the server does not
   echo it)."
  [loc wohnungsname {:keys [error min max]}]
  (case error
    :belegt               (snip/err-belegt loc)
    :anreise-nach-abreise (snip/err-anreise-vor-abreise loc)
    :mindestaufenthalt    (if min
                            (gstring/format (snip/err-mindestaufenthalt loc) wohnungsname min)
                            (snip/err-server-allgemein loc))
    :ueberbelegung        (if max
                            (ueberbelegung loc wohnungsname max)
                            (snip/err-server-allgemein loc))
    :invalid              (snip/err-server-ungueltig loc)
    (snip/err-server-allgemein loc)))
