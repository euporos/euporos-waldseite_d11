(ns analytics.posthog
  "Server-side PostHog analytics client.
   Reads config from :posthog {:project-id … :host …} in settings.edn
   (overridable via env vars POSTHOG__PROJECT_ID / POSTHOG__HOST).
   All public functions are safe to call when PostHog is unconfigured — they
   become no-ops."
  (:require ["posthog-node" :refer [PostHog]]
            [config.env :as env]
            [mount.core :refer [defstate]]
            [taoensso.timbre :refer [infof warnf]]))

(declare client)

(defstate client
  :start (let [{:keys [project-id host]} (env/setting :posthog)]
           (if (and (seq project-id) (seq host))
             (do (infof "analytics.posthog: initialising PostHog client (host=%s)" host)
                 (new PostHog project-id
                              #js {:host                     host
                                   :enableExceptionAutocapture true}))
             (do (warnf "analytics.posthog: :posthog :project-id/:host not configured — tracking disabled")
                 nil)))
  :stop  (when @client
           (.shutdown @client)))

;; ---------------------------------------------------------------------------
;; Helpers

(defn- distinct-id
  "Returns a stable per-browser anonymous ID derived from the signed-session
   cookie, falling back to \"anonymous\" when no session is present."
  [req]
  (or (get-in req [:cookies "macchiato-session.sig" :value])
      "anonymous"))

;; ---------------------------------------------------------------------------
;; Public API

(defn capture!
  "Fire-and-forget event capture. No-op when PostHog is unconfigured.
   `props` is a plain Clojure map of event properties."
  ([req event-name]
   (capture! req event-name {}))
  ([req event-name props]
   (when-let [ph @client]
     (.capture ph #js {:distinctId (distinct-id req)
                       :event      event-name
                       :properties (clj->js props)}))))

(defn capture-exception!
  "Capture a JS Error for PostHog error tracking. No-op when unconfigured."
  ([err]
   (capture-exception! err nil))
  ([err req]
   (when-let [ph @client]
     (.captureException ph err (when req (distinct-id req))))))
