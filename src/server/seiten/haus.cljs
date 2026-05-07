(ns seiten.haus
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [clojure.string :as str]
            [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [psite-hiccup.core :as ph]
            [db.setup :as db]
            [db.queries :as q]
            [seiten.templates :as templates]
            [serving.routing :as rt]
            [directus.core :as d]))

(defn- bilderkarussell [hauptbild bilder]
  [:div.bildheader
   [:div.columns.is-multiline.is-centered
    (for [bild (cons hauptbild
                     (remove #{hauptbild} (map :directus_files_id bilder)))]
      [:div.column.is-half-tablet.is-one-third-desktop
       [:figure.image
        [:img {:src (d/image-by-preset "1200" bild)}]]])]])

(defn- ausstattung-row [cells]
  [:tr
   (if (= 1 (count cells))
     [:td.has-text-centered {:colspan 2} (first cells)]
     (for [c cells] [:td c]))])

(defn- ausstattung-table [tabelle-string heading]
  [:div.floating-img.floating-img--right
   [:div.card
    [:table.table
     [:tbody
      (when heading [:tr [:td {:colspan 2} [:strong heading]]])
      (for [line (when tabelle-string (str/split-lines tabelle-string))
            :let [cells (str/split line #"::")]]
        (ausstattung-row cells))]]]])

(defn- wohnung-card [req {:keys [id name hauptbild]}]
  [:div.column.is-one-third-desktop.is-half-tablet
   [:a {:href (rt/path-wohnung req id name)}
    [:div.card.carousel-card
     [:div.card-image.card-image--hoverable
      [:figure.image.is-4by3
       [:img {:src (d/image-by-preset "600" hauptbild)}]]]
     [:div.content.base-background.has-text-centered
      [:strong name]]]]])

(defn- ausflugsvorschau [req haus-id {:keys [id bild titel]}]
  [:div.column.is-one-third-desktop.is-half-tablet
   [:a {:href (str (rt/path-ausfluege req haus-id) "#ausflug-" id)}
    [:div.card.carousel-card.base-background
     {:style "height: 100%;"}
     [:div.card-image.card-image--hoverable
      [:figure.image.is-4by3
       (when bild [:img {:src (d/image-by-preset "600" bild)}])]]
     [:div.content.has-text-centered
      [:strong titel]]]]])

(defn- maplink [href]
  [:a {:href href :target "_blank" :rel "noreferrer noopener"}
   [:i.fa.material-icons.is-size-1 "location_on"]])

(defn- page-body [req haus bilder wohnungen ausfluege]
  (let [{:keys [id name beschreibung ausstattung anreisetext buchungstext
                google_maplink adresse hauptbild ausstattung_tabelle]} haus
        adresszeilen (when adresse (str/split adresse #"\n"))]
    [:section
     [:div.panel.is-primary.mainpanel
      [:div.block (bilderkarussell hauptbild bilder)]

      [:div.block.textabschnitt.py-4.px-4
       [:h2.title.is-2.has-text-centered name]
       [:div.content (ph/dangerous-html (or beschreibung ""))]]

      [:div.block.textabschnitt.px-4
       [:h2.title.is-3.has-text-centered {:id "ausstattung"} "Ausstattung"]
       [:div.content
        (ausstattung-table
         ausstattung_tabelle
         (when (and name (seq name))
           (str "Das bietet "
                (str/lower-case (subs name 0 1))
                (subs name 1) ":")))
        (ph/dangerous-html (or ausstattung ""))]]

      [:div.block.px-4
       {:id "wohnungen"}
       [:h2.title.is-3.has-text-centered "Wohnungen"]
       [:div.columns.is-multiline.is-centered
        (map (partial wohnung-card req) wohnungen)]]

      [:div.block.px-4
       [:h2.title.is-3.has-text-centered {:id "anreise"} "Anreise"]
       [:div.textabschnitt
        [:div.columns
         [:div.column
          [:div.content (ph/dangerous-html (or anreisetext ""))]]
         [:div.column
          [:div.columns.is-mobile.is-vcentered
           [:div.column.is-narrow (maplink google_maplink)]
           [:div.column (interpose [:br] adresszeilen)]]]]]]

      [:div.block.px-4
       [:h2.title.is-3.has-text-centered {:id "ausfluege"} "Ausflugtips"]
       [:div.columns.is-multiline.is-centered
        (map (partial ausflugsvorschau req id) ausfluege)]]

      [:div.block.textabschnitt.px-4
       [:h2.title.is-3.has-text-centered {:id "buchung"} "Buchungsanfrage"]
       [:div.content (ph/dangerous-html (or buchungstext ""))]]

      [:div.block.has-text-centered.pb-4.px-4
       [:a {:href (str (rt/path-fixed :buchung req)
                       (when-let [w (first wohnungen)]
                         (str "?default=" (:id w))))}
        [:button.button.is-link "Jetzt Anfragen"]]]]]))

(defhandler handler [req]
  (p/let [locale    (:locale req)
          haus-id   (-> req :path-params :hausid)
          haus-id   (if (string? haus-id) (js/parseInt haus-id 10) haus-id)
          haus      (-> (db/query (q/haus-detail locale haus-id)) (.then first))
          bilder    (db/query (q/haus-bilder haus-id))
          wohnungen (db/query (q/wohnungen-by-haus locale haus-id))
          ausfluege (db/query (q/ausfluege-by-haus locale haus-id))]
    (templates/render-page
     req
     {:titel        (:name haus)
      :beschreibung (:meta_description haus)}
     (page-body req haus bilder wohnungen ausfluege))))
