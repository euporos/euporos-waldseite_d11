(ns seiten.routes
  {:clj-kondo/config {:linters {:unresolved-namespace {:level :off}}}}
  #?(:node (:require
            [seiten.admin :as admin]
            [seiten.home :as home]
            [setup.directus-auth :as directus-auth]))
  #?(:clj  (:require      [psite-routing.macros :as prm])
     :cljs (:require-macros [psite-routing.macros :as prm])))

(def routes
  (#?(:node identity :default prm/routes-reduced-for-matching)
   [["/" {:name    :home
          :handler home/handler}]

    ["/admin" {:middleware [directus-auth/wrap-directus-user]}
     ["" {:handler admin/handler
          :name    :admin}]]]))
