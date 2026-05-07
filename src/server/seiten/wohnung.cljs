(ns seiten.wohnung
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [clojure.string :as str]
            [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [psite-hiccup.core :as ph]
            [db.setup :as db]
            [db.queries :as q]
            [seiten.components.gallery :as gallery]
            [seiten.templates :as templates]
            [serving.routing :as rt]))

(defn- ausstattung-table [tabelle-string dtvsterne]
  [:div.floating-img.floating-img--right
   [:div.card
    [:table.table
     [:tbody
      (when (and dtvsterne (pos? dtvsterne))
        [:tr
         [:td [:a {:target "_blank" :rel "noopener"
                   :href "https://www.deutschertourismusverband.de/qualitaet/sterneunterkuenfte.html"}
               "DTV-Sterne"]]
         [:td (repeat dtvsterne [:i.dtvstern])]])
      (for [line (when tabelle-string (str/split-lines tabelle-string))
            :let [cells (str/split line #"::")]]
        [:tr
         (if (= 1 (count cells))
           [:td.has-text-centered {:colspan 2} (first cells)]
           (for [c cells] [:td c]))])]]]])

(defn- page-body [req wohnung bilder]
  (let [{:keys [id name beschreibung hauptbild
                ausstattung_tabelle dtvsterne]} wohnung]
    [:section
     [:div.panel.mainpanel
      [:div.block (gallery/bilder-gallery
                   "gallery-wohnung"
                   (cons hauptbild
                         (remove #{hauptbild} (map :directus_files_id bilder))))]

      [:div.block.textabschnitt.py-4.px-4
       [:h1.title.is-2 "Wohnung " name]
       [:div.content
        [:div.wohnungbeschreibung
         [:div.is-hidden-mobile
          (ausstattung-table ausstattung_tabelle dtvsterne)]
         (ph/dangerous-html (or beschreibung ""))
         [:div.is-hidden-tablet
          (ausstattung-table ausstattung_tabelle dtvsterne)]]]]

      [:div.mb-4.has-text-centered.pb-4
       [:a {:href (str (rt/path-fixed :buchung req)
                       "?default=" id)}
        [:button.button.is-link
         {:type "submit" :value "Submit"}
         "Jetzt Anfragen"]]]]]))

(defhandler handler [req]
  (p/let [locale     (:locale req)
          wohnung-id (-> req :path-params :wohnungsid)
          wohnung-id (if (string? wohnung-id) (js/parseInt wohnung-id 10) wohnung-id)
          wohnung    (-> (db/query (q/wohnung-detail locale wohnung-id)) (.then first))
          bilder     (db/query (q/wohnung-bilder wohnung-id))]
    (templates/render-page
     req
     {:titel        (str "Wohnung " (:name wohnung))
      :beschreibung ""}
     (page-body req wohnung bilder))))
