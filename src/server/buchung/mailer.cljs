(ns buchung.mailer
  "Builds and dispatches the booking-request email.

   Lifted from the old waldseite mailmaking namespace, but the cljstache
   templating dep has been dropped — one inline String/format-style body
   is plenty for a single template."
  (:require [clojure.string :as str]
            [config.env :as env]
            [setup.mail :as mail]
            [taoensso.timbre :refer [info]]))

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

(defn- subject [{:keys [gast]}]
  (str "Neue Buchungsanfrage von "
       (:vorname gast) " " (:nachname gast)))

(defn- body [{:keys [gast wohnungsname anreise abreise gaestezahl
                     haustierzahl preis zusatznachricht]}]
  (let [{:keys [gesamtsumme gaestebeitrag energieaufschlag]} preis
        zusatz (if (str/blank? zusatznachricht)
                 "Es wurde keine Zusatznachricht hinterlassen."
                 (str "Zusatznachricht:\n" zusatznachricht))]
    (str
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

(defn send!
  "Sends the booking-request email. Returns the underlying send promise."
  [anfrage]
  (let [recipient (env/setting :contact-email)]
    (info "buchung.mailer: sending request for"
          (-> anfrage :gast :email) "→" recipient)
    (mail/send-from-info
     {:to      recipient
      :replyTo (-> anfrage :gast :email)
      :subject (subject anfrage)
      :text    (body anfrage)})))
