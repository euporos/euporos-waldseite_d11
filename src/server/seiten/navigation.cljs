(ns seiten.navigation
  (:require
   [comp.localization :as loc]
   [psite-menu.core :as pmenu]
   [psite-utils.core :as h]
   [seiten.components.navigation :as cnav]
   [serving.routing :as rt]))

;; ###################
;; #### Renderer #####
;; ###################
;; psite-menu only ships compose-menus; the recursive walker that emits
;; hiccup lives here. Each menupart is either a map (leaf) or a vector
;; whose first element is the headitem and the rest are children.

(defn insertwarn [_req insertpoint]
  (js/console.warn "! Insertpoint" (str (:id insertpoint)) "ist ungefüllt"))

(defn render-item [req item & args]
  (if (:renderfn item)
    (apply (:renderfn item) req item args)
    (js/console.warn "! No :renderfn for menuitem" (pr-str item))))

(defn make-menu [req menupart]
  (if (vector? menupart)
    (render-item req (first menupart)
                 (map (partial make-menu req) (rest menupart)))
    (render-item req menupart)))

;; ###################
;; #### Basemenus ####
;; ###################

(def basemenus
  {:main [{:type :headitem :renderfn cnav/navbar :menuid "navbarBasicExample"}

          [{:type :headitem :renderfn cnav/navbar-section :class "navbar-start"}

           {:type     :menuitem :id :home
            :renderfn cnav/navbar-item-icon
            :pathfn   (fn [req] (rt/path-fixed :home req))
            :icon     "/imgs/zwei_daecher.png"
            :name     {:de "Startseite" :en "Home"}}

           {:type     :insertpoint :id :haeuser
            :renderfn insertwarn
            :defaults {}}

           {:type     :menuitem :id :buchung
            :renderfn cnav/navbar-item
            :pathfn   (fn [req] (rt/path-fixed :buchung req))
            :name     {:de "Buchung" :en "Booking"}}

           {:type     :menuitem :id :aktuelles
            :renderfn cnav/navbar-item
            :pathfn   (fn [req] (rt/path-fixed :aktuelles req))
            :name     {:de "Aktuelles" :en "News"}}

           {:type     :menuitem :id :galerie
            :renderfn cnav/navbar-item
            :pathfn   (fn [req] (rt/path-fixed :galerie req))
            :name     {:de "Galerie" :en "Gallery"}}

           {:type     :menuitem :id :ausfluege
            :renderfn cnav/navbar-item
            :pathfn   (fn [req] (rt/path-ausfluege req "alle"))
            :name     {:de "Ausflugtips" :en "Hiking"}}

           {:type     :insertpoint :id :oben
            :defaults {:renderfn cnav/navbar-item}}]]

   :footer [{:type :headitem :renderfn cnav/footer-menu}

            {:type     :insertpoint :id :footer
             :defaults {:renderfn cnav/footer-menuitem}}

            {:type     :menuitem :id :kontakt
             :renderfn cnav/footer-menuitem
             :pathfn   (fn [req] (rt/path-fixed :kontakt req))
             :name     {:de "Kontakt" :en "Contact us"}}]})

;; ###########################
;; ###### Data to Menus ######
;; ###########################

(defn einzelseite-to-menuitem
  [req {:keys [id titel menue]}]
  {:type        :menuitem
   :insertpoint (keyword menue)
   :name        titel
   :href        (rt/path-einzelseite req id titel)})

(def haeuser-anchors
  [["wohnungen" {:de "Wohnungen"}]
   ["anreise"   {:de "Anreise"}]
   ["buchung"   {:de "Buchung"}]])

(defn anchors-to-items [req baselink [anchor-id title]]
  {:type     :menuitem
   :renderfn cnav/navbar-item
   :href     (str baselink "#" anchor-id)
   :name     (loc/by-locale (:locale req) title)})

(defn haus-to-menuitem
  [req {:keys [id name]}]
  (let [href (rt/path-haus req id name)]
    (into [{:type        :headitem
            :insertpoint :haeuser
            :name        name
            :renderfn    cnav/navbar-dropdown
            :href        href}]
          (map (partial anchors-to-items req href) haeuser-anchors))))

;; ###########################
;; ##### Sondermenu glue #####
;; ###########################

(defn make-sondermenus
  "Take a vector of {:adaptfn :collection} and return a map keyed by
  each item's :insertpoint. For nested vectors, the headitem's
  :insertpoint is what counts."
  [req menudata]
  (group-by
   #(if (and (vector? %) (seq %))
      (:insertpoint (first %))
      (:insertpoint %))
   (reduce
    (fn [sofar {:keys [adaptfn collection]}]
      (into sofar (map (partial adaptfn req) collection)))
    []
    menudata)))

(defn compose-menus
  "Build the per-request menu tree by merging dynamic menu inputs into basemenus.
  `menu-inputs` is a map with :haeuser and :einzelseiten-for-menus collections."
  [req menu-inputs]
  (let [sondermenus (make-sondermenus
                     req
                     [{:adaptfn    einzelseite-to-menuitem
                       :collection (:einzelseiten-for-menus menu-inputs)}
                      {:adaptfn    haus-to-menuitem
                       :collection (:haeuser menu-inputs)}])]
    (h/map-vals
     #(pmenu/compose-menus % sondermenus)
     basemenus)))
