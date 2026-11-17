(ns buchung.views.formular
  "Views of the booking SPA. They deref subscriptions and dispatch events;
   nothing here keeps or changes state. DOM structure and Bulma classes as
   the legacy Reagent views (old `buchung.core`), so the existing styles
   keep applying. Every user-visible text goes through `buchung.views.texte`."
  (:require [re-frame.core :as rf]
            [comp.snippets :as snip]
            [buchung.events :as ev]
            [buchung.subs :as subs]
            [buchung.react-slick :as slick]
            [buchung.views.datepicker :refer [datepicker]]
            [buchung.views.texte :as tx]))

(defn- <sub [q] @(rf/subscribe q))

(defn- target-int [e] (js/parseInt (.. e -target -value) 10))

;; ---------------------------------------------------------------------------
;; Apartment selection

(defn- gaestezahl-select [loc]
  (let [max-n (<sub [::subs/maximalbelegung])]
    [:select
     {:class        (when (<sub [::subs/ueberbelegung?]) "is-danger")
      :data-test-id "gaestezahl"
      :value        (:gaestezahl (<sub [::subs/auswahl]))
      :on-change    #(rf/dispatch [::ev/set-gaestezahl (target-int %)])}
     (for [n (range 1 (inc max-n))]
       ^{:key n} [:option {:value n} (tx/gaeste-option loc n)])]))

(defn- haustierzahl-select [loc]
  [:select
   {:data-test-id "haustierzahl"
    :value        (:haustierzahl (<sub [::subs/auswahl]))
    :on-change    #(rf/dispatch [::ev/set-haustierzahl (target-int %)])}
   (for [n (range 0 4)]
     ^{:key n} [:option {:value n} (tx/haustiere-option loc n)])])

(defn- wohnungsdropdown [loc]
  [:select
   {:data-test-id "wohnung"
    :value        (<sub [::subs/wohnungsid])
    :on-change    #(rf/dispatch [::ev/select-wohnung (target-int %)])}
   (for [w (<sub [::subs/wohnungen])]
     ^{:key (:id w)} [:option {:value (:id w)} (tx/wohnung-option loc (:name w))])])

(defn- belegungswarnung [loc]
  (when (<sub [::subs/ueberbelegung?])
    [:div.notification.is-warning.has-text-centered
     {:data-test-id "ueberbelegung-warnung"}
     (tx/ueberbelegung loc (<sub [::subs/wohnungsname]) (<sub [::subs/maximalbelegung]))]))

(defn- wohnungswahl [loc]
  [:nav.panel.is-primary.panel-background
   [:div.panel-heading
    [:div.field.has-addons.has-addons-centered
     [:p.control [:span.select [gaestezahl-select loc]]]
     [:p.control [:span.select [haustierzahl-select loc]]]
     [:p.control.is-hidden-mobile
      [:span.select [wohnungsdropdown loc]]]]]
   [slick/wohnungspicker {:wohnungen   (<sub [::subs/wohnungen])
                          :selected-id (<sub [::subs/wohnungsid])
                          :on-select   #(rf/dispatch [::ev/select-wohnung %])}]
   [:div.panel-block [belegungswarnung loc]]])

;; ---------------------------------------------------------------------------
;; Travel dates

(defn- reisecard [loc picker label uhrzeit]
  (let [datum (get (<sub [::subs/auswahl]) picker)
        model (<sub [::subs/picker picker])]
    [:div.column
     [:div.card.reisecard
      [:header.card-header
       [:p.card-header-title label ": " (tx/format-datum loc datum)
        (when datum uhrzeit)]]
      [:div.card-content
       [datepicker (assoc model
                          :locale   loc
                          :test-id  (name picker)
                          :on-pick  #(rf/dispatch [::ev/pick-date picker %])
                          :on-shift #(rf/dispatch [::ev/shift-month picker %1 %2]))]]]]))

(defn- reisedaten [loc]
  (let [{:keys [anreise abreise]} (<sub [::subs/auswahl])
        stay-error (<sub [::subs/stay-error])]
    [:nav.panel.is-primary.panel-background
     [:p.panel-heading (snip/reisedaten loc)]
     (when (empty? (<sub [::subs/belegung]))
       [:div.notification.is-warning
        (tx/belegung-nicht-geladen loc (<sub [::subs/wohnungsname]))])
     [:div.columns.has-text-centered
      [reisecard loc :anreise (snip/anreise-label loc) (snip/ab-15-uhr loc)]
      [reisecard loc :abreise (snip/abreise-label loc) (snip/bis-10-uhr loc)]]
     (when (and anreise abreise)
       [:div.panel-block
        (when stay-error
          [:div.notification.is-danger.belegungswarnung.has-text-centered
           {:data-test-id "datumsfehler"}
           (tx/stay-error loc stay-error)])])]))

;; ---------------------------------------------------------------------------
;; Contact data

(defn- field-error [loc path]
  (when (contains? (<sub [::subs/field-errors]) path)
    [:p.help.is-danger {:data-test-id (str "fehler-" (name (last path)))}
     (tx/field-error loc path)]))

(defn- input-row [loc label path & {:keys [type rows] :or {type "text"}}]
  (let [value   (or (<sub [::subs/field path]) "")
        invalid (contains? (<sub [::subs/field-errors]) path)
        attrs   {:class        (when invalid "is-danger")
                 :data-test-id (name (last path))
                 :value        value
                 ;; dispatch-sync: a controlled input must see its new value
                 ;; before the next render, or the caret jumps.
                 :on-change    #(rf/dispatch-sync [::ev/set-field path (.. % -target -value)])}]
    [:div.field
     [:label.label label]
     [:div.control
      (if (= type "textarea")
        [:textarea.textarea (assoc attrs :rows (or rows 3))]
        [:input.input (assoc attrs :type type)])]
     [field-error loc path]]))

(defn- checkbox-row [loc path label-content]
  [:div.field
   [:label.checkbox
    [:input
     {:type         "checkbox"
      :data-test-id (name (last path))
      :checked      (boolean (<sub [::subs/field path]))
      :on-change    #(rf/dispatch [::ev/set-field path (.. % -target -checked)])}]
    " " label-content]
   [field-error loc path]])

(defn- kontaktdaten-form [loc]
  [:div.columns
   [:div.column
    [input-row loc (snip/vorname loc)       [:gast :vorname]]
    [input-row loc (snip/nachname loc)      [:gast :nachname]]
    [input-row loc (snip/telefonnummer loc) [:gast :telefonnummer]]
    [input-row loc (snip/emailadresse loc)  [:gast :email] :type "email"]]
   [:div.column
    [input-row loc (snip/zusatznachricht loc) [:zusatznachricht] :type "textarea"]
    [checkbox-row loc [:datenschutz-gelesen?]
     (str (snip/ich-habe-die loc) (snip/datenschutzerklärung loc) (snip/gelesen-akzeptiert loc))]
    [checkbox-row loc [:mietbedingungen-gelesen?]
     (str (snip/ich-habe-die loc) (snip/mietbedingungen loc) (snip/gelesen-akzeptiert loc))]]])

;; ---------------------------------------------------------------------------
;; Request

(defn- anfrage-button [loc]
  (let [{:keys [state]} (<sub [::subs/post])]
    [:button.button
     {:type         "button"
      :data-test-id "anfrage-senden"
      :class        (case state
                      :pending "is-loading is-primary"
                      :failed  "is-danger"
                      :success "is-success"
                      "is-primary")
      ;; Clickable while the form is incomplete: the click marks the
      ;; missing fields (`::submit` sets `:submit-attempted?`).
      :disabled     (= state :pending)
      :on-click     #(rf/dispatch [::ev/submit])}
     (case state
       :failed  (str "↻ " (snip/erneut-versuchen loc))
       :success "✔"
       (snip/jetzt-anfragen-btn loc))]))

(defn- anfrage-meldungen [loc]
  (let [{:keys [state error]} (<sub [::subs/post])]
    [:<>
     (when (seq (<sub [::subs/field-errors]))
       [:div.notification.is-warning {:data-test-id "formular-unvollstaendig"}
        (tx/formular-unvollstaendig loc)])
     (when (= state :failed)
       [:div.notification.is-danger {:data-test-id "serverfehler"}
        (tx/server-error loc (<sub [::subs/wohnungsname]) error)])]))

(defn- anfrage-panel [loc]
  (let [{:keys [state]} (<sub [::subs/post])]
    [:nav.panel.is-primary.panel-background
     [:p.panel-heading (snip/reisedaten-frei loc)]
     [:div.panel-block.has-text-left.has-text-weight-bold
      {:data-test-id "preiszeile"}
      (tx/preiszeile loc (<sub [::subs/wohnungsname]) (<sub [::subs/preis]))]
     [:div.panel-block [kontaktdaten-form loc]]
     [:div.panel-block
      (case state
        :success [:div.notification.is-success {:data-test-id "anfrage-uebermittelt"}
                  (str "✔ " (snip/anfrage-uebermittelt loc))]
        [:div
         [anfrage-meldungen loc]
         [anfrage-button loc]])]]))

;; ---------------------------------------------------------------------------
;; Page

(defn- hauptform [loc]
  (let [{:keys [anreise abreise]} (<sub [::subs/auswahl])]
    [:section.section
     [:h1.title.is-3.buchung_ueberschrift (snip/buchungsanfrage loc)]
     [:div.container
      [wohnungswahl loc]
      [reisedaten loc]
      (when (and anreise abreise (not (<sub [::subs/stay-error])))
        [anfrage-panel loc])]]))

(defn- loading-screen [loc]
  [:div.loadscreen
   [:div.lds-dual-ring__msg (snip/lade-buchungsdaten loc)]
   [:div.lds-dual-ring [:div]]])

(defn- load-failed [loc]
  [:section.section
   [:div.container.has-text-centered
    [:div.notification.is-danger {:data-test-id "laden-fehlgeschlagen"}
     (snip/buchungsdaten-nicht-geladen loc)]
    [:button.button.is-primary
     {:type         "button"
      :data-test-id "erneut-laden"
      :on-click     #(rf/dispatch [::ev/fetch-data])}
     (str "↻ " (snip/erneut-versuchen loc))]]])

(defn root []
  (let [loc (<sub [::subs/locale])]
    (case (<sub [::subs/status])
      :ready       [hauptform loc]
      :load-failed [load-failed loc]
      [loading-screen loc])))
