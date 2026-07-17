(ns seiten.kontakt
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [comp.snippets :as snip]
            [seiten.templates :as templates]
            [psite-routing.core :as routing]))

(defn- page-body [req locale]
  [:section
   [:div.panel.mainpanel
    [:div.textabschnitt.py-4.px-4
     [:h1.title.is-2 (snip/kontakt locale)]
     [:form
      {:id     "kontakt-form"
       :action (routing/reverse-match req :api_kontakt {})
       :method "POST"}
      [:input {:type "hidden"
               :name "__anti-forgery-token"
               :value (:af-token req)}]
      [:input {:type "hidden" :name "time-spent" :value ""}]
      [:div.is-hidden
       [:label "Alter:"]
       [:input {:type "text" :name "age" :required false}]]
      [:div.field
       [:label.label (snip/name-snip locale)]
       [:input.input {:type "text" :name "name" :required true}]]
      [:div.field
       [:label.label (snip/email locale)]
       [:div.control
        [:input.input {:name "email" :type "email" :required true}]]]
      [:div.field
       [:label.label (snip/nachricht locale)]
       [:div.control
        [:textarea.textarea
         {:name "kontaktnachricht"
          :placeholder (snip/ihre-nachricht locale)
          :required true}]]]
      [:div.field
       [:div.control
        [:label.checkbox
         [:input {:name "datenschutz?" :required true :type "checkbox"}]
         (snip/ich-habe-die locale)
         [:a {:href "#"} (snip/datenschutzerklärung locale)]
         (snip/gelesen-akzeptiert locale)]]]
      [:div.control
       [:button.button.is-link {:type "submit" :value "Submit"}
        (snip/absenden locale)]]]
     [:script
      "(function(){var t=Date.now();var f=document.getElementById('kontakt-form');"
      "f.addEventListener('submit',function(){"
      "f.elements['time-spent'].value=String(Date.now()-t);});})();"]]]])

(defhandler handler [req]
  (p/let [locale (:locale req)]
    (templates/render-page
     req
     {:titel        (snip/kontakt locale)
      :beschreibung (snip/kontakt-meta-desc locale)}
     (page-body req locale))))
