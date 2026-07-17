(ns seiten.translate
  "Site-wide translation tool for the /admin/uebersetzungen dashboard.

   Thin site shim over psite-translate.core: this namespace holds only what is
   specific to Bickels Ferienwohnungen — the `translatables` schema map, the
   `cfg` that wires in this site's config (gateway url/key, db query fn,
   LLM_SITE_* context), and the standalone dashboard page — and delegates the
   actual gaps/start/job logic to the shared module.

   The /admin group already exists (guarded by wrap-directus-user), so the
   dashboard and its API live alongside the existing admin stub:

     GET  /admin/uebersetzungen          → the dashboard page (this ns)
     GET  /admin/api/translation/gaps?langs=en,nl
     POST /admin/api/translation/start   {collection field pk langs}
     GET  /admin/api/translation/job?id=<jobId>

   All four are mounted under the /admin group, so wrap-directus-user has
   attached :directus-user.

   NOTE: `translatables` is the single source of truth for what gets translated.
   The Directus schema snapshot is not shipped to prod, so this cannot be derived
   at runtime — keep it in sync by hand when the translation schema changes
   (add/remove a translated field, or a new translated collection)."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [config.env :as env]
            [db.setup :as db]
            [kitchen-async.promise :as p]
            [macchiato-async.core :refer-macros [defhandler]]
            [psite-translate.core :as pt]
            [seiten.templates :as templates]
            [setup.directus-auth :as directus-auth]))

;; ---------------------------------------------------------------------------
;; Schema: what to translate
;; ---------------------------------------------------------------------------
;;
;; Per collection:
;;   :table       - the *_translations table
;;   :fk          - foreign-key column back to the parent (<coll>_id)
;;   :label-field - a text field used to label the item in the UI list; must be
;;                  one of :fields (only those columns are fetched for the label)
;;   :status?     - whether the *_translations table has a `status` column
;;                  (copied onto newly-inserted rows). Every waldseite
;;                  translation table has one, so this is true throughout.
;;   :fields      - {<field> :plain|:html}; :html fields are Directus
;;                  input-rich-text-html fields, sent to the gateway with
;;                  format "html" so markup is preserved; everything else :plain.

(def translatables
  {:haeuser      {:table :haeuser_translations :fk :haeuser_id
                  :label-field :beschreibung :status? true
                  :fields {:beschreibung :html :ausstattung :html
                           :anreisetext :html :buchungstext :html
                           :meta_description :plain :ausstattung_tabelle :plain}}
   :wohnungen    {:table :wohnungen_translations :fk :wohnungen_id
                  :label-field :beschreibung :status? true
                  :fields {:beschreibung :html :ausstattung_tabelle :plain}}
   :einzelseiten {:table :einzelseiten_translations :fk :einzelseiten_id
                  :label-field :titel :status? true
                  :fields {:titel :plain :text :html :meta_description :plain}}
   :ausfluege    {:table :ausfluege_translations :fk :ausfluege_id
                  :label-field :titel :status? true
                  :fields {:titel :plain :beschreibung :html}}
   :galerie      {:table :galerie_translations :fk :galerie_id
                  :label-field :beschreibung :status? true
                  :fields {:beschreibung :plain}}
   :news         {:table :news_translations :fk :news_id
                  :label-field :titel :status? true
                  :fields {:titel :plain :text :html}}
   :startseite   {:table :startseite_translations :fk :startseite_id
                  :label-field :hauptueberschrift :status? true
                  :fields {:hauptueberschrift :plain :haupttext :html}}
   :allgemeines  {:table :allgemeines_translations :fk :allgemeines_id
                  :label-field :ausstattung_tabelle :status? true
                  :fields {:ausstattung_tabelle :plain}}
   :articles     {:table :articles_translations :fk :articles_id
                  :label-field :title :status? true
                  :fields {:title :plain :body :plain}}
   :fixe_seiten  {:table :fixe_seiten_translations :fk :fixe_seiten_id
                  :label-field :titel :status? true
                  :fields {:titel :plain :beschreibung :plain}}})

;; ---------------------------------------------------------------------------
;; Site config handed to the shared module
;; ---------------------------------------------------------------------------

(def ^:private cfg
  {:translatables translatables
   :source-lang   "de"
   :max-chars     30000
   :site-context  (pt/site-context-from-settings env/setting)
   :gateway-url   (env/setting [:llm-gateway :url])
   :gateway-key   (env/setting [:llm-gateway :api-key])
   :query         db/query})

;; ---------------------------------------------------------------------------
;; Dashboard page — its own route under the existing /admin group
;; ---------------------------------------------------------------------------

(defhandler page-handler [req]
  (if-not (:directus-user req)
    (directus-auth/directus-login-redirect req)
    (let [locale (or (some-> (:locale req) name)
                     (get-in req [:path-params :locale])
                     "de")]
      (templates/render-page
       req
       {:titel    "Übersetzungen"
        :noindex? true
        :notrack? true
        :cljs     {:onload  "app.translate.main"
                   :js-data {:locale locale}}}
       [:section
        [:div.panel.is-primary.mainpanel
         [:div.block.textabschnitt.mt-4.py-4.px-4
          [:h2.title.is-3.has-text-centered "Übersetzungen"]
          [:div#admin]]]]))))

;; ---------------------------------------------------------------------------
;; API handlers — delegate to the shared module
;; ---------------------------------------------------------------------------

(defhandler gaps-handler  [req] (pt/gaps cfg req))
(defhandler start-handler [req] (pt/start cfg req))
(defhandler job-handler   [req] (pt/job cfg req))
