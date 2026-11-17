(ns preise.edn-io
  "File I/O of preise.edn, free of `config.env` so Node tests can use it
   with a temp path. `preise.persistence` binds the configured path."
  (:require ["fs" :as fs]
            [cljs.reader :as reader]))

(defn read-string-from
  "Raw contents of the file at `path`, or nil if missing."
  [path]
  (when (.existsSync fs path)
    (.toString (.readFileSync fs path))))

(defn write-edn-string!
  "Validates s parses as EDN, then writes atomically (tmp + rename) to
   `path`. Returns :ok on success, :parse-fail on bad EDN, :verify-fail if
   the read-back doesn't match what was written."
  [path s]
  (let [parsed (try (reader/read-string s)
                    (catch :default _ ::parse-error))]
    (if (= ::parse-error parsed)
      :parse-fail
      (let [tmp (str path ".tmp")]
        (.writeFileSync fs tmp s)
        (.renameSync fs tmp path)
        (if (= s (.toString (.readFileSync fs path)))
          :ok
          :verify-fail)))))
