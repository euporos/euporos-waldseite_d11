(ns preise.views
  "Views of the price admin (re-frame). Same table layout, CSS classes,
   labels and units as the legacy Reagent admin. Views only deref subs and
   dispatch events; every season is addressed by `:id`."
  (:require [preise.events :as ev]
            [preise.subs :as subs]
            [re-frame.core :as rf]))

(defn- input-value [e] (-> e .-target .-value))

;; ---------------------------------------------------------------------------
;; Allgemeine Daten

(defn einzelfeld [allgdaten [k label einheit]]
  ^{:key (str k)}
  [:td.einzelfeld {:align "center"}
   [:div.einzelfeld__label label]
   [:input.form-control.preisinput.einzelinput
    {:type          :number
     :data-test-id  (str "allg-" (name k))
     :value         (get allgdaten k)
     :on-change     #(rf/dispatch [::ev/set-allg k (input-value %)])}]
   (when einheit [:span.einheit einheit])])

(defn einzelfelder []
  (let [allgdaten @(rf/subscribe [::subs/allgdaten])]
    [:table.einzelfelder
     [:tbody.einzelfeldbody
      [:tr
       (doall
        (map (partial einzelfeld allgdaten)
             [[:aufschlag_zus_person "zus. Person" "€"]
              [:aufschlag_haustier "zus. Tier" "€"]
              [:reinigung-unter "Reinigung fällig unter:" "Tage"]
              [:gaestebeitrag "Gästebeitrag" "€"]
              [:energieaufschlag "Energie /Tag+Pers" "€"]]))]]]))

;; ---------------------------------------------------------------------------
;; Basisdaten

(defn basiszeile [basisdaten wohnungen [label k]]
  ^{:key (str label)}
  [:tr.zusatzzeile
   [:td.zusatzlabel label]
   (doall
    (for [[wid _] wohnungen]
      ^{:key wid}
      [:td.sonderfeld
       [:input.form-control
        {:type         :number
         :data-test-id (str "basis-" wid "-" (name k))
         :value        (get-in basisdaten [wid k])
         :on-change    #(rf/dispatch [::ev/set-basis wid k (input-value %)])}]]))])

(defn basisfelder []
  (let [basisdaten @(rf/subscribe [::subs/basisdaten])
        wohnungen  @(rf/subscribe [::subs/wohnungen])]
    [:table
     [:thead
      [:tr
       [:th.wohnungsname]
       (for [[wid w] wohnungen]
         ^{:key wid} [:th.wohnungsname (:name w)])]]
     [:tbody#wohnungsdaten-nosaison
      (doall
       (map (partial basiszeile basisdaten wohnungen)
            [["Maximalbelegung"          :maximalbelegung]
             ["Standardbelegung"         :standardbelegung]
             ["Grundpreis"               :grundpreis]
             ["Reinigung"                :reinigung]
             ["min. Aufenthalt (stand.)" :mindestaufenthalt_standard]]))]]))

;; ---------------------------------------------------------------------------
;; Saisonale Daten

(defn- durchschnittspreis-feld [wert einheit]
  [:div (str (.toFixed wert 0)) einheit])

(defn durchschnittspreis-row [wohnungen]
  (let [durchschnitt @(rf/subscribe [::subs/durchschnittspreise])]
    [:tr
     [:td]
     [:td.zusatzlabel "Durchschnittspreis" [:br] "(gewichtet)"]
     (doall
      (for [[wid _] wohnungen
            :let [{:keys [preis_tag preis_woche]} (get durchschnitt wid)]]
        ^{:key (str "durchschnitt-" wid)}
        [:td.dpreisfeld
         [durchschnittspreis-feld preis_tag " /Tag"]
         [durchschnittspreis-feld preis_woche " /W"]]))]))

(defn saison-fehler [id]
  (let [{:keys [dates-bad? overlaps?]} (get @(rf/subscribe [::subs/saison-fehler]) id)]
    (when (or dates-bad? overlaps?)
      [:div
       [:div.saison-errort.ttip "⚠"
        (when dates-bad? [:span.ttiptext "Beginn muss vor Ende liegen"])
        (when overlaps?  [:span.ttiptext "Saisonen überlappen"])]])))

(defn trash [id]
  (let [armed? (contains? @(rf/subscribe [::subs/trash-armed]) id)]
    (if armed?
      [:div.actionicon
       [:span {:data-test-id "saison-trash-cancel"
               :on-click #(rf/dispatch [::ev/arm-trash id false])} "X"]
       [:img {:src          "/imgs/preise/trash.png"
              :height       "30px"
              :data-test-id "saison-trash-confirm"
              :on-click     #(rf/dispatch [::ev/trash-saison id])}]]
      [:img.actionicon
       {:src          "/imgs/preise/trash.png"
        :height       "30px"
        :data-test-id "saison-trash"
        :on-click     #(rf/dispatch [::ev/arm-trash id true])}])))

(defn select [id]
  [:input.actionicon.actionicon--select
   {:type         "checkbox"
    :data-test-id "saison-select"
    :checked      (contains? @(rf/subscribe [::subs/selected]) id)
    :on-change    #(rf/dispatch [::ev/toggle-select id])}])

(defn datepicker [{:keys [id] :as saison} feld]
  [:input.form-control
   {:type         :date
    :placeholder  "yyyy-mm-dd"
    :data-test-id (str "saison-" (name feld))
    :value        (get saison feld)
    :on-change    #(rf/dispatch [::ev/set-saison-datum id feld (input-value %)])}])

(defn saisonfeld [id wid k einheit saison]
  ^{:key (str id "-" wid k)}
  [:div
   [:input.form-control.preisinput
    {:type         :number
     :data-test-id (str "saison-" (name k))
     :value        (get-in saison [:felder wid k])
     :on-change    #(rf/dispatch [::ev/set-saison-feld id wid k (input-value %)])}]
   [:span.einheit einheit]])

(defn season-row [wohnungen saison]
  (let [id (:id saison)]
    [:tr.saisonzeile
     [:td.iconzelle
      [trash id]
      [:br]
      [select id]
      [saison-fehler id]]
     [:td.datumszelle
      [:div.datepickers
       [datepicker saison :beginn]
       [datepicker saison :ende]]]
     (doall
      (for [[wid _] wohnungen]
        ^{:key wid}
        [:td.preisdoppel
         (saisonfeld id wid :preis_tag "€/Tag" saison)
         (saisonfeld id wid :preis_woche "€/W" saison)
         (saisonfeld id wid :mindestaufenthalt "Tage" saison)]))
     [:td]]))

(defn datum-fehler []
  (when-let [{:keys [error]} @(rf/subscribe [::subs/datum-fehler])]
    (when (= error :beginn-nicht-vor-ende)
      [:div.notification.is-danger {:data-test-id "datum-fehler"}
       "Beginn muss vor Ende liegen!"])))

(defn saisonale-daten []
  (let [wohnungen @(rf/subscribe [::subs/wohnungen])
        saisonen  @(rf/subscribe [::subs/sortierte-saisonen])]
    [:div
     [datum-fehler]
     [:table
      [:thead
       [:tr
        [:th] [:th]
        (for [[wid w] wohnungen]
          ^{:key wid} [:th.wohnungsname.wohnungsname--sticky (:name w)])]]
      [:tbody#durchschnittspreise
       [durchschnittspreis-row wohnungen]]
      [:tbody#saisondaten
       (doall
        (for [s saisonen]
          ^{:key (str "saison-" (:id s))}
          [season-row wohnungen s]))
       [:tr
        [:td
         [:div.addsaison
          {:data-test-id "saison-add"
           :on-click     #(rf/dispatch [::ev/add-saison])}
          "+"]]]]]]))

;; ---------------------------------------------------------------------------
;; Special actions

(defn special-actions-table []
  [:table
   [:tbody
    [:tr
     [:td.special-function
      [:button.btn.btn-primary
       {:data-test-id "saison-clone"
        :on-click #(rf/dispatch [::ev/clone-selected-next-year])}
       "für nächstes Jahr klonen"]]
     [:td.special-function
      [:button.btn.btn-primary
       {:data-test-id "saison-unselect"
        :on-click #(rf/dispatch [::ev/unselect-all])}
       "Auswahl aufheben"]]
     [:td.special-function.special-function--danger
      [:button.btn.btn-danger
       {:data-test-id "saison-delete-selected"
        :on-click #(rf/dispatch [::ev/delete-selected])}
       "Löschen"]]]]])

;; ---------------------------------------------------------------------------
;; Speichern

(def speicher-texte
  {:ungespeichert     ["Ungespeichert" "speicherbericht--ungespeichert"]
   :erfolgreich       ["Erfolgreich gespeichert" "speicherbericht--erfolgreich"]
   :falsches-passwort ["Nicht eingeloggt — bitte zuerst in Directus anmelden."
                       "speicherbericht--fehlgeschlagen"]
   :fehlgeschlagen    ["Speicherung fehlgeschlagen" "speicherbericht--fehlgeschlagen"]
   :req-failed        ["Speicherung fehlgeschlagen (Request failed)"
                       "speicherbericht--fehlgeschlagen"]})

(defn speicherbericht []
  (let [state @(rf/subscribe [::subs/save-state])
        [nachricht class] (get speicher-texte state)]
    [:div.centerinline
     [:button.btn.btn-primary
      {:type "button" :data-test-id "preise-save"
       :on-click #(rf/dispatch [::ev/save])}
      "Daten speichern"]
     [:br] [:br]
     (if (= state :ongoing)
       [:div.speichervorgang [:div.lds-dual-ring [:div]]]
       [:div {:class (str "speicherbericht " class)} nachricht])]))

;; ---------------------------------------------------------------------------
;; Root

(def loading-screen
  [:div.loadscreen
   [:div.lds-dual-ring__msg "Lade Preisdaten"]
   [:div.lds-dual-ring [:div]]])

(defn alles []
  [:div.panel-group.panelgruppe
   [:div.panel.panel-success
    [:div.panel-heading "Allgemeine Daten"]
    [einzelfelder]]
   [:div.panel.panel-success
    [:div.panel-heading "Basisdaten"]
    [basisfelder]]
   [:div.panel.panel-success
    [:div.panel-heading "Saisonale Daten"]
    [saisonale-daten]]
   [:div.panel.panel-success
    [:div.panel-heading "Aktionen für ausgewählte Saisonen"]
    [special-actions-table]]
   [:br]
   [speicherbericht]])

(defn root []
  (case @(rf/subscribe [::subs/status])
    :ready         [alles]
    :load-failed   [:div.notification.is-danger {:data-test-id "load-failed"}
                    "Preisdaten konnten nicht geladen werden. "
                    [:button.btn.btn-primary
                     {:data-test-id "load-retry"
                      :on-click #(rf/dispatch [::ev/load])}
                     "Erneut versuchen"]]
    :not-logged-in [:div.notification.is-danger {:data-test-id "not-logged-in"}
                    "Nicht eingeloggt — bitte zuerst in Directus anmelden."]
    loading-screen))
