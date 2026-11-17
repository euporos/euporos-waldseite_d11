(ns kontakt.formular
  "Hiccup of the JavaScript-free contact form (rendered by `seiten.kontakt`
   and re-rendered by `seiten.kontaktform`). Config-free so the Node tests
   can render it. No script tags: the form works without JS by construction."
  (:require [comp.snippets :as snip]
            [specs.kontakt :as spec]))

(def ^:private honeypot-style
  ;; Off-screen rather than display:none — naive bots skip hidden inputs.
  "position:absolute;left:-10000px;top:auto;width:1px;height:1px;overflow:hidden;")

(defn datenschutz-seite
  "The einzelseite holding the privacy policy: the first row of `rows-de`
   (`{:id :titel}`, German titles) whose title mentions \"Datenschutz\".
   Returns `{:id n :titel localized-title}` (title taken from `rows-locale`
   when present, else the German one), or nil."
  [rows-de rows-locale]
  (when-let [{:keys [id titel]}
             (first (filter #(re-find #"(?i)datenschutz" (str (:titel %))) rows-de))]
    {:id    id
     :titel (or (:titel (first (filter #(= (str id) (str (:id %))) rows-locale)))
                titel)}))

(defn fehlertext
  "Localized inline error for a field key reported by `specs.kontakt`."
  [locale feld werte]
  (case feld
    :name             (snip/bitte-name locale)
    :email            (snip/bitte-email locale)
    :kontaktnachricht (if (> (count (:kontaktnachricht werte)) spec/max-nachricht)
                        (snip/nachricht-zu-lang locale)
                        (snip/nachricht-leer locale))
    :datenschutz?     (snip/datenschutzregelung locale)
    nil))

(defn- hilfe [locale fehler werte feld]
  (when (contains? fehler feld)
    [:p.help.is-danger (fehlertext locale feld werte)]))

(defn- klasse [base fehler feld]
  (if (contains? fehler feld) (str base " is-danger") base))

(defn formular
  "Contact form hiccup.
   - `:action`, `:af-token`        form target and CSRF token
   - `:token`                      `{:ts :sig}` from `kontakt.pruefung/token`
   - `:werte`                      values to prefill (re-render keeps input)
   - `:fehler`                     set of invalid field keys → inline errors
   - `:hinweis`                    `:erneut-senden` → note above the form
   - `:datenschutz-href`           link to the privacy page, or nil"
  [{:keys [locale action af-token token werte fehler hinweis datenschutz-href]}]
  (let [fehler (or fehler #{})
        hilfe  (partial hilfe locale fehler werte)]
    [:form
     {:id     "kontakt-form"
      :action action
      :method "POST"}
     [:input {:type "hidden" :name "__anti-forgery-token" :value af-token}]
     [:input {:type "hidden" :name "ts" :value (:ts token)}]
     [:input {:type "hidden" :name "sig" :value (:sig token)}]
     (when (= :erneut-senden hinweis)
       [:div.notification.is-warning (snip/kontakt-erneut-senden locale)])
     [:div {:aria-hidden "true" :style honeypot-style}
      [:label {:for "kontakt-website"} "Website"]
      [:input {:type         "text"
               :id           "kontakt-website"
               :name         "website"
               :value        ""
               :autocomplete "off"
               :tabindex     "-1"}]]
     [:div.field
      [:label.label {:for "kontakt-name"} (snip/name-snip locale)]
      [:div.control
       [:input {:class     (klasse "input" fehler :name)
                :type      "text"
                :id        "kontakt-name"
                :name      "name"
                :maxlength spec/max-name
                :required  true
                :value     (:name werte "")}]]
      (hilfe :name)]
     [:div.field
      [:label.label {:for "kontakt-email"} (snip/email locale)]
      [:div.control
       [:input {:class     (klasse "input" fehler :email)
                :type      "email"
                :id        "kontakt-email"
                :name      "email"
                :maxlength spec/max-email
                :required  true
                :value     (:email werte "")}]]
      (hilfe :email)]
     [:div.field
      [:label.label {:for "kontakt-nachricht"} (snip/nachricht locale)]
      [:div.control
       [:textarea {:class       (klasse "textarea" fehler :kontaktnachricht)
                   :id          "kontakt-nachricht"
                   :name        "kontaktnachricht"
                   :maxlength   spec/max-nachricht
                   :placeholder (snip/ihre-nachricht locale)
                   :required    true}
        (:kontaktnachricht werte "")]]
      (hilfe :kontaktnachricht)]
     [:div.field
      [:div.control
       [:label.checkbox
        [:input (cond-> {:name "datenschutz?" :required true :type "checkbox"}
                  (:datenschutz? werte) (assoc :checked true))]
        (snip/ich-habe-die locale)
        (if datenschutz-href
          [:a {:href datenschutz-href :target "_blank"}
           (snip/datenschutzerklärung locale)]
          (snip/datenschutzerklärung locale))
        (snip/gelesen-akzeptiert locale)]]
      (hilfe :datenschutz?)]
     [:div.control
      [:button.button.is-link {:type "submit" :value "Submit"}
       (snip/absenden locale)]]]))
