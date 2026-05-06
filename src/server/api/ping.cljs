(ns api.ping
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.util.response :as r]))

(defhandler handler [_req]
  (-> (r/ok {:status "ok"
             :message "pong"})
      (r/content-type "application/edn")))
