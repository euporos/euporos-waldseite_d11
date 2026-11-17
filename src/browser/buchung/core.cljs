(ns buchung.core
  "Booking SPA entry point: mounts the re-frame app into #mainframe.
   `seiten.buchung` calls `buchung.core.main` with the transit-decoded
   `:js-data` (`{:locale …}`). State: `buchung.db`/`events`/`subs`/`fx`;
   views: `buchung.views.*`. Single entry namespace so shadow-cljs can ship
   it in the `:browser` build."
  (:require [re-frame.core :as rf]
            [reagent.dom :as rdom]
            [buchung.events :as ev]
            [buchung.subs]
            [buchung.fx]
            [buchung.views.formular :as views]))

(defn- mount! []
  (when-let [el (.getElementById js/document "mainframe")]
    (rdom/render [views/root] el)))

(defn ^:dev/after-load start []
  (rf/clear-subscription-cache!)
  (mount!))

(defn ^:export main [{:keys [locale]}]
  (rf/dispatch-sync [::ev/init locale])
  (mount!))
