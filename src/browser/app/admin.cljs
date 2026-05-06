(ns app.admin
  (:require
   [app.modal :as modal]
   [app.obfuscate :as obfuscate]
   [app.reframe :as arf]
   [cljs-http.client :as http]
   [cljs-time.coerce :as time.coerce]
   [cljs-time.core :as t]
   [cljs.core.async :refer [<!]]
   [clojure.string :as str]
   [comp.snippets :as snip]
   [garden.core :as garden]
   [malli.core :as m]
   [psite-config.env :as env]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [specs.book :as specs])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(rf/reg-fx ::clipboard-copy
           (fn [content]
             (-> (js/navigator.permissions.query
                  (clj->js {"name" "clipboard-write"}))
                 (.then (fn [result]
                          (when (#{"granted" "prompt"} (.-state result))
                            (-> (js/navigator.clipboard.writeText content)
                                (.then #(modal/set-content "Emailadressen kopiert")
                                       #(js/alert "copying failed")))))))))

(rf/reg-event-fx ::emails-to-clickboard
                 (fn [{:keys [db]} [_ concert-id filter-language]]
                   (let [emails (keep
                                 (fn [{:keys [concert language email]}]
                                   (when (and (= concert concert-id)
                                              (if filter-language
                                                (= language
                                                   filter-language)
                                                true))
                                     email))
                                 (get-in db [:data :reservations]))
                         emails (str/join "," emails)]
                     {::clipboard-copy emails})))

(rf/reg-sub ::reservations-for-concert
            :<- [:get-in [:data :reservations]]
            (fn [reservations [_ concert-id]]
              (filter #(= (:concert %) concert-id) reservations)))

(defn compose-email
  [])

(defn reservation-c  [reservation]
  [])

(defn concert-c [{:keys [id title datetime] :as concert}]
  (let [reservations @(rf/subscribe [::reservations-for-concert id])
        total-attendees (reduce #(+ %1 (:attendees %2)) 0 reservations)
        emails (set (map :email reservations))]
    [:section.section {:data-test-concert-id id
                       :key id}
     [:h2 (t/day datetime) "." (t/month datetime)
      " " title " (" [:span {:data-test-id "total-attendees"} total-attendees] ")"]
     [:div "Emailadressen kopieren:"]
     [:button
      {:data-test-id "all-reservers"
       :on-click #(rf/dispatch [::emails-to-clickboard id nil])}
      "Alle"]
     [:button
      {:data-test-id (str "uk-reservers-" id)
       :on-click #(rf/dispatch [::emails-to-clickboard id "uk"])}
      "Ukrainisch"]
     [:button
      {:data-test-id (str "en-reservers-" id)
       :on-click #(rf/dispatch [::emails-to-clickboard id "en"])}
      "Englisch"]
     [:button
      {:data-test-id (str "de-reservers-" id)
       :on-click #(rf/dispatch [::emails-to-clickboard id "de"])}
      "Deutsch"]
     [:div {:style {:margin-top "15px"}} "Erinnerungs-Email herunterladen:"]
     [:a.button {:href (str "/admin/reminder-eml/" id "/all")} "Alle"]
     [:a.button {:href (str "/admin/reminder-eml/" id "/uk")} "Ukrainisch"]
     [:a.button {:href (str "/admin/reminder-eml/" id "/en")} "Englisch"]
     [:a.button {:href (str "/admin/reminder-eml/" id "/de")} "Deutsch"]
     [:div {:style {:margin-top "15px"}}
      [:a.button {:href (str "/admin/reservations/" id)
                  :target "_blank"}
       "Reservierungsliste"]]]))

(defn admin []
  (into [:div [:h1 "Reservierungen" " (" (reduce #(+ %1 (:attendees %2)) 0
                                                 @(rf/subscribe [:get-in [:data :reservations]])) ")"]]
        (map concert-c @(rf/subscribe [:get-in [:data :concerts]]))))

(defn ^:dev/after-load mount []
  (rf/clear-subscription-cache!)
  (rdom/render [admin] (js/document.getElementById "admin")))

(defn ^:export main [{:keys [reservations concerts]}]
  (println "Admin ready")
  (rf/dispatch-sync [:set [:data] {:reservations
                                   (filter (complement :cancelled) reservations)
                                   :concerts concerts}])
  (mount))

(comment
  @(rf/subscribe [::reservations-by-concert]))



