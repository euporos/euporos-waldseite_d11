(ns api.routes
  {:clj-kondo/config {:linters {:unresolved-namespace {:level :off}}}}
  #?(:node (:require
            [api.anfrage :as anfrage]
            [api.buchungsproxy :as buchungsproxy]
            [api.ping :as ping]
            [api.qr :as qr]
            [api.wohnungen-voll :as wohnungen-voll]
            [config.env :as env]
            [psite-middleware.core :as middleware]
            [reitit.coercion.malli :as malli-coercion]))
  #?(:clj  (:require      [psite-routing.macros :as prm])
     :cljs (:require-macros [psite-routing.macros :as prm])))

(def routes
  (#?(:node identity :default prm/routes-reduced-for-matching)
   ["/api/" {:coercion   malli-coercion/coercion
             :middleware [(when (env/setting :hide-errors?)
                            (partial middleware/wrap-error-response-for-user
                                     (middleware/json-converter)))
                          middleware/wrap-edn-params]}
    ["ping"                 {:name :ping            :handler ping/handler}]
    ["wohnungen-und-preise" {:name :wohnungen-voll  :handler wohnungen-voll/handler}]
    ["buchungsproxy"        {:name :buchungsproxy   :handler buchungsproxy/handler}]
    ["anfrage"              {:name :anfrage         :handler anfrage/handler}]
    ["qr"                   {:name :qr              :handler qr/handler}]]))
