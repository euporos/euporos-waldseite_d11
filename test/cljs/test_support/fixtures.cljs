(ns test-support.fixtures
  "Reads the EDN fixtures under test/fixtures/ from disk at test run time.
   Paths resolve against the process cwd (the repo root under `nix run .#test`)
   and fall back to the location of the compiled test bundle
   (target/test/node-tests.js → ../../test/fixtures)."
  (:require [cljs.reader :as reader]
            ["fs" :as fs]
            ["path" :as path]))

(defn- fixture-path [file]
  (let [cands [(path/resolve "test/fixtures" file)
               (path/resolve js/__dirname "../../test/fixtures" file)]]
    (or (first (filter #(fs/existsSync %) cands))
        (throw (ex-info (str "fixture not found: " file) {:candidates cands})))))

(defn read-edn [file]
  (reader/read-string (fs/readFileSync (fixture-path file) "utf8")))

(def preise
  "test/fixtures/preise.edn exactly as on disk (string-valued numbers)."
  (memoize #(read-edn "preise.edn")))

(def belegung
  "test/fixtures/belegung.edn: {wohnung-id [{:dtstart \"yyyy-MM-dd\" :dtend …}]}."
  (memoize #(read-edn "belegung.edn")))
