(ns api.directus-proxy
  "Reverse-proxies /directus/* to a local Directus upstream so that
   stored URLs like /directus/assets/<uuid> resolve in dev the way they
   do in prod (where the edge web server fronts Directus on the same
   path). Enabled by setting :directus-proxy-upstream; intended for dev.

   Request bodies are not forwarded — the surrounding macchiato stack
   (wrap-restful-format, wrap-params) consumes them before the handler
   runs. GET is the only method this proxy supports in practice, which
   covers WYSIWYG asset rendering. Editors hit the Directus admin
   directly on its own port."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [clojure.string :as str]
            [config.env :as env]
            [taoensso.timbre :as log]))

(def ^:private hop-by-hop
  #{"connection" "keep-alive" "proxy-authenticate" "proxy-authorization"
    "te" "trailers" "transfer-encoding" "upgrade" "content-length"})

(defn- forward-req-headers [headers]
  (clj->js
   (into {} (remove (fn [[k _]] (hop-by-hop (str/lower-case (name k)))))
         headers)))

(defn- response-headers [resp-headers]
  (let [out (transient {})]
    (.forEach resp-headers
              (fn [v k]
                (when-not (hop-by-hop (str/lower-case k))
                  (assoc! out k v))))
    (let [m (persistent! out)
          set-cookies (when (.-getSetCookie resp-headers)
                        (vec (.getSetCookie resp-headers)))]
      (cond-> m
        (seq set-cookies) (assoc "set-cookie" set-cookies)))))

(defhandler handler [req]
  (let [upstream (str/replace (env/setting :directus-proxy-upstream) #"/+$" "")
        path     (or (-> req :path-params :path) "")
        qs       (:query-string req)
        url      (str upstream "/" path (when (seq qs) (str "?" qs)))
        method   (str/upper-case (name (:request-method req)))
        opts     #js {:method   method
                      :headers  (forward-req-headers (:headers req))
                      :redirect "manual"}]
    (-> (js/fetch url opts)
        (.then (fn [resp]
                 (.then (.arrayBuffer resp)
                        (fn [buf]
                          {:status  (.-status resp)
                           :headers (response-headers (.-headers resp))
                           :body    (js/Buffer.from buf)}))))
        (.catch (fn [e]
                  (log/warnf "directus proxy %s %s failed: %s"
                             method url (.-message e))
                  {:status  502
                   :headers {"content-type" "text/plain"}
                   :body    "directus upstream unreachable"})))))
