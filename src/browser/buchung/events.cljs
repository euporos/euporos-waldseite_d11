(ns buchung.events
  "Events of the booking SPA. Views dispatch these and never touch app-db.
   Domain rules live in `buchung.*` under src/common; the state layer only
   stores the selection and the server's answers."
  (:require [ajax.edn :as edn]
            [buchung.db :as db]
            [buchung.fx :as fx]
            [re-frame.core :as rf]
            [specs.anfrage :as specs]))

;; ---------------------------------------------------------------------------
;; Boot and data

(defn- fetch-data-fx
  "Effects of loading the apartments+prices snapshot (EDN)."
  [db]
  {:db         (assoc db :status :loading)
   :http-xhrio {:method          :get
                :uri             "/api/buchungsproxy"
                :response-format (edn/edn-response-format)
                :on-success      [::data-loaded]
                :on-failure      [::data-failed]}})

(rf/reg-event-fx ::init
  [(rf/inject-cofx ::fx/heute)]
  (fn [{:keys [heute]} [_ locale]]
    (fetch-data-fx (db/default-db (keyword (or locale :de)) heute))))

(rf/reg-event-fx ::fetch-data
  (fn [{:keys [db]} _]
    (fetch-data-fx db)))

(defn- clamp-gaestezahl
  "Legacy `gaestezahl-select` at render time: more guests than the wohnung
   allows → guests set to the maximum and the warning shown."
  [db]
  (let [max-n (db/maximalbelegung (db/normalized-preise db) (get-in db [:auswahl :wohnungsid]))]
    (if (> (get-in db [:auswahl :gaestezahl]) max-n)
      (-> db
          (assoc-in [:auswahl :gaestezahl] max-n)
          (assoc-in [:ui :ueberbelegung?] true))
      db)))

(rf/reg-event-db ::data-loaded
  (fn [db [_ {:keys [wohnungen preise]}]]
    (-> db
        (assoc :status    :ready
               :wohnungen (vec wohnungen)
               :preise    preise)
        clamp-gaestezahl)))

(rf/reg-event-db ::data-failed
  (fn [db _]
    (assoc db :status :load-failed)))

;; ---------------------------------------------------------------------------
;; Selection

(rf/reg-event-db ::select-wohnung
  (fn [db [_ wohnungsid]]
    (-> db
        (assoc-in [:auswahl :wohnungsid] wohnungsid)
        (assoc-in [:ui :ueberbelegung?] false)
        clamp-gaestezahl)))

(rf/reg-event-db ::set-gaestezahl
  (fn [db [_ n]]
    (-> db
        (assoc-in [:auswahl :gaestezahl] n)
        (assoc-in [:ui :ueberbelegung?] false))))

(rf/reg-event-db ::set-haustierzahl
  (fn [db [_ n]]
    (assoc-in db [:auswahl :haustierzahl] n)))

(rf/reg-event-db ::pick-date
  (fn [db [_ picker iso]]
    {:pre [(contains? db/pickers picker)]}
    (assoc-in db [:auswahl picker] iso)))

(rf/reg-event-db ::shift-month
  (fn [db [_ picker unit n]]
    {:pre [(contains? db/pickers picker) (#{:month :year} unit)]}
    (update-in db [:ui :picker picker :shown-month] db/shift-month unit n)))

(rf/reg-event-db ::set-field
  (fn [db [_ path value]]
    (if (contains? db/feld-pfade path)
      (assoc-in db path value)
      (do (js/console.warn "buchung: unknown field" (pr-str path))
          db))))

;; ---------------------------------------------------------------------------
;; Submit

(rf/reg-event-fx ::submit
  [(rf/inject-cofx ::fx/csrf-token)]
  (fn [{:keys [db csrf-token]} _]
    (let [db      (assoc-in db [:ui :submit-attempted?] true)
          anfrage (db/anfrage db (db/normalized-preise db))]
      (cond
        (= :pending (get-in db [:ui :post :state]))
        {:db db}

        (not (specs/valid? anfrage))
        {:db db}

        :else
        {:db         (assoc-in db [:ui :post] {:state :pending :error nil})
         :http-xhrio {:method          :post
                      :uri             "/api/anfrage"
                      :params          anfrage
                      :format          (edn/edn-request-format)
                      :response-format (edn/edn-response-format)
                      :headers         {"X-CSRF-Token" csrf-token}
                      :on-success      [::submit-ok]
                      :on-failure      [::submit-failed]}}))))

(rf/reg-event-db ::submit-ok
  (fn [db _]
    (assoc-in db [:ui :post] {:state :success :error nil})))

(defn server-error
  "The EDN error the server answered with (`{:error :belegt}`,
   `{:error :mindestaufenthalt :min n}`, `{:error :invalid :fields […]}` …),
   or `{:error :unbekannt :status n}` when the failure carried none."
  [{:keys [response status]}]
  (if (and (map? response) (keyword? (:error response)))
    response
    {:error :unbekannt :status status}))

(rf/reg-event-fx ::submit-failed
  (fn [{:keys [db]} [_ result]]
    {:db       (assoc-in db [:ui :post] {:state :failed :error (server-error result)})
     ::fx/warn ["anfrage rejected" (:status result) (pr-str (:response result))]}))
