(ns seiten.kontaktform
  "POST /:locale/kontaktform — submit of the JavaScript-free contact form.
   All checks live in `kontakt.pruefung`; this namespace wires in the real
   mailer, logger and analytics and renders the outcome. Invalid input or a
   bad/expired form token re-renders the form with the input preserved.
   Rate-limited per IP (bucket `:kontakt`) via `wrap-rate-limit-page`."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [comp.snippets :as snip]
            [setup.mail :as mail]
            [config.env :as env]
            [analytics.posthog :as ph]
            [kontakt.pruefung :as pruefung]
            [psite-rate-limit.core :as rate-limit]
            [seiten.kontakt :as kontakt]
            [seiten.templates :as templates]
            [taoensso.timbre :refer [info]]))

(defn- result-page [req heading body]
  (templates/render-page
   req
   {:titel heading :beschreibung ""}
   [:section
    [:div.panel.is-primary.mainpanel
     [:div.content.py-4.px-4
      [:h2.title.is-3.has-text-centered heading]
      body]]]))

(defn- deps [req]
  {:schluessel pruefung/schluessel
   :jetzt      #(.now js/Date)
   :send-mail! (fn [m] (mail/send-from-info (assoc m :to (env/setting :contact-email))))
   :log!       (fn [msg] (info msg))
   :track!     (fn [event props]
                 (ph/capture! req event (assoc props :locale (some-> (:locale req) name))))})

(defn- render [req {:keys [seite] :as outcome}]
  (let [locale (:locale req)]
    (case seite
      :formular
      (kontakt/formular-seite req (select-keys outcome [:werte :fehler :hinweis]))

      :gesendet
      (result-page req (snip/nachricht-verschickt locale)
                   [:p (snip/dank-nachricht locale) (snip/antwort-kuerze locale)])

      :fehlgeschlagen
      (result-page req (snip/versand-fehlgeschlagen locale)
                   [:p (snip/versand-fehler-text locale)]))))

(defhandler handler [req]
  (.then (pruefung/respond (deps req) (:params req))
         (partial render req)))

(defn wrap-rate-limit-page
  "`psite-rate-limit` bucket `:kontakt`, but a denied request gets a
   localized HTML page (status 429, `Retry-After` kept) instead of the
   limiter's EDN body."
  [handler]
  (let [limited ((rate-limit/wrap-rate-limit :kontakt) handler)]
    (fn [req res raise]
      (limited req
               (fn [resp]
                 (if (= 429 (:status resp))
                   (let [locale (:locale req)]
                     (-> (result-page req (snip/zu-viele-anfragen locale)
                                      [:p (snip/zu-viele-anfragen-text locale)])
                         (.then (fn [page]
                                  (res (-> page
                                           (assoc :status 429)
                                           (assoc-in [:headers "Retry-After"]
                                                     (get-in resp [:headers "Retry-After"]))))))
                         (.catch raise)))
                   (res resp)))
               raise))))
