(ns buchung.mailer
  "Builds and dispatches the booking-request email.

   Lifted from the old waldseite mailmaking namespace, but the cljstache
   templating dep has been dropped — the text is built by the pure
   `buchung.mailtext`."
  (:require [buchung.mailtext :refer [subject body]]
            [config.env :as env]
            [setup.mail :as mail]
            [taoensso.timbre :refer [info]]))

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
