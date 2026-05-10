(ns seiten.home
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.util.response :as r]
            [kitchen-async.promise :as p]
            [psite-hiccup.core :as ph]
            [psite-routing.core :as routing]
            [db.setup :as db]
            [db.queries :as q]
            [seiten.templates :as templates]
            [serving.routing :as rt]
            [directus.core :as d]))

(defhandler blankhome [req]
  (let [coerced-locale (:locale req)
        query-string   (:query-string req)
        target         (str (routing/reverse-match req :home
                                                   {:locale (or (#{:de :uk} coerced-locale) :de)})
                            (when query-string (str "?" query-string)))]
    (r/found target)))

(defn- house-card [req {:keys [id name hauptbild]}]
  [:div.column
   [:div.container
    [:div.card.base-background
     [:div.card-image.card-image--hoverable
      [:figure.image.is-4by3
       [:a {:href (rt/path-haus req id name)}
        [:img {:width "100%"
               :src   (d/image-by-preset "600" hauptbild)}]]]]
     [:div.is-size-3 name]]]])

(defn- page-body [req startseite haeuser]
  (let [{:keys [hauptueberschrift haupttext familienbild]} startseite]
    [:section
     [:div.panel.is-primary.mainpanel
      [:div.py-4.px-4
       [:div.block.willkommen
        [:h1.title.is-2
         "Herzlich willkommen" [:span.is-hidden-tablet "…"]
         [:span.is-hidden-mobile [:br] "in unseren Ferienhäusern…"]]]

       [:div.block
        [:div.columns.is-mobile.has-text-centered
         (map (partial house-card req) haeuser)]]

       [:div.block
        [:h2.title.is-3.has-text-centered
         (or hauptueberschrift "…wo der Bayerische Wald am schönsten ist.")]
        [:div.content (ph/dangerous-html (or haupttext ""))]]

       (when familienbild
         [:div.columns
          [:div.column.is-one-third]
          [:div.column.is-italic.has-text-centered
           [:span.is-size-3 "Ihre Familie Bickel"] [:br]
           [:div.mt-4
            [:img {:src (d/image-by-preset "600" familienbild)}]]]])]]]))

(defhandler handler [req]
  (p/let [locale     (:locale req)
          startseite (-> (db/query (q/startseite-content locale)) (.then first))
          haeuser    (db/query (q/haeuser-overview locale))]
    (templates/render-page
     req
     {:titel        "Bickels Ferienwohnungen — Bayerischer Wald"
      :beschreibung "Ferienwohnungen in Falkenstein, Bayerischer Wald."
      :og-image     "/imgs/zwei_daecher.png"}
     (page-body req startseite haeuser))))
