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
            [comp.snippets :as snip]
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

;; Portal ratings ("bewertungen") -----------------------------------------
;; Scales differ per platform (FeWo-direkt and Booking.com /10, Google /5), so
;; the score is always rendered together with its max_wert — never normalised.

(def ^:private plattform-namen
  {"fewo-direkt" "FeWo-direkt"
   "google"      "Google"
   "booking"     "Booking.com"})

(defn- ->zahl
  "Numerics arrive from node-pg as strings."
  [v]
  (when-let [n (some-> v str js/parseFloat)]
    (when-not (js/isNaN n) n)))

(defn- fmt-wert
  "Score with one decimal, no trailing \".0\"; decimal comma in de/nl, point in en."
  [locale n]
  (cond-> (if (= n (js/Math.floor n)) (str (int n)) (.toFixed n 1))
    (not= :en locale) (str/replace "." ",")))

(defn- sterne
  "Five stars filled to `anteil` (0–1). The fill is a plain coloured bar and the
   star shapes are a CSS mask over it, so the boundary lands anywhere inside a
   star — 9,6/10 is exactly 96 % of the bar, not a rounded half-star. Decorative
   only: the score sits next to it as text."
  [anteil]
  [:span.sternewertung {:aria-hidden "true"}
   [:span.sternewertung__fuellung
    {:style (str "width:" (.toFixed (* 100 (min 1 (max 0 anteil))) 2) "%")}]])

(defn- effektive-bewertungen
  "One row per platform: an apartment-level row wins over the house-level one."
  [bewertungen]
  (->> bewertungen
       (remove (fn [{:keys [wohnung haus]}] (and (nil? wohnung) (nil? haus))))
       (group-by :plattform)
       (sort-by key)
       (map (fn [[_ rows]] (or (first (filter :wohnung rows)) (first rows))))))

(defn- bewertung-rows [locale bewertungen]
  (for [{:keys [plattform wert max_wert]} (effektive-bewertungen bewertungen)
        :let [wert     (->zahl wert)
              max_wert (->zahl max_wert)]
        :when (and wert max_wert (pos? max_wert))]
    [:tr
     [:td (get plattform-namen plattform plattform)]
     [:td
      [:span.bewertung__zahl (fmt-wert locale wert) "/" (fmt-wert locale max_wert)]
      (sterne (/ wert max_wert))]]))

(defn- preis-rows [locale {:keys [maximalbelegung mindestaufenthalt_standard
                                  tag-min woche-min]}]
  (list
   (when maximalbelegung
     [:tr
      [:td (snip/platz-fuer-bis-zu locale)]
      [:td (int maximalbelegung) " " (snip/personen locale)]])
   (when (or tag-min woche-min)
     [:tr
      [:td (snip/preise-ab locale)]
      [:td
       (when tag-min   [:span (fmt-eur tag-min) " " (snip/eur-pro-nacht locale)])
       (when (and tag-min woche-min) [:br])
       (when woche-min [:span (fmt-eur woche-min) " " (snip/eur-pro-woche locale)])]])
   (when mindestaufenthalt_standard
     [:tr
      [:td (snip/mindestaufenthalt locale)]
      [:td (snip/ab locale) " " (int mindestaufenthalt_standard) " " (snip/naechte locale)]])))

(defn- ausstattung-table [locale tabelle-string dtvsterne preise bewertungen]
  [:div.ausstattung-table
   [:div.card
    [:table.table
     [:tbody
      (preis-rows locale preise)
      (when (and dtvsterne (pos? dtvsterne))
        [:tr
         [:td [:a {:target "_blank" :rel "noopener"
                   :href "https://www.deutschertourismusverband.de/qualitaet/sterneunterkuenfte.html"}
               "DTV-Sterne"]]
         [:td (repeat dtvsterne [:i.dtvstern])]])
      (bewertung-rows locale bewertungen)
      (for [line (when tabelle-string (str/split-lines tabelle-string))
            :let [cells (str/split line #"::")]]
        [:tr
         (if (= 1 (count cells))
           [:td.has-text-centered {:colspan 2} (first cells)]
           (for [c cells] [:td c]))])]]]])

(defn- page-body [req locale wohnung bilder ausstattung-string preise bewertungen]
  (let [{:keys [id name beschreibung hauptbild dtvsterne]} wohnung]
    [:section
     [:div.panel.mainpanel
      [:div.block (gallery/bilder-gallery
                   "gallery-wohnung"
                   (cons hauptbild
                         (remove #{hauptbild} (map :directus_files_id bilder))))]

      [:div.block.textabschnitt.py-4.px-4
       [:h1.title.is-2 (snip/wohnung locale) " " name]
       [:div.content
        [:div.wohnungbeschreibung
         [:div.wohnungbeschreibung__text
          (ph/dangerous-html (or beschreibung ""))]
         [:div.wohnungbeschreibung__ausstattung
          (ausstattung-table locale ausstattung-string dtvsterne preise bewertungen)]]]]

      [:div.mb-4.has-text-centered.pb-4
       [:a {:href (str (rt/path-fixed :buchung req)
                       "?default=" id)}
        [:button.button.is-link
         {:type "submit" :value "Submit"}
         (snip/jetzt-anfragen locale)]]]]]))

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
          preise       (plookup/wohnung-summary wohnung-id)
          bewertungen  (db/query (q/bewertungen-for-wohnung wohnung-id (:haus wohnung)))]
    (templates/render-page
     req
     {:titel        (str (snip/wohnung locale) " " (:name wohnung))
      :beschreibung ""
      :og-image     (when-let [img (:hauptbild wohnung)]
                      (d/image-by-preset "og" img))
      :breadcrumbs  [{:name "Bickels"                   :url (routing/reverse-match req :home {})}
                     {:name (snip/ferienhaeuser locale) :url (routing/reverse-match req :haeuser {})}
                     {:name (str (snip/wohnung locale) " " (:name wohnung))
                      :url  (:url req)}]
      :json-ld      (ld/entity
                     :Apartment
                     {:name  (str (snip/wohnung locale) " " (:name wohnung))
                      :url   (routing/make-path-absolute req (:url req))
                      :image (when-let [img (:hauptbild wohnung)]
                               (d/image-by-preset "og" img))})}
     (page-body req locale wohnung bilder ausstattung preise bewertungen))))
