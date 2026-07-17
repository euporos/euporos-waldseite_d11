(ns seiten.kontaktform
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [clojure.string :as str]
            [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [comp.snippets :as snip]
            [setup.mail :as mail]
            [config.env :as env]
            [analytics.posthog :as ph]
            [seiten.templates :as templates]))

(defn- result-page [req heading body]
  (templates/render-page
   req
   {:titel heading :beschreibung ""}
   [:section
    [:div.panel.is-primary.mainpanel
     [:div.content.py-4.px-4
      [:h2.title.is-3.has-text-centered heading]
      body]]]))

(def ^:private min-time-spent-ms 4000)

(defn- validate [{:keys [age time-spent name email kontaktnachricht datenschutz?]}]
  (cond-> []
    (seq age)                       (conj :spam)
    (let [n (js/parseInt (or time-spent "") 10)]
      (or (js/isNaN n) (< n min-time-spent-ms)))
                                     (conj :too-fast)
    (str/blank? name)               (conj :name)
    (or (str/blank? email)
        (not (re-find #".+@.+\..+" (or email ""))))
                                     (conj :email)
    (str/blank? kontaktnachricht)   (conj :kontaktnachricht)
    (not datenschutz?)              (conj :datenschutz?)))

(defn- errormessage [locale e]
  (case e
    :datenschutz?     (snip/datenschutzregelung locale)
    :name             (snip/bitte-name locale)
    :email            (snip/bitte-email locale)
    :kontaktnachricht (snip/nachricht-leer locale)
    nil))

(defn- send-mail! [{:keys [name email kontaktnachricht]}]
  (mail/send-from-info
   {:replyTo email
    :to      (env/setting :contact-email)
    :subject (str "neue Kontaktanfrage von " name)
    :text    (str kontaktnachricht
                  "\n\n--------\ngesendet von " name " <" email ">")}))

(defhandler handler [req]
  (let [locale (:locale req)
        params (:params req)
        form   {:age              (:age params)
                :time-spent       (:time-spent params)
                :name             (:name params)
                :email            (:email params)
                :kontaktnachricht (:kontaktnachricht params)
                :datenschutz?     (boolean (:datenschutz? params))}
        errs   (validate form)]
    (cond
      (some #{:spam :too-fast} errs)
      (result-page req (snip/spam-erkannt locale)
                   [:p (snip/kein-bot locale)])

      (seq errs)
      (result-page req (snip/fehler-versand locale)
                   [:ul (for [e errs] [:li (errormessage locale e)])])

      :else
      (-> (p/let [_ (send-mail! form)]
            (ph/capture! req "Kontaktanfrage gesendet"
                         {:locale (name (:locale req))})
            (result-page req (snip/nachricht-verschickt locale)
                         [:p (snip/dank-nachricht locale)
                          (snip/antwort-kuerze locale)]))
          (.catch (fn [_]
                    (result-page req (snip/versand-fehlgeschlagen locale)
                                 [:p (snip/versand-fehler-text locale)])))))))
