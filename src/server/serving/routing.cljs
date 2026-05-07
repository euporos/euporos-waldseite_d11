(ns serving.routing
  (:require
   [clojure.string :as str]
   [psite-routing.core :as routing]))

(defn slugify [s]
  (-> (or s "")
      str
      str/lower-case
      (str/replace #"ä" "ae")
      (str/replace #"ö" "oe")
      (str/replace #"ü" "ue")
      (str/replace #"ß" "ss")
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"(^-+|-+$)" "")))

(defn path-fixed
  [name request]
  (routing/reverse-match request name {}))

(defn path-haus [req id name]
  (routing/reverse-match req :haus {:hausid id :hausbez (slugify name)}))

(defn path-ausfluege [req hausid]
  (routing/reverse-match req :ausfluege {:hausid hausid}))

(defn path-einzelseite [req id titel]
  (routing/reverse-match req :einzelseite
                         {:einzelseitid  id
                          :einzelseitbez (slugify titel)}))

(defn switch-locale
  [new-locale {:keys [path-params query-params] :as req}]
  (let [route-name (get-in req [:reitit.core/match :data :name])]
    (routing/reverse-match req route-name
                           (assoc path-params :locale new-locale)
                           query-params)))

(defn switch-locale-and-prepend-domain
  [new-locale req]
  (routing/make-path-absolute req (switch-locale new-locale req)))
