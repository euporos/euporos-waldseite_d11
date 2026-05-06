(ns seiten.components.navigation
  (:require [comp.localization :as loc]))

;; #################
;; #### Helpers ####
;; #################

(defn statify-navitem
  "vereinfacht Rendering"
  [req dynamic-menuitem]
  (let [{:keys [pathfn href name]} dynamic-menuitem]
    (-> dynamic-menuitem
        (assoc :href (cond
                       href   href
                       pathfn (pathfn req)
                       :else  "#warning-noref"))
        (assoc :name (cond
                       (map? name) (loc/by-locale (:locale req) name)
                       (fn? name)  (name (:locale req))
                       :else       (str name))))))

;; ##############
;; ### Navbar ###
;; ##############

(defn navbar-section
  "class is navbar-start or navbar-end"
  [req headitem items]
  [:div
   {:class (:class headitem)}
   items])

(defn navbar-item
  [req item]
  (let [item (statify-navitem req item)]
    [:li.navigation__item.menuitem.menuitem--lvl-0
     [:a.navbar-item
      {:href (:href item)}
      (:name item)]]))

(defn locale-span
  "erzeugt aus einer Sprachkollektion, z.b. Deutsch
  einen span mit der entsprechenden Sprachschaltfläche"
  [req item]
  (let [item (statify-navitem req item)]
    [:span.navigation__locale
     [:a
      {:href (:href item)}
      (:name item)]]))

(defn locale-choice
  "mapt über alle Sprachcollections und erzeugt für jede
  einen locale-Span. packt dann alle in ein Nav-item."
  [req headitem items]
  (into
   [:li.navigation__item.navigation__item--locale]
   items))

(defn navbar-dropdown
  ""
  [_req headitem items]
  [:div.navbar-item.has-dropdown.is-hoverable
   [:a.navbar-link
    {:href (:href headitem)}
    (:name headitem)]
   [:div.navbar-dropdown
    items]])

(defn navbar
  ""
  [_req headitem items]
  [:div.navigation
   [:input#navi-toggle.navigation__checkbox
    {:type "checkbox"}]
   [:label.navigation__button
    {:for "navi-toggle"}
    [:span.navigation__hamburger]]
   [:div.navigation__background " "]
   [:nav.navigation__nav
    [:div.navigation__list.navigation__list--lvl-0
     {:id (:menuid headitem)}
     items]]])

;; ##############
;; ### Footer ###
;; ##############

(def facebook-button-shariff
  (list

   [:div.shariff]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/shariff/1.26.2/shariff.min.js"}]))

(defn fb-button [url]
  [:div.solid-icon.social-media-icon
   [:a
    {:rel    "noopener noreferrer"
     :target "_blank"
     :href   (str "https://www.facebook.com/sharer/sharer.php?u="
                  url)}
    [:img {:src "/imgs/icons/facebook-black.png"}]]])

(defn footer-menuitem
  ""
  [req item]
  (let [item (statify-navitem req item)]
    [:a
     {:href (:href item)} (:name item)]))

(defn footer-menu
  ""
  [req headitem items]
  [:div.footer__menue.footer__menue--lvl-0
   items])

