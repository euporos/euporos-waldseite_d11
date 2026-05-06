(ns seiten.home
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.util.response :as r]
            [psite-routing.core :as routing])
  (:require-macros [hiccups.core :refer [html5]]))

(defhandler blankhome [req]
  (let [coerced-locale (:locale req)
        query-string (:query-string req)
        target (str (routing/reverse-match req :home
                                            {:locale (or (#{:de :uk} coerced-locale) :de)})
                    (when query-string (str "?" query-string)))]
    (r/found target)))

(defhandler handler [_req]
  (-> (r/ok (html5
             [:head [:meta {:charset "UTF-8"}] [:title "Waldseite – Home"]]
             [:body
              [:h1 "Home (Stub)"]
              [:p "This is the public home page stub."]
              [:p [:a {:href "admin"} "Admin →"]]]))
      (r/content-type "text/html; charset=utf-8")))
