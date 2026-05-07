(ns seiten.aktuelles
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [psite-hiccup.core :as ph]
            [psite-datetime.core :as td]
            [db.setup :as db]
            [db.queries :as q]
            [seiten.templates :as templates]
            [directus.core :as d]))

(defn- date-parts
  "Extract [day month year] from a date value. Handles goog.date.UtcDateTime
   (which the pg pool returns for date columns via cljs-time.coerce/from-date,
   shifted into UTC) by reading the original JS Date wrapped inside, and falls
   back to JS Date and ISO strings."
  [datum]
  (cond
    (nil? datum) nil
    ;; goog.date.* exposes getDate/getMonth/getYear in its own (UTC) frame,
    ;; but a plain JS Date with the desired local-midnight is what the pg
    ;; driver originally produced. Pull it back out of UtcDateTime.
    (and (some? datum) (fn? (.-getTime datum)))
    (let [js-d (js/Date. (.getTime datum))]
      [(.getDate js-d) (inc (.getMonth js-d)) (.getFullYear js-d)])
    (string? datum)
    (let [d (js/Date. datum)]
      (when-not (js/isNaN (.getTime d))
        [(.getDate d) (inc (.getMonth d)) (.getFullYear d)]))))

(defn- fmt-datum [datum locale]
  (when-let [[day month year] (date-parts datum)]
    (let [mname (td/month->string {:locale (or locale :de) :variant 1} month)]
      (str day ". " mname " " year))))

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
