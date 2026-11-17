(ns buchung.mailtext
  "Subject and plain-text body of the booking-request email. Pure (no
   config, no transport) so tests can load it; `buchung.mailer` sends it."
  (:require [clojure.string :as str]))

(defn- format-date
  "ISO date 'yyyy-MM-dd' → 'dd.MM.yyyy'. Anything else passes through."
  [s]
  (if-let [[_ y m d] (and (string? s) (re-matches #"(\d{4})-(\d{2})-(\d{2})" s))]
    (str d "." m "." y)
    (or s "")))

(defn- haustier-suffix [n]
  (cond
    (and (number? n) (> n 1)) (str ", " n " Haustiere")
    (= 1 n)                   ", 1 Haustier"
    :else                     ""))

(defn subject [{:keys [gast]}]
  (str "Neue Buchungsanfrage von "
       (:vorname gast) " " (:nachname gast)))

(defn- hinweis-zeilen
  "Server-side warnings for the owner (e.g. the occupancy calendar could not
   be checked), one '⚠ …' line each, followed by a blank line."
  [hinweise]
  (when (seq hinweise)
    (str (str/join (map #(str "⚠ " % "\n") hinweise)) "\n")))

(defn body
  "Mail text. `preis` is the server-computed `buchung.preis/berechnen`
   result; `hinweise` optional warning strings shown at the top."
  [{:keys [gast wohnungsname anreise abreise gaestezahl
           haustierzahl preis zusatznachricht hinweise]}]
  (let [{:keys [gesamtsumme gaestebeitrag energieaufschlag]} preis
        zusatz (if (str/blank? zusatznachricht)
                 "Es wurde keine Zusatznachricht hinterlassen."
                 (str "Zusatznachricht:\n" zusatznachricht))]
    (str
     (hinweis-zeilen hinweise)
     (:vorname gast) " " (:nachname gast)
     " hat eine neue Buchungsanfrage gestellt:\n\n"
     "Wohnung: " wohnungsname "\n"
     "vom " (format-date anreise) " bis " (format-date abreise) "\n"
     gaestezahl " Personen" (haustier-suffix haustierzahl) ".\n"
     (when gesamtsumme
       (str "\nBerechneter Preis: " gesamtsumme "€"
            (when gaestebeitrag (str " (+ " gaestebeitrag "€ Gästebeitrag"))
            (when (and energieaufschlag (> energieaufschlag 0))
              (str ", + " energieaufschlag "€ Energieaufschlag"))
            (when gaestebeitrag ")")
            "\n"))
     "\n" zusatz "\n\n"
     "→ Email: " (:email gast) "\n"
     "→ Tel.:  " (:telefonnummer gast) "\n")))
