(ns seiten.wohnung
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [clojure.string :as str]
            [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [psite-hiccup.core :as ph]
            [psite-routing.core :as routing]
            [psite-seo.json-ld :as ld]
            [db.setup :as db]
            [db.queries :as q]
            [preise.lookup :as plookup]
            [seiten.components.gallery :as gallery]
            [seiten.templates :as templates]
            [serving.routing :as rt]
            [directus.core :as d]))

(defn- fmt-eur [n]
  (when n
    (if (= n (js/Math.floor n))
      (str (int n))
      (-> n (.toFixed 2)))))

(defn- combine-tables [& tabelle-strings]
  (->> tabelle-strings
       (remove (fn [s] (or (nil? s) (str/blank? s))))
       (str/join "\n")
       not-empty))

(defn- preis-rows [{:keys [maximalbelegung mindestaufenthalt_standard
                           tag-min woche-min]}]
  (list
   (when maximalbelegung
     [:tr
      [:td "Platz für bis zu"]
      [:td (int maximalbelegung) " Personen"]])
   (when (or tag-min woche-min)
     [:tr
      [:td "Preise ab"]
      [:td
       (when tag-min   [:span (fmt-eur tag-min) " €/Nacht"])
       (when (and tag-min woche-min) [:br])
       (when woche-min [:span (fmt-eur woche-min) " €/Woche"])]])
   (when mindestaufenthalt_standard
     [:tr
      [:td "Mindestaufenthalt"]
      [:td "ab " (int mindestaufenthalt_standard) " Nächte"]])))

(defn- ausstattung-table [tabelle-string dtvsterne preise]
  [:div.ausstattung-table
   [:div.card
    [:table.table
     [:tbody
      (preis-rows preise)
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

(defn- page-body [req wohnung bilder ausstattung-string preise]
  (let [{:keys [id name beschreibung hauptbild dtvsterne]} wohnung]
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
         [:div.wohnungbeschreibung__text
          (ph/dangerous-html (or beschreibung ""))]
         [:div.wohnungbeschreibung__ausstattung
          (ausstattung-table ausstattung-string dtvsterne preise)]]]]

      [:div.mb-4.has-text-centered.pb-4
       [:a {:href (str (rt/path-fixed :buchung req)
                       "?default=" id)}
        [:button.button.is-link
         {:type "submit" :value "Submit"}
         "Jetzt Anfragen"]]]]]))

(defhandler handler [req]
  (p/let [locale       (:locale req)
          wohnung-id   (-> req :path-params :wohnungsid)
          wohnung-id   (if (string? wohnung-id) (js/parseInt wohnung-id 10) wohnung-id)
          wohnung      (-> (db/query (q/wohnung-detail locale wohnung-id)) (.then first))
          bilder       (db/query (q/wohnung-bilder wohnung-id))
          haus-tabelle (when (:haus wohnung)
                         (-> (db/query (q/haus-ausstattung-tabelle locale (:haus wohnung)))
                             (.then (comp :ausstattung_tabelle first))))
          allg         (-> (db/query (q/allgemeines-content locale))
                           (.then (comp :ausstattung_tabelle first)))
          ausstattung  (combine-tables (:ausstattung_tabelle wohnung) haus-tabelle allg)
          preise       (plookup/wohnung-summary wohnung-id)]
    (templates/render-page
     req
     {:titel        (str "Wohnung " (:name wohnung))
      :beschreibung ""
      :og-image     (when-let [img (:hauptbild wohnung)]
                      (d/image-by-preset "og" img))
      :breadcrumbs  [{:name "Bickels"      :url (routing/reverse-match req :home {})}
                     {:name "Ferienhäuser" :url (routing/reverse-match req :haeuser {})}
                     {:name (str "Wohnung " (:name wohnung))
                      :url  (:url req)}]
      :json-ld      (ld/entity
                     :Apartment
                     {:name  (str "Wohnung " (:name wohnung))
                      :url   (routing/make-path-absolute req (:url req))
                      :image (when-let [img (:hauptbild wohnung)]
                               (d/image-by-preset "og" img))})}
     (page-body req wohnung bilder ausstattung preise))))
