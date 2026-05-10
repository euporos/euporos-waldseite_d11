(ns seiten.kontaktform
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [clojure.string :as str]
            [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [setup.mail :as mail]
            [config.env :as env]
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

(defn- validate [{:keys [age name email kontaktnachricht datenschutz?]}]
  (cond-> []
    (seq age)                       (conj :spam)
    (str/blank? name)               (conj :name)
    (or (str/blank? email)
        (not (re-find #".+@.+\..+" (or email ""))))
                                     (conj :email)
    (str/blank? kontaktnachricht)   (conj :kontaktnachricht)
    (not datenschutz?)              (conj :datenschutz?)))

(def errormessages
  {:datenschutz?     "Bitte akzeptieren Sie die Datenschutzerklärung."
   :name             "Bitte geben Sie Ihren Namen an."
   :email            "Bitte geben Sie eine gültige Emailadresse an."
   :kontaktnachricht "Nachrichtenfeld ist leer."})

(defn- send-mail! [{:keys [name email kontaktnachricht]}]
  (mail/send-from-info
   {:replyTo email
    :to      (env/setting :contact-email)
    :subject (str "neue Kontaktanfrage von " name)
    :text    (str kontaktnachricht
                  "\n\n--------\ngesendet von " name " <" email ">")}))

(defhandler handler [req]
  (let [params (:params req)
        form   {:age              (:age params)
                :name             (:name params)
                :email            (:email params)
                :kontaktnachricht (:kontaktnachricht params)
                :datenschutz?     (boolean (:datenschutz? params))}
        errs   (validate form)]
    (cond
      (some #{:spam} errs)
      (result-page req "Nachricht als Spam erkannt"
                   [:p "Falls Sie kein Bot sind, schreiben Sie uns bitte direkt."])

      (seq errs)
      (result-page req "Fehler beim Versenden der Nachricht"
                   [:ul (for [e errs] [:li (errormessages e)])])

      :else
      (-> (p/let [_ (send-mail! form)]
            (result-page req "Nachricht verschickt"
                         [:p "Vielen Dank für Ihre Nachricht! "
                          "Sie erhalten in Kürze eine Antwort."]))
          (.catch (fn [_]
                    (result-page req "Versand fehlgeschlagen"
                                 [:p "Leider ist beim Versenden Ihrer Nachricht ein Fehler aufgetreten."])))))))
