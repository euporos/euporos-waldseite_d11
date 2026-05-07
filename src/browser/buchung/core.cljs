(ns buchung.core
  "Reagent SPA for booking requests. Mounted into #mainframe by
   seiten.buchung. Single-namespace entry point so shadow-cljs can ship
   it as its own browser module."
  (:require [cljs-time.core :as t]
            [malli.core :as m]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.ratom :as ratom]
            [specs.anfrage :as specs]
            [buchung.ajaxing :as ajx]
            [buchung.datechecking :as dtc]
            [buchung.datepicker :refer [datepicker]]
            [buchung.preisberechnung :as prs]
            [buchung.react-slick :as slick]
            [buchung.utils :as u]))

;; -------------------------------------------------------------- state

(defonce data (r/atom nil))

(defonce wohnungen (r/cursor data [:wohnungen]))
(defonce preise    (r/cursor data [:preise]))

(defonce state
  (r/atom {:ausgew-whg-id 1
           :gaestezahl    2
           :haustierzahl  0
           :anreise       nil
           :abreise       nil
           :gast          {}}))

(defonce anreise         (r/cursor state [:anreise]))
(defonce abreise         (r/cursor state [:abreise]))
(defonce ausgew-whg-id   (r/cursor state [:ausgew-whg-id]))
(defonce ueberbelegung?  (r/cursor state [:ueberbelegung?]))
(defonce poststate       (r/cursor state [:poststate]))

(def aufenthalt
  (ratom/reaction
   (when (and @anreise @abreise (t/before? @anreise @abreise))
     (t/interval @anreise @abreise))))

(def belegung
  (ratom/reaction
   (u/get-by-id @wohnungen @ausgew-whg-id :belegung)))

(def wohnungsname
  (ratom/reaction
   (u/get-by-id @wohnungen @ausgew-whg-id :name)))

(def daterror
  (ratom/reaction
   (dtc/err-dates-bad? @anreise @abreise @aufenthalt
                       @ausgew-whg-id @wohnungen @preise)))

(def preis
  (ratom/reaction
   (prs/berechnen @aufenthalt (:gaestezahl @state) (:haustierzahl @state)
                  @ausgew-whg-id @preise)))

;; --------------------------------------------------------- form payload

(defn- build-anfrage [state preis wohnungsname]
  (let [{:keys [anreise abreise ausgew-whg-id gast gaestezahl haustierzahl
                datenschutz-gelesen? mietbedingungen-gelesen? zusatznachricht]} state]
    (cond-> {:anreise                  (when (t/date? anreise) (u/to-iso-day anreise))
             :abreise                  (when (t/date? abreise) (u/to-iso-day abreise))
             :gaestezahl               gaestezahl
             :haustierzahl             haustierzahl
             :wohnungsid               ausgew-whg-id
             :wohnungsname             (or wohnungsname "")
             :gast                     (or gast {})
             :datenschutz-gelesen?     (boolean datenschutz-gelesen?)
             :mietbedingungen-gelesen? (boolean mietbedingungen-gelesen?)}
      (seq zusatznachricht) (assoc :zusatznachricht zusatznachricht)
      preis                 (assoc :preis (u/map-vals #(when (number? %) (.round js/Math %)) preis)))))

(def anfrage
  (ratom/reaction (build-anfrage @state @preis @wohnungsname)))

;; ------------------------------------------------------------ helpers

(defn- maximalbelegung [whg-id preise]
  (or (some-> (get-in preise [:basisdaten whg-id :maximalbelegung]) int)
      6))

;; ------------------------------------------------------------ widgets

(defn- gaestezahl-select [max-belegung]
  (let [cursor (r/cursor state [:gaestezahl])]
    (when (> @cursor max-belegung)
      (reset! ueberbelegung? true)
      (reset! cursor max-belegung))
    [:select
     {:class     (when @ueberbelegung? "is-danger")
      :value     @cursor
      :on-change (fn [e]
                   (reset! ueberbelegung? nil)
                   (reset! cursor (js/parseInt (.. e -target -value))))}
     (for [n (range 1 (inc max-belegung))]
       ^{:key n} [:option {:value n} (str n (if (> n 1) " Gäste" " Gast"))])]))

(defn- haustierzahl-select []
  (let [cursor (r/cursor state [:haustierzahl])]
    [:select
     {:value     @cursor
      :on-change #(reset! cursor (js/parseInt (.. % -target -value)))}
     (for [n (range 0 4)]
       ^{:key n} [:option {:value n} (str n (if (= n 1) " Haustier" " Haustiere"))])]))

(defn- wohnungsdropdown [!ausgew-whg-id wohnungen]
  [:select
   {:value     @!ausgew-whg-id
    :on-change #(reset! !ausgew-whg-id (js/parseInt (.. % -target -value)))}
   (for [w wohnungen]
     ^{:key (:id w)} [:option {:value (:id w)} (str "Wohnung " (:name w))])])

(defn- belegungswarnung []
  (when @ueberbelegung?
    [:div.notification.is-warning.has-text-centered
     "Bitte beachten Sie, dass die Wohnung »" @wohnungsname
     "« maximal " (maximalbelegung @ausgew-whg-id @preise)
     " Personen Platz bietet."]))

;; --------------------------------------------------------------- panels

(defn- wohnungswahl []
  [:nav.panel.is-primary.panel-background
   [:div.panel-heading
    [:div.field.has-addons.has-addons-centered
     [:p.control [:span.select [gaestezahl-select (maximalbelegung @ausgew-whg-id @preise)]]]
     [:p.control [:span.select [haustierzahl-select]]]
     [:p.control.is-hidden-mobile
      [:span.select [wohnungsdropdown ausgew-whg-id @wohnungen]]]]]
   [slick/wohnungspicker-stateful state @wohnungen ausgew-whg-id ueberbelegung?]
   [:div.panel-block [belegungswarnung]]])

(defn- reisedaten []
  [:nav.panel.is-primary.panel-background
   [:p.panel-heading "Reisedaten"]
   (when (empty? @belegung)
     [:div.notification.is-warning
      "Belegungsdaten für »" @wohnungsname "« konnten nicht geladen werden — Ihre Wunschdaten könnten u.U. nicht frei sein."])
   [:div.columns.has-text-centered
    [:div.column
     [:div.card.reisecard
      [:header.card-header
       [:p.card-header-title "Anreise: " (u/format-date-de @anreise)
        (when @anreise " ab 15:00 Uhr")]]
      [:div.card-content
       [datepicker anreise {:counterdate @abreise
                            :daterror    @daterror
                            :belegung    @belegung}]]]]
    [:div.column
     [:div.card.reisecard
      [:header.card-header
       [:p.card-header-title "Abreise: " (u/format-date-de @abreise)
        (when @abreise " bis 10:00 Uhr")]]
      [:div.card-content
       [datepicker abreise {:counterdate @anreise
                            :daterror    @daterror
                            :belegung    @belegung}]]]]]
   (when (and @anreise @abreise)
     [:div.panel-block [dtc/anzeige @daterror nil]])])

(defn- input-row [label path & {:keys [type rows]
                                :or   {type "text"}}]
  [:div.field {:key (str path)}
   [:label.label label]
   [:div.control
    (if (= type "textarea")
      [:textarea.textarea
       {:rows      (or rows 3)
        :value     (or (get-in @state path) "")
        :on-change #(swap! state assoc-in path (.. % -target -value))}]
      [:input.input
       {:type      type
        :value     (or (get-in @state path) "")
        :on-change #(swap! state assoc-in path (.. % -target -value))}])]])

(defn- checkbox-row [path label-content]
  [:div.field {:key (str path)}
   [:label.checkbox
    [:input
     {:type      "checkbox"
      :checked   (boolean (get-in @state path))
      :on-change #(swap! state assoc-in path (.. % -target -checked))}]
    " " label-content]])

(defn- kontaktdaten-form []
  [:div.columns
   [:div.column
    [input-row "Vorname"        [:gast :vorname]]
    [input-row "Nachname"       [:gast :nachname]]
    [input-row "Telefonnummer"  [:gast :telefonnummer]]
    [input-row "Emailadresse"   [:gast :email] :type "email"]]
   [:div.column
    [input-row "Zusatznachricht" [:zusatznachricht] :type "textarea"]
    [checkbox-row [:datenschutz-gelesen?]
     "Ich habe die Datenschutzerklärung gelesen und akzeptiert."]
    [checkbox-row [:mietbedingungen-gelesen?]
     "Ich habe die Mietbedingungen gelesen und akzeptiert."]]])

(defn- anfrage-button []
  (let [ok? (m/validate specs/payload @anfrage)]
    [:button.button
     {:class    (case @poststate
                  :pending "is-loading is-primary"
                  :failed  "is-danger"
                  :success "is-success"
                  "is-primary")
      :disabled (not ok?)
      :on-click #(ajx/post-anfrage! @anfrage poststate)}
     (case @poststate
       :failed  "↻ Erneut versuchen"
       :success "✔"
       "Jetzt anfragen!")]))

(defn- anfrage-panel []
  [:nav.panel.is-primary.panel-background
   [:p.panel-heading "Reisedaten sind frei! Stellen Sie jetzt Ihre unverbindliche Anfrage…"]
   [:div.panel-block.has-text-left.has-text-weight-bold
    (:tageszahl @preis) " Nächte in der Wohnung " @wohnungsname
    " für voraussichtlich " (:gesamtsumme @preis) "€"
    " (+ " (:gaestebeitrag @preis) "€ Gästebeitrag"
    (when (and (:energieaufschlag @preis) (> (:energieaufschlag @preis) 0))
      (str ", + " (:energieaufschlag @preis) "€ Energieaufschlag"))
    ")"]
   [:div.panel-block [kontaktdaten-form]]
   [:div.panel-block
    (case @poststate
      :success [:div.notification.is-success "✔ Ihre Anfrage wurde erfolgreich übermittelt."]
      [anfrage-button])]])

(defn- hauptform []
  [:section.section
   [:h1.title.is-3.buchung_ueberschrift "Buchungsanfrage"]
   [:div.container
    [wohnungswahl]
    [reisedaten]
    (when (and @anreise @abreise (not @daterror))
      [anfrage-panel])]])

;; --------------------------------------------------------- mount

(defn ^:dev/after-load start []
  (rdom/render [hauptform] (.getElementById js/document "mainframe")))

(defn ^:export main []
  (rdom/render
   [:div.container.has-text-centered
    [:p "Lade Buchungsdaten…"]]
   (.getElementById js/document "mainframe"))
  (ajx/fetch-data! preise wohnungen start))
