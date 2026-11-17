(ns api.routes
  {:clj-kondo/config {:linters {:unresolved-namespace {:level :off}}}}
  #?(:node (:require
            [api.anfrage :as anfrage]
            [api.buchungsproxy :as buchungsproxy]
            [api.ping :as ping]
            [api.preise :as preise]
            [config.env :as env]
            [psite-middleware.core :as middleware]
            [psite-rate-limit.core :as rate-limit]
            [setup.directus-auth :as directus-auth]))
  #?(:clj  (:require      [psite-routing.macros :as prm])
     :cljs (:require-macros [psite-routing.macros :as prm])))

(def routes
  (#?(:node identity :default prm/routes-reduced-for-matching)
   ["/api/" {:middleware [(when (env/setting :hide-errors?)
                            (partial middleware/wrap-error-response-for-user
                                     (middleware/json-converter)))
                          middleware/wrap-edn-params]}
    ["ping"                 {:name :ping            :handler ping/handler}]
    ["buchungsproxy"        {:name :buchungsproxy   :handler buchungsproxy/handler}]
    ["anfrage"              {:name       :anfrage
                             :middleware [(rate-limit/wrap-rate-limit :anfrage)]
                             :handler    anfrage/handler}]

    ["preise" {:middleware [directus-auth/wrap-directus-user
                            directus-auth/require-directus-user-401]}
     ["/data"    {:name :api-preise-data    :handler preise/data}]
     ["/save"    {:name :api-preise-save    :handler preise/save}]
     ["/refdata" {:name :api-preise-refdata :handler preise/refdata}]]]))
