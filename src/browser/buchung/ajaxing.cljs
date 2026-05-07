(ns buchung.ajaxing
  "Two server interactions: pull the apartments+prices snapshot from
   /api/buchungsproxy on mount, and POST the booking-request to
   /api/anfrage on submit."
  (:require [cljs-http.client :as http]
            [cljs-time.core :as t]
            [cljs.core.async :refer [<!]]
            [buchung.utils :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- timify-saisonen [saisonen]
  (->> saisonen
       (map (fn [{:keys [beginn ende] :as s}]
              (let [b (u/parse-iso beginn)
                    e (u/parse-iso ende)]
                (-> s
                    (assoc :beginn b :ende e)
                    (assoc :interval (when (and b e) (t/interval b e)))))))
       vec))

(defn- numparse-recursively
  "Walks `data` and parses values at any of `ks` from string to number.
   Mirrors the legacy edn-proc/numparse-recursively-selective behaviour
   that the SPA's preisberechnung relies on."
  [data ks]
  (let [keyset (set ks)]
    (letfn [(walk [v]
              (cond
                (map? v)        (into {} (map (fn [[k v]]
                                                [k (if (and (keyset k) (string? v) (seq v))
                                                     (let [n (js/parseFloat v)]
                                                       (if (js/isNaN n) v n))
                                                     (walk v))])
                                              v))
                (sequential? v) (mapv walk v)
                :else           v))]
      (walk data))))

(def ^:private numeric-fields
  [:mindestaufenthalt :mindestaufenthalt_standard :grundpreis
   :preis_woche :preis_tag :reinigung-unter :standardbelegung
   :maximalbelegung :reinigung :aufschlag_zus_person
   :aufschlag_haustier :gaestebeitrag :energieaufschlag])

(defn- rework-preise [preise]
  (-> preise
      (update :saisonen timify-saisonen)
      (numparse-recursively numeric-fields)))

(defn- rework-wohnungen
  "Filter out single-night vevents and coerce dates to cljs-time."
  [wohnungen]
  (mapv
   (fn [w]
     (update w :belegung
             (fn [vs]
               (->> (u/timify-vevents vs)
                    (filter (fn [{:keys [dtstart dtend]}]
                              (> (t/in-hours (t/interval dtstart dtend)) 47)))
                    vec))))
   wohnungen))

(defn fetch-data!
  "Load apartments+prices, populate the supplied atoms, then call
   `done`. Reads the response body string as EDN — the proxy returns
   application/edn."
  [!preise !wohnungen done]
  (go
    (let [resp (<! (http/get "/api/buchungsproxy" {:with-credentials? false}))
          {:keys [wohnungen preise]} (cljs.reader/read-string (:body resp))]
      (reset! !preise   (rework-preise preise))
      (reset! !wohnungen (rework-wohnungen wohnungen))
      (done))))

(defn- csrf-token []
  (some-> (.getElementById js/document "ifg") (.getAttribute "token")))

(defn post-anfrage!
  "POST the request to /api/anfrage. Resolves the supplied atom to
   :success / :failed."
  [anfrage !poststate]
  (reset! !poststate :pending)
  (go
    (let [resp (<! (http/post "/api/anfrage"
                              {:form-params {:__anti-forgery-token (csrf-token)
                                             :anfragedaten         (pr-str anfrage)}}))]
      (reset! !poststate (if (:success resp) :success :failed)))))
