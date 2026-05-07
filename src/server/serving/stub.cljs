(ns serving.stub
  (:require [macchiato.util.response :as r]))

(defn- stub-body [req]
  (str "STUB " (:request-method req) " " (:uri req) "\n"
       "path-params:  " (pr-str (:path-params req)) "\n"
       "query-params: " (pr-str (:query-params req)) "\n"))

(defn make-handler [route-name]
  (fn [req res _raise]
    (res (-> (r/ok (str "[" route-name "]\n" (stub-body req)))
             (r/content-type "text/plain; charset=utf-8")))))
