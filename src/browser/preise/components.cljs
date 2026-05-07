(ns preise.components
  (:require
   [cljs-time.coerce :as tm.coerce]
   [cljs-time.core :as tm]
   [preise.calculations :as calc]
   [preise.util :as u]
   [reagent.core :as r]))

;; ########################
;; ### Allgemeine Daten ###
;; ########################

(defn einzelfeld
  [allgdaten datenkey label einheit]
  (let [cursor (r/cursor allgdaten [datenkey])]
    [:td.einzelfeld
     {:key (str datenkey) :align "center"}
     [:div.einzelfeld__label label]
     [:input.form-control.preisinput.einzelinput
      {:type      :number
       :key       (str "einzelfeld-" (str datenkey))
       :value     @cursor
       :on-change (fn [e] (reset! cursor (-> e .-target .-value)))}]
     (when einheit
       [:span.einheit {:key (str "einheit-" datenkey)} einheit])]))

(defn einzelfelder
  [allgdaten]
  [:table.einzelfelder
   [:tbody.einzelfeldbody
    [:tr
     (doall
      (u/mapply (partial einzelfeld allgdaten)
                [[:aufschlag_zus_person "zus. Person" "€"]
                 [:aufschlag_haustier "zus. Tier" "€"]
                 [:reinigung-unter "Reinigung fällig unter:" "Tage"]
                 [:gaestebeitrag "Gästebeitrag" "€"]
                 [:energieaufschlag "Energie /Tag+Pers" "€"]]))]]])

;; ######################
;; ##### Basisdaten #####
;; ######################

(defn basisfeld
  [basisdaten path]
  (let [cursor (r/cursor basisdaten path)]
    [:td.sonderfeld
     {:key (str path)}
     [:input.form-control
      {:type      :number
       :value     @cursor
       :on-change (fn [e] (reset! cursor (-> e .-target .-value)))}]]))

(defn basiszeile
  [basisdaten wohnungen label path]
  [:tr.zusatzzeile
   {:key (str label "-" path)}
   [:td.zusatzlabel label]
   (doall
    (map #(basisfeld basisdaten (into [(first %)] path))
         @wohnungen))])

(defn basisfelder
  [basisdaten-a wohnungen-a]
  [:table
   [:thead
    [:tr
     [:th.wohnungsname]
     (for [wohnung @wohnungen-a]
       [:th.wohnungsname
        {:key (:name (second wohnung))}
        (:name (second wohnung))])]]
   [:tbody#wohnungsdaten-nosaison
    (doall
     (map
      #(basiszeile basisdaten-a wohnungen-a (first %) (second %))
      [["Maximalbelegung"            [:maximalbelegung]]
       ["Standardbelegung"           [:standardbelegung]]
       ["Grundpreis"                 [:grundpreis]]
       ["Reinigung"                  [:reinigung]]
       ["min. Aufenthalt (stand.)"   [:mindestaufenthalt_standard]]]))]])

;; #############################
;; ###### Saisonale Daten ######
;; #############################

(defn durchschnittspreis-einzelfeld
  [saisonen wohnungsid tag-or-woche]
  [:div
   (str (.toFixed (calc/gewichte-preise saisonen wohnungsid tag-or-woche) 0))
   (case tag-or-woche
     :preis_tag   " /Tag"
     :preis_woche " /W")])

(defn durchschnittspreis-doppelfeld
  [saisonen wohnungsid]
  [:td.dpreisfeld
   {:key (str "durchschnitt-" wohnungsid)}
   (durchschnittspreis-einzelfeld saisonen wohnungsid :preis_tag)
   (durchschnittspreis-einzelfeld saisonen wohnungsid :preis_woche)])

(defn durchschnittspreis-row
  [saisonen wohnungen]
  [:tr
   [:td]
   [:td.zusatzlabel "Durchschnittspreis" [:br] "(gewichtet)"]
   (doall
    (map #(durchschnittspreis-doppelfeld saisonen (first %)) wohnungen))])

(defn interval-sn
  [saison]
  (if (and (tm/date? (:beginn saison))
           (tm/date? (:ende saison))
           (tm/before? (:beginn saison) (:ende saison)))
    (tm/interval (:beginn saison) (:ende saison))
    (do (js/console.warn (str "Cannot compute interval from dates: " saison))
        (tm/interval (tm/now) (tm/now)))))

(defn seasons-overlap?
  [s1 s2]
  (or (tm/= (:beginn s1) (:ende s2))
      (tm/= (:beginn s2) (:ende s1))
      (tm/overlaps? (interval-sn s1) (interval-sn s2))))

(defn saison-overlaps?
  [checksaison saisonen]
  (reduce (fn [sofar saison] (or sofar (seasons-overlap? checksaison saison)))
          false
          (filter #(not= % checksaison) saisonen)))

(defn trash-seasons!
  [saisonen-a & saisonen]
  (swap! saisonen-a #(remove (fn [v] (some #{v} saisonen)) %)))

(defn next-saison
  [saisonen]
  (let [beginn (if (seq saisonen)
                 (-> (sort tm/before? (map :ende saisonen))
                     (last)
                     (tm/plus- (tm/days 1)))
                 (tm/today))]
    {:beginn beginn :ende (tm/plus- beginn (tm/weeks 2))}))

(defn add-saison!
  [saisonen-a saison]
  (let [sids  (map :id @saisonen-a)
        newid (if (seq sids) (inc (apply max sids)) 1)]
    (swap! saisonen-a #(conj % (assoc saison :id newid)))))

(defn check-saison
  [checksaison saisonen]
  (let [dates-bad? (not (tm/before? (:beginn checksaison) (:ende checksaison)))
        overlaps?  (saison-overlaps? checksaison saisonen)]
    (when (or dates-bad? overlaps?)
      [:div
       [:div.saison-errort.ttip "⚠"
        (when dates-bad? [:span.ttiptext "Beginn muss vor Ende liegen"])
        (when overlaps?  [:span.ttiptext "Saisonen überlappen"])]])))

(defn trash
  [_saisonen-a _saison]
  (let [hot? (r/atom false)]
    (fn [saisonen-a saison]
      (if @hot?
        [:div.actionicon
         [:span {:on-click #(reset! hot? false)} "X"]
         [:img {:src     "/imgs/preise/trash.png"
                :height  "30px"
                :onClick (fn [] (trash-seasons! saisonen-a saison))}]]
        [:img.actionicon
         {:src      "/imgs/preise/trash.png"
          :height   "30px"
          :on-click #(swap! hot? not)}]))))

(defn select
  [saisonen-a saison]
  [:input.actionicon.actionicon--select
   {:type    "checkbox"
    :checked (boolean (:selected saison))
    :on-change
    (fn [_e]
      (u/swap-in-seq! saisonen-a saison #(update % :selected not)))}])

(defn datepicker
  [saisonen-a saison field]
  [:input.form-control
   {:type        :date
    :placeholder "yyyy-mm-dd"
    :value       (u/to-iso-day (field saison))
    :on-change
    (fn [e]
      (let [newseason (assoc saison field
                             (tm.coerce/from-string (-> e .-target .-value)))]
        (if (tm/before? (:beginn newseason) (:ende newseason))
          (u/swap-in-seq!
           saisonen-a saison
           #(assoc % field (tm.coerce/from-string (-> e .-target .-value))))
          (js/alert "Beginn muss vor Ende liegen!"))))}])

(defn datepickers
  [saisonen-a saison]
  [:div.datepickers
   (datepicker saisonen-a saison :beginn)
   (datepicker saisonen-a saison :ende)])

(defn saisonfeld
  [saisonen-a saison wohnungsid datenkey einheit]
  (let [path [:felder wohnungsid datenkey]]
    [:div
     {:key (str "feld-" (:id saison) "-" wohnungsid (str datenkey))}
     [:input.form-control.preisinput
      {:type      :number
       :key       (str (:id saison) "-" wohnungsid (str datenkey))
       :value     (get-in saison path)
       :on-change (fn [e]
                    (u/swap-in-seq!
                     saisonen-a saison
                     #(assoc-in % path (-> e .-target .-value))))}]
     [:span.einheit
      {:key (str "einheit-" (:id saison) "-" wohnungsid (str datenkey))}
      einheit]]))

(defn saisonfelder
  [saisonen-a saison wohnungsid]
  [:td.preisdoppel
   {:key (str (:id saison) "-" wohnungsid)}
   (u/mapply (partial saisonfeld saisonen-a saison wohnungsid)
             [[:preis_tag "€/Tag"]
              [:preis_woche "€/W"]
              [:mindestaufenthalt "Tage"]])])

(defn make-season-row
  [saisonen-a wohnungen saison]
  (let [saisonid (:id saison)]
    [:tr.saisonzeile
     {:key (str "saison-" saisonid)}
     [:td.iconzelle
      [trash saisonen-a saison]
      [:br]
      [select saisonen-a saison]
      (check-saison saison @saisonen-a)]
     [:td.datumszelle (datepickers saisonen-a saison)]
     (doall
      (for [wohnung wohnungen]
        ^{:key wohnung}
        (saisonfelder saisonen-a saison (first wohnung))))
     [:td]]))

(defn saisonale-daten
  [saisonen-a wohnungen]
  [:table
   [:thead
    [:tr
     [:th] [:th]
     (for [wohnung wohnungen]
       [:th.wohnungsname.wohnungsname--sticky
        {:key (:name (second wohnung))}
        (:name (second wohnung))])]]
   [:tbody#durchschnittspreise
    (durchschnittspreis-row @saisonen-a wohnungen)]
   [:tbody#saisondaten
    (doall
     (for [saison (sort-by :beginn tm/before? @saisonen-a)]
       (make-season-row saisonen-a wohnungen saison)))
    [:tr
     [:td
      [:div.addsaison
       {:on-click #(add-saison! saisonen-a (next-saison @saisonen-a))}
       "+"]]]]])

;; #############################
;; ###### Special Actions ######
;; #############################

(defn shift-saison-by-years
  [years saison]
  (u/update-multiple saison [:beginn :ende] #(tm/plus- % (tm/years years))))

(defn shift-saisonen-by-one-year
  [saisonen]
  (map (partial shift-saison-by-years 1) (filter :selected saisonen)))

(defn unselect-all-saisonen
  [saisonen]
  (map #(assoc % :selected false) saisonen))

(defn !copyshift-selected
  [saisonen-a]
  (doseq [shifted (shift-saisonen-by-one-year @saisonen-a)]
    (add-saison! saisonen-a shifted)))

(def special-actions
  [["für nächstes Jahr klonen"
    (fn [saisonen-a]
      (!copyshift-selected saisonen-a)
      (swap! saisonen-a unselect-all-saisonen))]
   ["Auswahl aufheben"
    (fn [saisonen-a] (swap! saisonen-a unselect-all-saisonen))]])

(defn special-action-cell
  [label func]
  [:td.special-function
   {:key (str "Sfun-" label)}
   [:button.btn.btn-primary {:on-click func} label]])

(defn special-actions-table
  [saisonen-a special-actions]
  [:table
   [:tbody
    [:tr
     (doall
      (map (fn [[label func]]
             (special-action-cell label (partial func saisonen-a)))
           special-actions))
     [:td.special-function.special-function--danger
      {:key "ausgewaehlte-löschen"}
      [:button.btn.btn-danger
       {:on-click (fn [] (swap! saisonen-a #(remove :selected %)))}
       "Löschen"]]]]])

;; ###################
;; #### Speichern ####
;; ###################

(defn post-button
  [func]
  [:button.btn.btn-primary
   {:type "button" :on-click func}
   "Daten speichern"])

(def speichervorgang
  [:div.speichervorgang
   [:div.lds-dual-ring [:div]]])

(defn speicherbericht
  "Auth no longer uses a password — the legacy :falsches-passwort label is
   kept for UI continuity but now means 'not logged into Directus'."
  [speicherzustand speicherfunc]
  (let [[nachricht class]
        (get
         {:ungespeichert     ["Ungespeichert"
                              "speicherbericht--ungespeichert"]
          :erfolgreich       ["Erfolgreich gespeichert"
                              "speicherbericht--erfolgreich"]
          :falsches-passwort ["Nicht eingeloggt — bitte zuerst in Directus anmelden."
                              "speicherbericht--fehlgeschlagen"]
          :fehlgeschlagen    ["Speicherung fehlgeschlagen"
                              "speicherbericht--fehlgeschlagen"]
          :req-failed        ["Speicherung fehlgeschlagen (Request failed)"
                              "speicherbericht--fehlgeschlagen"]}
         speicherzustand)]
    [:div.centerinline
     (post-button speicherfunc)
     [:br] [:br]
     (if (= speicherzustand :ongoing)
       speichervorgang
       [:div {:class (str "speicherbericht " class)} nachricht])]))
