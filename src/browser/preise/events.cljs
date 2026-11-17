(ns preise.events
  "Events of the price admin. Views dispatch these and never touch app-db.
   Every season edit addresses the season by `:id`. Every edit of the price
   data bumps `:rev` and sets the save state to `:ungespeichert`; selection
   and trash arming are UI state and do not count as edits."
  (:require [ajax.edn :as edn]
            [preise.db :as db]
            [preise.fx :as fx]
            [re-frame.core :as rf]))

;; ---------------------------------------------------------------------------
;; Load

(def login-uri
  "Where a 401 sends the operator: the admin page itself, whose page
   middleware redirects to the Directus login. (Legacy read a `location`
   header first, but the API's 401 never carries one.)"
  "/admin/preise")

(rf/reg-event-fx ::load
  [(rf/inject-cofx ::fx/heute) (rf/inject-cofx ::fx/jetzt)]
  (fn [{:keys [heute jetzt]} _]
    ;; ?t= cache-bust as legacy preise/ajaxing.cljs: an old max-age entry in
    ;; the browser cache must not mask freshly saved prices.
    (let [req (fn [uri on-success]
                {:method          :get
                 :uri             (str uri "?t=" jetzt)
                 :response-format (edn/edn-response-format)
                 :on-success      [on-success]
                 :on-failure      [::load-failed]})]
      {:db         (db/default-db heute)
       :http-xhrio [(req "/api/preise/refdata" ::refdata-loaded)
                    (req "/api/preise/data" ::daten-loaded)]})))

(defn- fertig-wenn-komplett [db]
  (let [{:keys [refdata daten]} (get-in db [:ui :geladen])]
    (cond
      (not (and refdata daten)) db

      (not (and (map? daten) (map? refdata)))
      (-> db (assoc :status :load-failed) (assoc-in [:ui :geladen] {}))

      :else
      (let [[daten selected] (db/von-disk daten)]
        (-> db
            (assoc :status    :ready
                   :wohnungen (db/wohnungen-map (:wohnungen refdata))
                   :daten     daten)
            (assoc-in [:ui :selected] selected)
            (assoc-in [:ui :geladen] {}))))))

(defn- geladen [db k v]
  (if (= :loading (:status db))
    (-> db (assoc-in [:ui :geladen k] v) fertig-wenn-komplett)
    db))

(rf/reg-event-db ::refdata-loaded
  (fn [db [_ body]] (geladen db :refdata body)))

(rf/reg-event-db ::daten-loaded
  (fn [db [_ body]] (geladen db :daten body)))

(rf/reg-event-fx ::load-failed
  (fn [{:keys [db]} [_ {:keys [status] :as result}]]
    (cond
      (not= :loading (:status db))
      {}

      (= 401 status)
      {:db        (-> db (assoc :status :not-logged-in) (assoc-in [:ui :geladen] {}))
       ::fx/redirect login-uri}

      :else
      {:db       (-> db (assoc :status :load-failed) (assoc-in [:ui :geladen] {}))
       ::fx/warn ["preise: load failed" status (pr-str (:response result))]})))

;; ---------------------------------------------------------------------------
;; Edits

(defn- edit
  "Applies `f` to `db` as a data edit: bumps `:rev`, marks unsaved."
  [db f & args]
  (-> (apply f db args)
      (update :rev inc)
      (assoc-in [:ui :save-state] :ungespeichert)))

(defn- update-saison
  "Applies `f` to the season(s) with id `id`."
  [db id f & args]
  (update-in db [:daten :saisonen]
             (fn [ss] (mapv #(if (= id (:id %)) (apply f % args) %) ss))))

(defn- warn-unknown [db what x]
  (js/console.warn "preise: unknown" what (pr-str x))
  db)

(rf/reg-event-db ::set-allg
  (fn [db [_ k v]]
    (if (db/allg-felder k)
      (edit db assoc-in [:daten :allgdaten k] v)
      (warn-unknown db "allg field" k))))

(rf/reg-event-db ::set-basis
  (fn [db [_ wohnungs-id k v]]
    (if (db/basis-felder k)
      (edit db assoc-in [:daten :basisdaten wohnungs-id k] v)
      (warn-unknown db "basis field" k))))

(rf/reg-event-db ::set-saison-feld
  (fn [db [_ id wohnungs-id k v]]
    (if (db/saison-felder k)
      (edit db update-saison id assoc-in [:felder wohnungs-id k] v)
      (warn-unknown db "season field" k))))

(rf/reg-event-db ::set-saison-datum
  ;; Legacy datepicker: a change making beginn ≥ ende is rejected and the
  ;; old value kept (was js/alert, now an inline message as data).
  (fn [db [_ id feld iso]]
    {:pre [(#{:beginn :ende} feld)]}
    (let [alt (first (filter #(= id (:id %)) (get-in db [:daten :saisonen])))]
      (cond
        (nil? alt)
        (warn-unknown db "season" id)

        (db/dates-bad? (assoc alt feld iso))
        (assoc-in db [:ui :datum-fehler] {:id id :feld feld :error :beginn-nicht-vor-ende})

        :else
        (-> db
            (assoc-in [:ui :datum-fehler] nil)
            (edit update-saison id assoc feld iso))))))

(rf/reg-event-fx ::add-saison
  [(rf/inject-cofx ::fx/heute)]
  (fn [{:keys [db heute]} _]
    {:db (edit db update-in [:daten :saisonen]
               (fn [ss] (db/add-saison ss (db/next-saison ss heute))))}))

(rf/reg-event-db ::toggle-select
  (fn [db [_ id]]
    (update-in db [:ui :selected] #(if (% id) (disj % id) (conj % id)))))

(rf/reg-event-db ::unselect-all
  (fn [db _]
    (assoc-in db [:ui :selected] #{})))

(rf/reg-event-db ::clone-selected-next-year
  (fn [db _]
    (let [sel (get-in db [:ui :selected])]
      (if (seq sel)
        (-> db
            (edit update-in [:daten :saisonen] db/clone-naechstes-jahr sel)
            (assoc-in [:ui :selected] #{}))
        db))))

(defn- remove-saisonen [db ids]
  (-> db
      (edit update-in [:daten :saisonen] (fn [ss] (into [] (remove #(ids (:id %))) ss)))
      (update-in [:ui :selected] #(reduce disj % ids))
      (update-in [:ui :trash-armed] #(reduce disj % ids))
      (update-in [:ui :datum-fehler] #(when-not (ids (:id %)) %))))

(rf/reg-event-db ::delete-selected
  (fn [db _]
    (let [sel (get-in db [:ui :selected])]
      (if (seq sel) (remove-saisonen db sel) db))))

(rf/reg-event-db ::arm-trash
  (fn [db [_ id armed?]]
    (update-in db [:ui :trash-armed] (if armed? conj disj) id)))

(rf/reg-event-db ::trash-saison
  ;; Only an armed trash deletes (legacy: first click arms, the second
  ;; image deletes).
  (fn [db [_ id]]
    (if (get-in db [:ui :trash-armed id])
      (remove-saisonen db #{id})
      db)))

;; ---------------------------------------------------------------------------
;; Save

(rf/reg-event-fx ::save
  [(rf/inject-cofx ::fx/csrf-token)]
  (fn [{:keys [db csrf-token]} _]
    (if (or (not= :ready (:status db))
            (= :ongoing (get-in db [:ui :save-state])))
      {}
      {:db         (assoc-in db [:ui :save-state] :ongoing)
       :http-xhrio {:method          :post
                    :uri             "/api/preise/save"
                    :params          {:preisstruktur (db/auf-disk db)}
                    :format          (edn/edn-request-format)
                    :response-format (edn/edn-response-format)
                    :headers         {"X-CSRF-Token" csrf-token}
                    :on-success      [::save-ok (:rev db)]
                    :on-failure      [::save-failed (:rev db)]}})))

(defn save-state-for
  "Save state after a failed save (legacy `post-preise!`): 401 → not
   logged in, 400 → rejected by the server check, else request failed."
  [status]
  (case status
    401 :falsches-passwort
    400 :fehlgeschlagen
    :req-failed))

(rf/reg-event-db ::save-ok
  ;; Edits made while the save was in flight stay unsaved.
  (fn [db [_ rev]]
    (assoc-in db [:ui :save-state] (if (= rev (:rev db)) :erfolgreich :ungespeichert))))

(rf/reg-event-fx ::save-failed
  (fn [{:keys [db]} [_ rev {:keys [status] :as result}]]
    {:db       (assoc-in db [:ui :save-state]
                         (if (= rev (:rev db)) (save-state-for status) :ungespeichert))
     ::fx/warn ["preise: save failed" status (pr-str (:response result))]}))
