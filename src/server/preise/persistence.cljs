(ns preise.persistence
  (:require [config.env :as env]
            [preise.edn-io :as io]
            [taoensso.timbre :refer [warnf]]))

(defn- path []
  (or (env/setting :preise-edn-path) "../preise.edn"))

(defn read-edn-string
  "Returns the raw contents of preise.edn as a string, or nil if missing."
  []
  (let [p (path)]
    (or (io/read-string-from p)
        (do (warnf "preise.edn not found at %s" p) nil))))

(defn write-edn-string!
  "See `preise.edn-io/write-edn-string!`, on the configured path."
  [s]
  (io/write-edn-string! (path) s))
