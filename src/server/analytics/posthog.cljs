(ns analytics.posthog
  "Server-side PostHog analytics client.
   Reads config from :posthog {:project-id … :host …} in settings.edn.
   All public functions are safe to call when PostHog is unconfigured — they
   become no-ops.

   Identity: visitors are identified by a salted-hash tracking-id stored on
   the macchiato session (`[:session :tracking :id]`), populated by
   setup.tracking/wrap-tracking-id. The browser snippet bootstraps
   posthog-js with the same value and runs with disable_cookie: true, so
   client and server events share an identity without an analytics cookie."
  (:require ["posthog-node" :refer [PostHog]]
            [config.env :as env]
            [mount.core :refer [defstate]]
            [psite-rate-limit.core :as rate-limit]
            [taoensso.timbre :refer [infof warnf]]))

(declare client)

(defstate client
  :start (let [{:keys [project-id host]} (env/setting :posthog)]
           (if (and (seq project-id) (seq host))
             (do (infof "analytics.posthog: initialising PostHog client (host=%s)" host)
                 (let [c (new PostHog project-id
                                      #js {:host                       host
                                           :enableExceptionAutocapture true})]
                   (.on js/process "beforeExit"
                        (fn [] (.shutdown c)))
                   c))
             (do (warnf "analytics.posthog: :posthog :project-id/:host not configured — tracking disabled")
                 nil)))
  :stop  (when @client
           (.shutdown @client)))

;; ---------------------------------------------------------------------------
;; Helpers

(defn- distinct-id [req]
  (or (get-in req [:session :tracking :id])
      "anonymous"))

(defn- current-url [req]
  (let [scheme (some-> (:scheme req) name)
        host   (get-in req [:headers "host"])
        uri    (:uri req)
        qs     (:query-string req)]
    (when (and scheme host uri)
      (str scheme "://" host uri (when (seq qs) (str "?" qs))))))

(defn- req->props
  "PostHog auto-properties the Node SDK does not fill in for us."
  [req]
  (let [trust-proxy? (boolean (env/setting :rate-limit-trust-proxy?))
        ip           (rate-limit/identify req {:trust-proxy? trust-proxy?})
        ua           (get-in req [:headers "user-agent"])
        ref          (get-in req [:headers "referer"])
        host         (get-in req [:headers "host"])
        url          (current-url req)]
    (cond-> {}
      ip   (assoc "$ip" ip)
      ua   (assoc "$useragent" ua)
      ref  (assoc "$referrer" ref)
      host (assoc "$host" host)
      url  (assoc "$current_url" url)
      (:uri req) (assoc "$pathname" (:uri req)))))

;; ---------------------------------------------------------------------------
;; Public API

(defn capture!
  "Fire-and-forget event capture. No-op when PostHog is unconfigured.
   `props` is a plain Clojure map of event properties; request-derived
   PostHog auto-properties ($ip, $current_url, …) are merged in."
  ([req event-name]
   (capture! req event-name {}))
  ([req event-name props]
   (when-let [ph @client]
     (.capture ph #js {:distinctId (distinct-id req)
                       :event      event-name
                       :properties (clj->js (merge (req->props req) props))}))))

(defn identify!
  "Associate the current visitor with a known user id and (optional) traits.
   Use after Directus login so admin actions land on a named person rather
   than the anonymous tracking-id."
  ([req user-id] (identify! req user-id {}))
  ([req user-id traits]
   (when-let [ph @client]
     (when (seq user-id)
       (.identify ph #js {:distinctId user-id
                          :properties (clj->js traits)})
       (let [anon (distinct-id req)]
         (when (and anon (not= anon user-id) (not= anon "anonymous"))
           (.alias ph #js {:distinctId user-id :alias anon})))))))

(defn capture-exception!
  "Capture a JS Error for PostHog error tracking. No-op when unconfigured."
  ([err]
   (capture-exception! err nil))
  ([err req]
   (when-let [ph @client]
     (if req
       (.captureException ph err (distinct-id req) (clj->js (req->props req)))
       (.captureException ph err)))))
