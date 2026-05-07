(ns preise.persistence
  (:require ["fs" :as fs]
            [cljs.reader :as reader]
            [config.env :as env]
            [taoensso.timbre :refer [warnf]]))

(defn- path []
  (or (env/setting :preise-edn-path) "../preise.edn"))

(defn read-edn-string
  "Returns the raw contents of preise.edn as a string, or nil if missing."
  []
  (let [p (path)]
    (if (.existsSync fs p)
      (.toString (.readFileSync fs p))
      (do (warnf "preise.edn not found at %s" p) nil))))

(defn write-edn-string!
  "Validates s parses as EDN, then writes atomically (tmp + rename).
   Returns :ok on success, :parse-fail on bad EDN, :verify-fail if the
   read-back doesn't match what was written."
  [s]
  (let [parsed (try (reader/read-string s)
                    (catch :default _ ::parse-error))]
    (if (= ::parse-error parsed)
      :parse-fail
      (let [target (path)
            tmp    (str target ".tmp")]
        (.writeFileSync fs tmp s)
        (.renameSync fs tmp target)
        (if (= s (.toString (.readFileSync fs target)))
          :ok
          :verify-fail)))))
