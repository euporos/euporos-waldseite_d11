(ns seiten.components.navigation
  (:require [comp.localization :as loc]))

;; #################
;; #### Helpers ####
;; #################

(defn statify-navitem
  "Resolve :pathfn -> :href and i18n :name -> string for the current request."
  [req item]
  (let [{:keys [pathfn href name]} item]
    (-> item
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
  "Container for a row of items inside the navbar (navbar-start / navbar-end)."
  [_req headitem items]
  [:div {:class (:class headitem)} items])

(defn navbar-item
  [req item]
  (let [item (statify-navitem req item)]
    [:a.navbar-item {:href (:href item)} (:name item)]))

(defn navbar-dropdown
  [_req headitem items]
  [:div.navbar-item.has-dropdown.is-hoverable
   [:a.navbar-link {:href (:href headitem)} (:name headitem)]
   [:div.navbar-dropdown items]])

(defn navbar
  [_req headitem items]
  [:nav.navbar
   {:role       "navigation"
    :aria-label "main navigation"}
   [:div.navbar-brand
    [:a.navbar-item.nationalpark-partner-top
     {:rel    "noopener noreferrer"
      :target "_blank"
      :href   "https://www.nationalpark-partner.com/"}
     [:img {:src "/imgs/nationalpark.png"}]]
    [:a.navbar-burger.burger
     {:role          "button"
      :aria-label    "menu"
      :aria-expanded "false"
      :data-target   (:menuid headitem)}
     [:span {:aria-hidden "true"}]
     [:span {:aria-hidden "true"}]
     [:span {:aria-hidden "true"}]]]
   [:div.navbar-menu {:id (:menuid headitem)} items]])

;; ##############
;; ### Footer ###
;; ##############

(defn fb-button [url]
  [:div.solid-icon.social-media-icon
   [:a {:rel    "noopener noreferrer"
        :target "_blank"
        :href   (str "https://www.facebook.com/sharer/sharer.php?u=" url)}
    [:img {:src "/imgs/icons/facebook-black.png"}]]])

(defn footer-menuitem
  [req item]
  (let [item (statify-navitem req item)]
    [:div.column.is-narrow
     [:a.discretelink {:href (:href item)} (:name item)]]))

(defn footer-menu
  [_req _headitem items]
  [:div.columns.is-mobile.is-vcentered
   [:div.column.is-one-quarter.has-text-centered
    [:div.nationalpark-partner
     [:a {:rel    "noopener noreferrer"
          :target "_blank"
          :href   "https://www.nationalpark-partner.com/"}
      [:img {:src "/imgs/nationalpark.png"}]]]]
   [:div.column.is-half
    [:div.columns.is-centered.is-multiline
     (interpose [:div.is-divider-vertical {:data-content "OR"}]
                items)]]
   [:div.column.is-one-quarter.has-text-centered
    (fb-button "https://bickels-ferienwohnungen.de")]])
