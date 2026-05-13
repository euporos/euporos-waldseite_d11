(ns setup.tracking
  "Cookieless visitor identity for analytics.

   Computes a stable per-visitor id as SHA256(salt + remote-addr + user-agent),
   stored on the macchiato session under [:session :tracking :id]. The salt
   lives in a file outside the working tree and rotates daily at midnight,
   so the same browser is recognised across sessions until rotation —
   without ever issuing an analytics cookie."
  (:require
   ["crypto" :as crypto]
   ["fs" :as fs]
   ["node-schedule" :as schedule]
   [mount.core :refer [defstate]]
   [taoensso.timbre :refer [info]]))

(def rotation-interval-ms
  ;; One day. Returning visitors share an id for up to this long.
  86400000)

(def ^:private salt-path "../salt")

(defn- sha256 [s]
  (-> crypto (.createHash "sha256") (.update s) (.digest "hex")
      (js/Buffer.from) (.toString "base64")))

(defonce ^:private salt (atom nil))
(defonce ^:private last-salt-rotation-timestamp (atom nil))

(defn- generate-salt []
  (str (rand-int 1000000000000000)))

(defn- load-salt! []
  (reset! salt (.toString (.readFileSync fs salt-path)))
  (reset! last-salt-rotation-timestamp
          (.getTime (.-mtime (.statSync fs salt-path))))
  (info "Salt loaded"))

(defn- rotate-salt! []
  (.writeFileSync fs salt-path (generate-salt))
  (info "Salt rotated")
  (load-salt!))

(defn- init! []
  (when-not (.existsSync fs salt-path)
    (info "No salt exists, creating")
    (rotate-salt!))
  (let [last-modified (.getTime (.-mtime (.statSync fs salt-path)))]
    (if (> (- (js/Date.now) rotation-interval-ms) last-modified)
      (do (info "Salt has expired, rotating") (rotate-salt!))
      (do (info "Salt is current; last modified" last-modified) (load-salt!)))))

(declare salt-rotation)

(defstate salt-rotation
  :start (let [job (.scheduleJob schedule "0 0 * * *" rotate-salt!)]
           (init!)
           job)
  :stop  (.cancel @salt-rotation))

(defn- expired? [now req]
  (when-let [ts (get-in req [:session :tracking :timestamp])]
    (> (- now ts) rotation-interval-ms)))

(defn wrap-tracking-id
  "Ensures every request has [:session :tracking :id] set, writing the value
   back into the response session so it persists across requests."
  [handler]
  (fn [req res raise]
    (let [now (js/Date.now)
          current (get-in req [:session :tracking :id])]
      (if (and current (not (expired? now req)))
        (handler req res raise)
        (let [{:keys [remote-addr headers]} req
              new-id (sha256 (str @salt remote-addr (get headers "user-agent")))
              stamp  (fn [session]
                       (-> (or session {})
                           (assoc-in [:tracking :id] new-id)
                           (assoc-in [:tracking :timestamp] now)))]
          (handler
           (update req :session stamp)
           (fn [response]
             ;; If the inner handler didn't touch :session, preserve req's
             ;; session so we don't blow away other session keys that the
             ;; session middleware would otherwise persist.
             (res (assoc response :session
                         (stamp (or (:session response) (:session req))))))
           raise))))))
