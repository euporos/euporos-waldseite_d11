(ns preise.cache
  "In-memory snapshot of preise.edn for server-side consumers.

   The preise admin SPA still owns persistence — it reads/writes the EDN
   file via /api/preise/{data,save}. This module exists so the *public*
   side of the app (booking flow, apartment 'from €' indicators) can
   read prices synchronously without touching the filesystem on every
   request.

   Coherency: api.preise/save calls (refresh!) after the disk write
   succeeds, so subsequent reads here observe the operator's edit."
  (:require [cljs.reader :as reader]
            [mount.core :refer-macros [defstate]]
            [preise.persistence :as persist]
            [taoensso.timbre :refer [info warnf]]))

(declare prices)

(defn- load-from-disk []
  (when-let [s (persist/read-edn-string)]
    (try
      (reader/read-string s)
      (catch :default e
        (warnf "preise.cache: EDN parse failed — %s" (.-message e))
        nil))))

(defstate prices
  :start (let [a (atom (load-from-disk))]
           (info "preise.cache started —"
                 (if @a "loaded" "EMPTY (preise.edn missing or unreadable)"))
           a))

(defn read-prices
  "Returns the current cached price map, or nil if the cache is empty.
   Two derefs: the outer unwraps mount's DerefableState to the atom set
   up in :start, the inner reads the atom's current value."
  []
  (some-> prices deref deref))

(defn refresh!
  "Re-reads preise.edn and replaces the cache. Called after successful
   saves through /api/preise/save."
  []
  (some-> prices deref (reset! (load-from-disk))))
