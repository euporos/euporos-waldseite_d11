(ns preise.speichern
  "Server-side check and dispatch of a price admin save (POST
   /api/preise/save). Config-free so the Node tests can run it with a stub.

   The body is EDN `{:preisstruktur <map>}` (`:edn-params`). Checks:
   1. body/preisstruktur not a map or schema mismatch (`preise.model/schema`)
      → 400 `{:error :invalid :explain <humanized>}`
   2. a season with `:beginn` after `:ende`   → 400 `{:error :saison-umgekehrt :ids [..]}`
   3. duplicate season ids                    → 400 `{:error :doppelte-saison-id :ids [..]}`
   Overlapping seasons stay allowed (the UI only warns).

   `deps`: `:write!` (fn [edn-string] :ok | :parse-fail | :verify-fail),
   `:after-save!` (fn [] response) called after a successful write."
  (:require [malli.error :as me]
            [preise.model :as pm]))

(defn edn-response [status body]
  {:status  status
   :headers {"Content-Type" "application/edn"}
   :body    (pr-str body)})

(defn pruefe
  "nil if `m` is acceptable, else the EDN error body."
  [m]
  (cond
    (not (map? m))
    {:error :invalid :explain "preisstruktur is not a map"}

    (not (pm/valid? m))
    {:error :invalid :explain (me/humanize (pm/explain m))}

    :else
    (let [ss        (:saisonen m)
          umgekehrt (vec (keep #(when (pos? (compare (:beginn %) (:ende %))) (:id %)) ss))
          doppelt   (vec (map key (filter #(> (val %) 1) (frequencies (map :id ss)))))]
      (cond
        (seq umgekehrt) {:error :saison-umgekehrt :ids umgekehrt}
        (seq doppelt)   {:error :doppelte-saison-id :ids doppelt}))))

(defn respond
  [deps req]
  (let [m (get-in req [:edn-params :preisstruktur])]
    (if-let [err (pruefe m)]
      (edn-response 400 err)
      (case ((:write! deps) (pr-str m))
        :ok          ((:after-save! deps))
        :parse-fail  (edn-response 400 {:error :invalid :explain "unparseable"})
        :verify-fail (edn-response 500 {:error :verify-fail})))))
