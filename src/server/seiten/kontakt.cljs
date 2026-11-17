(ns seiten.kontakt
  "GET /:locale/kontakt — the JavaScript-free contact form. The form hiccup
   lives in `kontakt.formular`, the submit check in `kontakt.pruefung`;
   `seiten.kontaktform` re-renders the page through `formular-seite`."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [comp.snippets :as snip]
            [db.queries :as q]
            [db.setup :as db]
            [kontakt.formular :as formular]
            [kontakt.pruefung :as pruefung]
            [seiten.templates :as templates]
            [serving.routing :as rt]
            [psite-routing.core :as routing]))

(defn- datenschutz-href
  "Promise of the path of the privacy-policy einzelseite, or nil."
  [req]
  (let [locale (:locale req)]
    (-> (js/Promise.resolve
         (p/let [rows-de     (db/query (q/einzelseiten-for-sitemap :de))
                 rows-locale (db/query (q/einzelseiten-for-sitemap locale))]
           (when-let [{:keys [id titel]} (formular/datenschutz-seite rows-de rows-locale)]
             (rt/path-einzelseite req id titel))))
        (.catch (fn [_] nil)))))

(defn formular-seite
  "Promise of the full contact page. `opts` may carry `:werte`, `:fehler`,
   `:hinweis` (see `kontakt.formular/formular`); a fresh token is always
   embedded."
  [req opts]
  (p/let [locale (:locale req)
          href   (datenschutz-href req)]
    (templates/render-page
     req
     {:titel        (snip/kontakt locale)
      :beschreibung (snip/kontakt-meta-desc locale)}
     [:section
      [:div.panel.mainpanel
       [:div.textabschnitt.py-4.px-4
        [:h1.title.is-2 (snip/kontakt locale)]
        (formular/formular
         (merge opts
                {:locale           locale
                 :action           (routing/reverse-match req :api_kontakt {})
                 :af-token         (:af-token req)
                 :token            (pruefung/token pruefung/schluessel (.now js/Date))
                 :datenschutz-href href}))]]])))

(defhandler handler [req]
  (formular-seite req {}))
