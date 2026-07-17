(ns buchung.core
  "Reagent SPA for booking requests. Mounted into #mainframe by
   seiten.buchung. Single-namespace entry point so shadow-cljs can ship
   it as its own browser module."
  (:require [cljs-time.core :as t]
            [goog.string :as gstring]
            [goog.string.format]
            [malli.core :as m]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.ratom :as ratom]
            [comp.snippets :as snip]
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
       ^{:key n} [:option {:value n}
                  (str n " " (if (> n 1) (snip/gaeste @u/locale) (snip/gast @u/locale)))])]))

(defn- haustierzahl-select []
  (let [cursor (r/cursor state [:haustierzahl])]
    [:select
     {:value     @cursor
      :on-change #(reset! cursor (js/parseInt (.. % -target -value)))}
     (for [n (range 0 4)]
       ^{:key n} [:option {:value n}
                  (str n " " (if (= n 1) (snip/haustier @u/locale) (snip/haustiere @u/locale)))])]))

(defn- wohnungsdropdown [!ausgew-whg-id wohnungen]
  [:select
   {:value     @!ausgew-whg-id
    :on-change #(reset! !ausgew-whg-id (js/parseInt (.. % -target -value)))}
   (for [w wohnungen]
     ^{:key (:id w)} [:option {:value (:id w)}
                      (str (snip/wohnung @u/locale) " " (:name w))])])

(defn- belegungswarnung []
  (when @ueberbelegung?
    [:div.notification.is-warning.has-text-centered
     (gstring/format (snip/ueberbelegung-warnung @u/locale)
                     @wohnungsname (maximalbelegung @ausgew-whg-id @preise))]))

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
   [:p.panel-heading (snip/reisedaten @u/locale)]
   (when (empty? @belegung)
     [:div.notification.is-warning
      (gstring/format (snip/belegung-nicht-geladen @u/locale) @wohnungsname)])
   [:div.columns.has-text-centered
    [:div.column
     [:div.card.reisecard
      [:header.card-header
       [:p.card-header-title (snip/anreise-label @u/locale) ": " (u/format-date @anreise)
        (when @anreise (snip/ab-15-uhr @u/locale))]]
      [:div.card-content
       [datepicker anreise {:counterdate @abreise
                            :daterror    @daterror
                            :belegung    @belegung}]]]]
    [:div.column
     [:div.card.reisecard
      [:header.card-header
       [:p.card-header-title (snip/abreise-label @u/locale) ": " (u/format-date @abreise)
        (when @abreise (snip/bis-10-uhr @u/locale))]]
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
  (let [loc @u/locale]
    [:div.columns
     [:div.column
      [input-row (snip/vorname loc)       [:gast :vorname]]
      [input-row (snip/nachname loc)      [:gast :nachname]]
      [input-row (snip/telefonnummer loc) [:gast :telefonnummer]]
      [input-row (snip/emailadresse loc)  [:gast :email] :type "email"]]
     [:div.column
      [input-row (snip/zusatznachricht loc) [:zusatznachricht] :type "textarea"]
      [checkbox-row [:datenschutz-gelesen?]
       (str (snip/ich-habe-die loc) (snip/datenschutzerklärung loc) (snip/gelesen-akzeptiert loc))]
      [checkbox-row [:mietbedingungen-gelesen?]
       (str (snip/ich-habe-die loc) (snip/mietbedingungen loc) (snip/gelesen-akzeptiert loc))]]]))

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
       :failed  (str "↻ " (snip/erneut-versuchen @u/locale))
       :success "✔"
       (snip/jetzt-anfragen-btn @u/locale))]))

(defn- anfrage-panel []
  (let [loc @u/locale]
    [:nav.panel.is-primary.panel-background
     [:p.panel-heading (snip/reisedaten-frei loc)]
     [:div.panel-block.has-text-left.has-text-weight-bold
      (gstring/format (snip/preiszeile loc)
                      (:tageszahl @preis) @wohnungsname (:gesamtsumme @preis))
      (gstring/format (snip/gaestebeitrag-teil loc) (:gaestebeitrag @preis))
      (when (and (:energieaufschlag @preis) (> (:energieaufschlag @preis) 0))
        (gstring/format (snip/energieaufschlag-teil loc) (:energieaufschlag @preis)))
      ")"]
     [:div.panel-block [kontaktdaten-form]]
     [:div.panel-block
      (case @poststate
        :success [:div.notification.is-success (str "✔ " (snip/anfrage-uebermittelt loc))]
        [anfrage-button])]]))

(defn- hauptform []
  [:section.section
   [:h1.title.is-3.buchung_ueberschrift (snip/buchungsanfrage @u/locale)]
   [:div.container
    [wohnungswahl]
    [reisedaten]
    (when (and @anreise @abreise (not @daterror))
      [anfrage-panel])]])

;; --------------------------------------------------------- mount

(defn ^:dev/after-load start []
  (rdom/render [hauptform] (.getElementById js/document "mainframe")))

(defn loading-screen []
  [:div.loadscreen
   [:div.lds-dual-ring__msg (snip/lade-buchungsdaten @u/locale)]
   [:div.lds-dual-ring [:div]]])

(defn ^:export main [{:keys [locale]}]
  (u/set-locale! locale)
  (rdom/render [loading-screen] (.getElementById js/document "mainframe"))
  (ajx/fetch-data! preise wohnungen start))
