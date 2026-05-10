(ns seiten.aktuelles
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [psite-hiccup.core :as ph]
            [db.setup :as db]
            [db.queries :as q]
            [seiten.components.dates :refer [fmt-datum]]
            [seiten.templates :as templates]
            [directus.core :as d]))

(defn- format-newsitem [locale {:keys [id bild titel text datum]}]
  [:div.columns
   {:id (str "newsitem-" id)}
   (when bild
     [:div.column.is-one-quarter
      [:img {:width "100%"
             :src   (d/image-by-preset "600" bild)}]])
   [:div.column
    [:h2.title.is-4 titel
     (when-let [s (fmt-datum datum locale)]
       [:span ", " s])]
    [:div.content (ph/dangerous-html (or text ""))]]])

(defn- page-body [locale newsitems]
  [:section
   [:div.panel.is-primary.mainpanel
    [:div.block.textabschnitt.mt-4.py-4.px-4
     [:h2.title.is-3.has-text-centered
      {:id "aktuelles"}
      "Aktuelles"]
     (map (partial format-newsitem locale) newsitems)]]])

(defhandler handler [req]
  (p/let [locale    (:locale req)
          newsitems (db/query (q/news-overview locale))]
    (templates/render-page
     req
     {:titel "Aktuelles — Bickels Ferienwohnungen"
      :beschreibung "Neuigkeiten von Bickels Ferienwohnungen im Bayerischen Wald."}
     (page-body locale newsitems))))
