(ns ^:dev/always serving.routes
  (:require
   [api.routes :as api]
   [api.sitemap :as sitemap]
   [db.setup]
   [macchiato.middleware.params :as params]
   [macchiato.middleware.restful-format :as rf]
   [psite-middleware.core :as middleware]
   [psite-routing.core :as routing]
   [reitit.coercion.malli]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [seiten.home :as home]
   [seiten.routes :as seiten]
   [seiten.test-page :as test-page]))

(def routes
  ;; The /directus/* reverse proxy is dispatched in serving.core/app, before
  ;; the macchiato middleware stack runs, so request bodies survive for POST
  ;; login etc. It is therefore not registered here.
  [api/routes

   ["/" {:coercion   reitit.coercion.malli/coercion
         :parameters {:query [:map
                              [:debug {:optional true} :boolean]]}}
    ["" home/blankhome]
    ["sitemap.xml" {:name :sitemap :handler sitemap/handler}]
    ["test" {:name :test :handler test-page/handler}]

    [":locale" {:middleware []}
     ;; No :parameters {:path …} here — locale is keywordized by
     ;; routing/wrap-locale, and a parent path schema would force reitit
     ;; to merge with each leaf's path schema (needs malli.util).

     seiten/routes]]])

(def router
  (ring/router
   [routes]
   {;; einzelseite is an int-prefixed catch-all under /:locale/. Reitit
    ;; would refuse to build the router otherwise; its trie still prefers
    ;; the static siblings (haeuser, kontakt, …) at match time.
    :conflicts nil
    :data {:middleware [params/wrap-params
                        routing/wrap-locale
                        middleware/wrap-synchronous-exceptions
                        #(rf/wrap-restful-format % {:keywordize? true})
                          ;; middleware/wrap-body-to-params
                        rrc/coerce-request-middleware
                        rrc/coerce-response-middleware]}}))
