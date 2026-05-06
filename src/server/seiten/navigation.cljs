(ns seiten.navigation
  (:require
   [comp.snippets :as snip]
   [psite-routing.core :as routing]
   [comp.localization :as loc]
   [psite-menu.core :as pmenu]
   [psite-utils.core :as h]
   [seiten.components.navigation :as cnav]
   [serving.routing :as rt]))

;; ###################
;; #### Basemenus ####
;; ###################

(def language-items
  (map
   (fn [locale]
     {:type     :menuitem
      :renderfn cnav/locale-span
      :pathfn   (partial rt/switch-locale locale)
      :name     (name locale)})
   [:de :en :uk :it]))

(def language-menu
  (into [{:type     :headitem
          :renderfn cnav/locale-choice}]
        language-items))

(def basemenus
  "Anforderungen an Renderfunktionen:
  1. Erste Elemente eines Vekors: drei Stellen
  _Headitem_ _Daten_ _Unterelemente_
  2. Einzelelemente: Drei Stellen
  _item_ Daten_

  "
  {:main [{:type     :headitem
           :renderfn cnav/navbar
           :menuid   "navbarBasicExample"}

          {:type     :menuitem
           :id       :home
           :renderfn cnav/navbar-item
           :pathfn   (partial rt/path-fixed :home)
           :name     snip/home}

          {:type     :menuitem
           :id       :termine
           :renderfn cnav/navbar-item
           :pathfn   (partial rt/path-fixed :termine)
           :name     snip/termine}

          {:type     :menuitem
           :id       :musicians
           :renderfn cnav/navbar-item
           :pathfn   (partial rt/path-fixed :kuenstler)
           :name     snip/musicians}

          {:type     :menuitem
           :id       :programme
           :renderfn cnav/navbar-item
           :pathfn   (partial rt/path-fixed :programme)
           :name     snip/programme}

          {:type     :menuitem
           :id       :cds
           :renderfn cnav/navbar-item
           :pathfn   (partial rt/path-fixed :cds)
           :name     snip/cds}

          {:type     :menuitem
           :id       :galerie
           :renderfn cnav/navbar-item
           :pathfn   (partial rt/path-fixed :galerie)
           :name     snip/galerie}

          {:type     :menuitem
           :id       :galerie
           :renderfn cnav/navbar-item
           :pathfn   (partial rt/path-fixed :presse)
           :name     snip/presse}

          {:type     :insertpoint
           :id       :main
           ;; :renderfn pnav/insertwarn
           :defaults {:renderfn cnav/navbar-item}}

          language-menu]

   :footer [{:type     :headitem
             :renderfn cnav/footer-menu}

            {:type     :insertpoint
             :id       :footer
             :defaults {:renderfn cnav/footer-menuitem}}]})

;; ###########################
;; ###### Data to Menus ######
;; ###########################

;; Einzelseiten

(defn einzelseite-to-menuitem
  ""
  [req einzelseite]
  (let [{:keys [id titel menue slug]}
        einzelseite]

    {:type        :menuitem
     :insertpoint (keyword menue)
     :name        titel
     :href        (routing/reverse-match req :einzelseite
                                         {:einzelseitid   id
                                          :einzelseitslug (h/slugify slug)})}))

;; Language

(defn language-to-menuitem
  [req language]
  (let []
    {:type     :menuitem
     :renderfn cnav/locale-span
     :pathfn   (partial rt/path-fixed :cds)
     :name     "de"}))

;; Häuser

(def haeuser-anchors
  [["wohnungen" {:de "Wohnungen"}]
   ;; ["ausstattung" {:de "Ausstattung"}]
   ;; ["aktuelles" {:de "Aktuelles"}]
   ;; ["ausfluege" {:de "Ausflüge"}]
   ["anreise" {:de "Anreise"}]
   ["buchung" {:de "Buchung"}]])

(defn anchors-to-items
  ""
  [data baselink anchor]
  (let [[id title] anchor]
    {:type     :menuitem
     :renderfn cnav/navbar-item
     :href     (str baselink "#" id)
     :name     (loc/by-locale (:locale data) title)}))

(defn haus-to-menuitem
  ""
  [data haus]
  (let [{:keys [id name]} haus
        href              nil ;;              (rt/path-haus (:locale data) id name) ;TODO:
        ]
    (into
     [{:type        :headitem
       :insertpoint :haeuser
       :name        name
       :renderfn    cnav/navbar-dropdown
       :href        href}]
     (map (partial anchors-to-items data href) haeuser-anchors))))

;; Alle

(defn make-sondermenus
  "takes a vector of maps
  containing a :adaptfn function
  and a :collection to map over"
  [data menudata]
  (group-by
   #(if (and (vector? %) (seq %))
      (:insertpoint (first %)) ; wenn vector mit subitems
      (:insertpoint %))  ; wenn einfaches item
   (reduce
    (fn [sofar {:keys [adaptfn collection]}]
      (into sofar
            (map (partial adaptfn data) collection)))
    []                                  ;sofar
    menudata)))

;; ###########################
;; ###### Compose Menus ######
;; ###########################

(defn compose-menus
  "integriere dynamische Menüs in das
  "
  [data]
  (let [sondermenus (make-sondermenus data ; build the menu dynamically
                                      [{:adaptfn    einzelseite-to-menuitem
                                        :collection (:einzelseiten-for-menus (:dresponses data))}
                                       {:adaptfn    haus-to-menuitem
                                        :collection (:haeuser (:dresponses data))}])]

    (h/map-vals ; now stick them into the static menu
     #(pmenu/compose-menus % sondermenus)
     basemenus)))
