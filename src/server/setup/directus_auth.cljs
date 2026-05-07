(ns setup.directus-auth
  (:require
   [config.env :as env]
   [db.setup :as db]
   [kitchen-async.promise :as p]
   [macchiato.middleware.anti-forgery :as af]
   [macchiato.util.response :as r]))

(defn- decode-jwt-payload
  "Decodes the payload of a JWT (base64) without signature verification.
   Session validity is checked against the database instead."
  [jwt]
  (try
    (let [payload (second (.split jwt "."))
          json (.toString (js/Buffer.from payload "base64") "utf8")]
      (js->clj (.parse js/JSON json) :keywordize-keys true))
    (catch js/Error _ nil)))

(defn lookup-directus-user
  "Looks up a Directus session token in the database.
   Returns a js/Promise resolving to a user map or nil."
  [session-token]
  (p/let [rows (db/query
                {:select [:du.id :du.first_name :du.last_name :du.email :du.role]
                 :from [[:directus_sessions :ds]]
                 :join [[:directus_users :du] [:= :ds.user :du.id]]
                 :where [:and
                         [:= :ds.token session-token]
                         [:> :ds.expires [:now]]
                         [:= :du.status "active"]]})]
    (first rows)))

(defn directus-login-redirect
  "Returns a redirect response to the Directus login page."
  [_req]
  (r/found (str (env/setting :directus-url) "admin/login")))

(defn require-directus-user
  "Page-route middleware: if wrap-directus-user did not attach :directus-user,
   redirect to the Directus login page."
  [handler]
  (fn [req res raise]
    (if (:directus-user req)
      (handler req res raise)
      (res (directus-login-redirect req)))))

(defn require-directus-user-401
  "API-route middleware: if wrap-directus-user did not attach :directus-user,
   respond 401 with an EDN body. Use after wrap-directus-user."
  [handler]
  (fn [req res raise]
    (if (:directus-user req)
      (handler req res raise)
      (res {:status  401
            :headers {"Content-Type" "application/edn"}
            :body    (pr-str {:error :unauthenticated})}))))

(defn wrap-directus-user
  "Ring middleware that detects Directus login via the session token cookie.
   Decodes the JWT to extract the session ID, validates it against the DB,
   and attaches :directus-user to the request when a valid session is found.

   Also snapshots the anti-forgery token onto :directus-af-token before the
   async DB hop. Downstream form-rendering handlers that sit behind this
   middleware should embed (:directus-af-token req), not (:af-token req) —
   see comment inside the impl for why."
  [handler]
  (fn [req res raise]
    ;; Snapshot the anti-forgery token onto a *non-colliding* key. We can't
    ;; use :af-token here because macchiato-async/wrap-async (used by
    ;; defhandler) re-snapshots :af-token from the dynamic var inside its
    ;; own promise hop, by which time the wrap-anti-forgery binding has
    ;; been popped — it would clobber our capture with nil. Handlers that
    ;; need a working token after the auth middleware should read
    ;; :directus-af-token instead of :af-token.
    (let [req (assoc req :directus-af-token af/*anti-forgery-token*)]
      (if-let [jwt (get-in req [:cookies "directus_session_token" :value])]
        (if-let [session-token (:session (decode-jwt-payload jwt))]
          (-> (lookup-directus-user session-token)
              (.then (fn [user]
                       (handler (cond-> req user (assoc :directus-user user)) res raise)))
              (.catch (fn [_err]
                        (handler req res raise))))
          (handler req res raise))
        (handler req res raise)))))
