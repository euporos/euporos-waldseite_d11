(ns seiten.templates
  (:require
   [cljs-time.core :as t]
   [cljstache.core :as tache]
   [clojure.string :as str]
   [comp.snippets :as snip]
   [db.setup :as db]

   [garden.core :as garden]
   [goog.string :as gstring]
   [goog.string.format]
   [kitchen-async.promise :as p]
   [psite-menu.core :as pmenu]
   [psite-routing.core :as routing]
   [psite-seo.core :as seo]
   [psite-transit.core :as transit]
   [psite-transit.cljs-time :as transit.time]
   [psite-hiccup.core :as ph]
   [seiten.navigation :as nav]
   [serving.routing :as rt]
   [shadow.resource :as rc])
  (:require-macros [hiccups.core :as hiccups :refer [html html5]]
                   [psite-utils.macros :as m]))

;; #############
;; ### Blank ###
;; #############

(def writer
  (transit/make-writer {:handlers transit.time/write-handlers}))

(def full-locales
  {:de "de-DE"
   :en "en-US"})

(def font-awesome
  (list [:script {:src "/compiled/fontawesome-free-6.4.0-web/js/brands.min.js"
                  :defer true}]
        [:script {:src "/compiled/fontawesome-free-6.4.0-web/js/solid.min.js"
                  :defer true}]
        [:script {:src "/compiled/fontawesome-free-6.4.0-web/js/fontawesome.min.js"
                  :defer true}]))

(defn blank_hiccup
  "Empty page… "
  [req head-data & comps]
  (let [{:keys [titel beschreibung into-head
                og-image breadcrumbs cljs raw-title
                noindex? notrack?]
         :or   {head-data []}} head-data
        locale                 (keyword (-> req :path-params :locale))
        ;; titlesupp (by-locale locale (:title dpage))
        localestring           (get full-locales locale)
        title                  (or raw-title
                                   (str (when titel (str titel " | "))
                                        (get-in req [:config :site-name])))]

    (list (into [:head
                 (seo/charset)
                 (seo/viewport)

                 (seo/robots
                  (when (or (get-in req [:parameters :query :debug])
                            noindex?
                            (get-in req [:config :noindex]))
                    :noindex))

                 #_(seo/ld
                    (seo/breadcrumb-list
                     (or breadcrumbs [{:name titel :url (:url req)}])))

                 [:script {:src (m/cache-bust "/js/pedestrian.js")}]

                 (when (:noindex (get-in req [:config :noindex]))
                   (seo/http-equiv "Cache-Control" "no-store"))

                 (seo/google-site-verification
                  (get-in req [:config :google-site-verfication])) ;TODO: make setting

                 (seo/title title)

                 (seo/open-graph
                  {:title       title
                   :description beschreibung
                   :image       (when og-image
                                  (if (re-find #"^https?://" og-image)
                                    og-image
                                    (routing/make-path-absolute req og-image)))
                   :url         (routing/make-path-absolute req (:url req))
                   :type        "website"})

                 (seo/description beschreibung)

                 (seo/favicon-set {:base-path "/imgs/favicon"})

                 (when (:reitit.core/router req)
                   (seo/alternates
                    (for [locale (get-in req [:config :locale-fallback])]
                      {:hreflang (name locale)
                       :url      (rt/switch-locale-and-prepend-domain locale req)})))

                 [:link
                  {:rel  "stylesheet"
                   :href (m/cache-bust "/compiled/bundle/main.css") :type "text/css" :media "screen"}]

                 [:script
                  {:src   (m/cache-bust "/compiled/bundle/main.js")
                   :defer true}]

                 (when (and (get-in req [:config :posthog])
                            (not notrack?))
                   [:script
                    (ph/dangerous-html (tache/render (rc/inline "posthog.js") (-> (get-in req [:config :posthog])
                                                                                 (assoc :posthog-identity
                                                                                        (get-in req
                                                                                                [:cookies "macchiato-session.sig" :value])
                                                                                        :tracking-id (get-in req
                                                                                                             [:session :tracking :id])))))])]

                into-head)

          [:body {:lang locale}

           comps

           (:browser-env-script req)

           (let [{:keys [onload js-data]} cljs
                 js-data (assoc js-data :csrf-token (:af-token req))
                 argument                 (when js-data (str "'" (js/escape (transit/serialize writer js-data)) "'"))
                 onload-string                   (str
                                                  onload
                                                  "("
                                                  "app.core.readarg" "(" argument ")"
                                                  ")")]
             [:script {:src (m/cache-bust "/compiled/js/app.js") :onload onload-string}])]

          #_[:script
             "console.log('" settings/mode " build " settings/build-id " ')"])))

(defn blank
  [req head-data & comps]
  (html5 {:lang (:locale req)}
         (apply (partial blank_hiccup req head-data) comps)))

;; ###########################
;; ###### Head & Footer ######
;; ###########################

(defn locale-switcher [locale string req]
  [:a.navbar-item {:href (rt/switch-locale-and-prepend-domain locale req)}
   string])

(let [switchers [[:de (partial locale-switcher :de "Deutsch")]
                 [:uk (partial locale-switcher :uk "Українска")]
                 [:en (partial locale-switcher :en "English")]]]
  (defn locale-switchers [req]
    (keep
     (fn [[locale func]]
       (when-not (= locale (:locale req)) (func req)))
     switchers)))

(defn alternate-locales  [current-locale])

(defn navbar [{:keys [locale session] :as req}]
  [:nav.navbar
   [:div.navbar-brand
    [:a.navbar-item
     {:href (routing/reverse-match req :home {})
      :style (garden/style {:position "relative"})}
     [:div
      {:style (garden/style {:width "55px"
                             :height "28px"
                             :background-size "cover"
                             :background (str "url('" (m/cache-bust "/imgs/logo.svg") "') no-repeat left")})}]
     (locale-switchers req)]
    [:div.navbar-burger.burger
     {:data-target "navbarExampleTransparentExample"}
     [:span]
     [:span]
     [:span]]]
   [:div#navbarExampleTransparentExample.navbar-menu
    [:div.navbar-start
     [:a.navbar-item {:href (routing/reverse-match req :home {})} "Home"]
     ;; TODO: Häuser dropdown — populate from (:dresponses req) once a fetcher is wired.
     ;; TODO: Buchung / Aktuelles / Galerie / Ausflüge once those routes land.
     ]
    [:div.navbar-end.mr-3]]])
(defn footer [{:keys [locale] :as _req}]
  [:footer.footer
   [:div.content.has-text-centered
    [:p
     [:img {:src   "/imgs/nationalpark.png"
            :alt   "Nationalpark Bayerischer Wald"
            :style "max-width: 180px;"}]]
    [:p [:a {:href "https://www.facebook.com/" :target "_blank"}
         [:img {:src   "/imgs/icons/facebook-black.png"
                :alt   "Facebook"
                :style "width: 32px; height: 32px;"}]]]]])


(defn head-and-foot-blank
  [req head-data dynamic-menus & comps]
  (blank req head-data
         (navbar req)
         [:div#modal]
         comps
         (footer req)))

(defn router-free
  [req head-data & comps]
  (blank req head-data
         [:div#modal]
         comps))

