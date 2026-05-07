(ns serving.stub
  (:require [seiten.templates :as templates]))

(defn- stub-body [req route-name]
  [:section.section
   [:div.container
    [:h1.title.is-3 (str "Stub: " (name route-name))]
    [:table.table
     [:tbody
      [:tr [:th "method"]       [:td (str (:request-method req))]]
      [:tr [:th "uri"]          [:td (:uri req)]]
      [:tr [:th "path-params"]  [:td [:code (pr-str (:path-params req))]]]
      [:tr [:th "query-params"] [:td [:code (pr-str (:query-params req))]]]]]]])

(defn make-handler [route-name]
  (fn [req res raise]
    (-> (templates/render-page
         req
         {:titel (str "Stub: " (name route-name))}
         (stub-body req route-name))
        (.then res)
        (.catch raise))))
