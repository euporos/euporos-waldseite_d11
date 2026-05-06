(ns app.youtube
  (:require-macros [hiccups.core :as hiccups :refer [html]])
  (:require [goog.net.Cookies]
            [hiccups.runtime :as hiccupsrt]))

(def cookies (goog.net.Cookies. js/document))

(defn youtube-iframe [src]
  [:div {:style "position: relative; padding-bottom: 56.25%; height: 0; overflow: hidden;"}
   [:iframe
    {:style "position: absolute; top: 0; left: 0; width: 100%; height: 100%;"
     :allowfullscreen true
     :src src
     :title "YouTube video player"
     :frameborder "0"}]])

(defn mount-youtube []
  (let [elements (.querySelectorAll js/document "[data-youtube-src]")]
    (doseq [el (js->clj elements)]
      (set! (.-outerHTML el)
            (html
             (youtube-iframe
              (.getAttribute el "data-youtube-src")))))))

(defn consent []
  (.set cookies "consented_youtube" true (clj->js {:maxAge (* 60 60 24 180)}))
  (mount-youtube))

(defn attach-listeners []
  (let [elements (.querySelectorAll js/document ".youtube-consent-button")]
    (doseq [el (js->clj elements)]
      (.addEventListener el "click"
                         (fn [_event]
                           (consent))))))

(.addEventListener js/document "DOMContentLoaded"
                   (fn [e]
                     (if true #_(.get cookies "consented_youtube" false)
                       (mount-youtube)
                       (attach-listeners))))
