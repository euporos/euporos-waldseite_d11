(ns preise.core
  "Price admin entry point: loads the data and mounts the re-frame app into
   #mainframe (`seiten.preise` ships it as the `:admin` build). State:
   `preise.db`/`events`/`subs`/`fx`; views: `preise.views`."
  (:require [preise.events :as ev]
            [preise.fx]
            [preise.subs]
            [preise.views :as views]
            [re-frame.core :as rf]
            [reagent.dom :as rdom]))

(defn- mount! []
  (when-let [el (.getElementById js/document "mainframe")]
    (rdom/render [views/root] el)))

(defn ^:dev/after-load start []
  (rf/clear-subscription-cache!)
  (mount!))

(defn ^:export main []
  (rf/dispatch-sync [::ev/load])
  (mount!))
