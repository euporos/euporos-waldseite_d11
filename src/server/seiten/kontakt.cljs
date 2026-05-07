(ns seiten.kontakt
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [seiten.templates :as templates]
            [psite-routing.core :as routing]))

(defn- page-body [req]
  [:section
   [:div.panel.mainpanel
    [:div.textabschnitt.py-4.px-4
     [:h1.title.is-2 "Kontakt"]
     [:form
      {:action (routing/reverse-match req :api_kontakt {})
       :method "POST"}
      [:input {:type "hidden"
               :name "__anti-forgery-token"
               :value (:af-token req)}]
      [:div.is-hidden
       [:label "Alter:"]
       [:input {:type "text" :name "age" :required false}]]
      [:div.field
       [:label.label "Name"]
       [:input.input {:type "text" :name "name" :required true}]]
      [:div.field
       [:label.label "Email"]
       [:div.control
        [:input.input {:name "email" :type "email" :required true}]]]
      [:div.field
       [:label.label "Nachricht"]
       [:div.control
        [:textarea.textarea
         {:name "kontaktnachricht"
          :placeholder "Ihre Nachricht"
          :required true}]]]
      [:div.field
       [:div.control
        [:label.checkbox
         [:input {:name "datenschutz?" :required true :type "checkbox"}]
         " Ich habe die "
         [:a {:href "#"} "Datenschutzerklärung"]
         " gelesen und akzeptiert."]]]
      [:div.control
       [:button.button.is-link {:type "submit" :value "Submit"}
        "Absenden"]]]]]])

(defhandler handler [req]
  (p/let [_ nil]
    (templates/render-page
     req
     {:titel        "Kontakt"
      :beschreibung "Bei Fragen rund um unsere Ferienwohnungen können Sie uns hier kontaktieren."}
     (page-body req))))
