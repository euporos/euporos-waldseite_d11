(ns app.book
  (:require
   [app.modal :as modal]
   [app.obfuscate :as obfuscate]
   [app.reframe :as arf]
   [app.router :refer [reverse-match]]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [comp.snippets :as snip]
   [garden.core :as garden]
   [malli.core :as m]
   [psite-config.env :as env]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [reagent.dom :as rdom]
   [specs.book :as specs])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; (def payload (r/atom {}))

;; {:on-change (fn [e]
;;               (swap! payload #(assoc % :name (-> e .-target .-value))))
;;  :value (:name @payload)
;;  :type "text"}

(def time-started (atom (.now js/Date)))

(rf/reg-sub ::payload-valid?
            :<- [:get-in [:payload]]
            (fn [payload _]
              (m/validate specs/payload payload)))

(rf/reg-sub ::payload-error-fields
            :<- [:get-in [:payload]]
            (fn [payload _]
              (set
               (map
                (comp first :path)
                (:errors (m/explain specs/payload payload))))))

(rf/reg-sub ::field-invalid?
            :<- [::payload-error-fields]
            (fn [error-set [_ path]]
              (error-set path)))

(rf/reg-event-db ::invalid-attempt
                 (fn [db _]
                   (assoc db :invalid-attempted? true)))

(defn stop-event [event]
  (doto event
    .preventDefault
    .stopPropagation))

(defn thank-you  [message]
  [:div
   {:style {:width "100%"
            :text-align "center"
            :z-index "10"
            :opacity "80%"
            :height "500px"
            :background-color "white"
            :position "absolute"}}
   [:div {:style {:opacity "100%"
                  :color "black"
                  :margin-top "25%"}}
    message]])

(defn book []
  (let [invalid-attempted? @(rf/subscribe [:get-in [:invalid-attempted?]])
        locale @(rf/subscribe [:get-in [:locale]])
        error-fields-set @(rf/subscribe [::payload-error-fields])
        show-invalid? (fn [field] (and invalid-attempted? (error-fields-set field)))]
    (cond (= :succeeded @(rf/subscribe [:get-in [:status]]))
          [:div
           [:h2.title.has-text-centered (snip/reservierung-erfolgreich locale)]]

          @(rf/subscribe [:get-in [:sold_out]])
          [:div [:h2.title.has-text-centered "ausgebucht"]
           "Dieses Konzert ist ausverkauft. "
           (when-let [alternative-id (first @(rf/subscribe [:get-in [:additional-performance-ids]]))]
             [:a {:href (reverse-match :concert {:concert-id alternative-id})}
              "Klicken Sie hier für einen Alternativtermin."])]

          :else
          [:div
           [:h2.title.has-text-centered (snip/reserve-seats locale)]
           [:form
        ;; (when (= :succeeded @(rf/subscribe [:get-in [:status]]))
        ;;   [thank-you
        ;;    "Vielen Dank, Ihre Reservierung wurde registriert!"])
        ;; (when (= :failed @(rf/subscribe [:get-in [:status]]))
        ;;   [thank-you "Das hat leider nicht geklappt. Laden Sie sie Seite neu, um es erneut zu versuchen."])
            [:div.field
             [:label.label (snip/name-snip locale)]
             [:div.control
              [:input.input
               {:data-test-id "name-input"
                :class (when (show-invalid? :name) "is-danger")
                :on-change #(rf/dispatch-sync [:set [:payload :name]
                                               (-> % .-target .-value)])
                :value @(rf/subscribe [:get-in [:payload :name]])
                :type "text"}]]
             (when (show-invalid? :name)
               [:p.help.is-danger (snip/bitte-name locale)])]
            [:div.field
             [:label.label "Email"]
             [:div.control ;;.has-icons-left.has-icons-right
              [:input.input
               {:data-test-id "email-input"
                :class (when (show-invalid? :email) "is-danger")
                :on-change #(rf/dispatch-sync [:set [:payload :email]
                                               (-> % .-target .-value)])
                :value @(rf/subscribe [:get-in [:payload :email]])
                :type "email"}]
              #_[:span.icon.is-small.is-left
                 [:i.fas.fa-envelope]]
              #_[:span.icon.is-small.is-right
                 [:i.fas.fa-exclamation-triangle]]]
             (when (show-invalid? :email)
               [:p.help.is-danger (snip/bitte-email locale)])]
            [:div.field
             {:style {:display "none"
                      :visibility "hidden"}}
             [:label.label
              {:aria-hidden true
               :for "phone"}
              (snip/telefonnummer locale)]
             [:div.control
              [:input.input
               {:autoComplete "off"
                :name "phone"
                :required true
                :id "phone"
                :on-change #(rf/dispatch-sync [:set [:payload :phone]
                                               (-> % .-target .-value)])
                :value @(rf/subscribe [:get-in [:payload :phone]])
                :type "email"}]]
             (when (show-invalid? :email)
               [:p.help.is-danger (snip/bitte-nicht-ausfuellen locale)])]
            [:div.field
             [:label.label (snip/anzahl-personen locale)]
             [:div.control
              [:div.select
               [:select
                {:data-test-id "concert-input"
                 :on-change #(rf/dispatch-sync [:set [:payload :attendees]
                                                (int (-> % .-target .-value))])
                 :value @(rf/subscribe [:get-in [:payload :attendees]])}
                (map (fn [val]
                       [:option {:value val :key val} val]) (range 1 10))]]]]
            [:div.field
             [:label.label (snip/additional-message locale)]
             [:div.control
              [:textarea.textarea
               {:data-test-id "message-input"
                :max-length  specs/message-max-length
                :on-change #(rf/dispatch-sync [:set [:payload :message]
                                               (-> % .-target .-value)])
                :value @(rf/subscribe [:get-in [:payload :message]])}]]]
            [:div.field
             [:div.control
              [:label.checkbox
               [:input
                {:data-test-id "privacy-input"
                 :type "checkbox"
                 :on-change #(rf/dispatch-sync [:set [:payload :privacy-accepted?]
                                                (-> % .-target .-checked)])
                 :checked (boolean @(rf/subscribe [:get-in [:payload :privacy-accepted?]]))}]
               (snip/ich-akzeptiere locale) " "
               [:a
                {:href (reverse-match :single-page {:page-id 2 :page-slug "datenschutz"})} (snip/datenschutzerklärung locale)] "."]]
             (when (show-invalid? :privacy-accepted?)
               [:p.help.is-danger (snip/datenschutzregelung locale)])]
            [:div.field.is-grouped
             [:div.control
              [:button#booking-button.button
               {:class [(when @(rf/subscribe [::payload-valid?]) "is-warning")
                        (when (= :running @(rf/subscribe [:get-in [:status]])) "is-loading")]
                :on-click
                (cond

                  (= :running @(rf/subscribe [:get-in [:status]]))
                  nil

                  (not @(rf/subscribe [::payload-valid?]))
                  #(do (stop-event %)
                       (rf/dispatch [::invalid-attempt])
                       (.capture js/posthog "Ungültiger Buchungsversuch"
                                 (clj->js {:time-spent (- (.now js/Date)
                                                          @(rf/subscribe [:get-in [:time-started]]))})))

                  :else
                  (let [payload (assoc @(rf/subscribe [:get-in [:payload]])
                                       :time-spent (- (.now js/Date)
                                                      @(rf/subscribe [:get-in [:time-started]])))
                        token @(rf/subscribe [:get-in [:csrf-token]])]
                    (fn [e]
                      (stop-event e)
                      (modal/set-content [modal/notes])
                      (go
                        (let [result (<! (http/post
                                          (reverse-match :book)
                                          {:edn-params (assoc payload
                                                              :time-spent (- (.now js/Date)
                                                                             @time-started))
                                           :headers {"X-CSRF-Token" token}}))
                              succeeded? (:success result)]
                          (rf/dispatch [:set [:status] (if succeeded? :succeeded :failed)])
                          (if succeeded?
                            (.capture js/posthog "Buchung erfolgreich"
                                      (clj->js {}))
                            (.capture js/posthog "Buchung fehlgeschlagen"
                                      (clj->js {:error (get-in result [:body :error])})))
                          (modal/set-content
                           [:div {:data-test-id "booking-message"
                                  :data-test-booking-status (str (or (get-in result [:body :error])
                                                                     :success))}

                            (if succeeded?
                              (snip/reservierung-erfolgreich locale)

                              (case (get-in result [:body :error])
                                :too-fast (snip/etwas-zu-schnell locale)
                                :spam (snip/spamfilter locale)
                                [:<> (snip/leider-nicht-geklappt locale)]))]))))))}

               (snip/reserve-seats locale)]]]]])))

(defn ^:dev/after-load mount []
  (rf/clear-subscription-cache!)
  (rdom/render [book] (js/document.getElementById "book")))

(defn ^:export main [{:keys [default-payload] :as init-data}]
  (println "Booking ready")
  (rf/dispatch-sync [:set-db init-data])
  (rf/dispatch-sync [:set [:time-started] (.now js/Date)])
  (rf/dispatch-sync [:set [:payload] default-payload])
  (mount))




