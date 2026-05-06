(ns seiten.admin
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.util.response :as r]
            [setup.directus-auth :as directus-auth])
  (:require-macros [hiccups.core :refer [html5]]))

(defhandler handler [req]
  (if-not (:directus-user req)
    (directus-auth/directus-login-redirect req)
    (-> (r/ok (html5
               [:head [:meta {:charset "UTF-8"}] [:title "Waldseite – Admin"]]
               [:body
                [:h1 "Admin (Stub)"]
                [:p "Logged in as: "
                 [:strong (or (get-in req [:directus-user :email])
                              (str (:directus-user req)))]]]))
        (r/content-type "text/html; charset=utf-8"))))
