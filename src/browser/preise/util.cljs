(ns preise.util
  "Small bundle of helpers used by the ported preise SPA. Extracted from the
   legacy plibs/utils.{helpers,tdstuff} and various.edn-processing modules so
   the preise app stays self-contained."
  (:require [cljs-time.coerce :as tm.coerce]
            [cljs-time.core :as tm]
            [cljs-time.format :as tm.format]))

;; -- collection helpers --

(defn mapply
  [func argtuples]
  (map #(apply func %) argtuples))

(defn swap-in-seq!
  [a old func]
  (reset! a (into [] (map #(if (= % old) (func %) %) @a))))

(defn update-multiple
  [m ks f]
  (reduce #(update %1 %2 f) m ks))

(defn mapify-single
  "[{:id 1 :name a}] → {1 {:name a}}"
  [k coll]
  (into {} (map #(vector (k %) (dissoc % k)) coll)))

;; -- date helpers --

(defn to-iso-day [d]
  (tm.format/unparse (tm.format/formatter "yyyy-MM-dd") d))

(defn count-days [a b]
  (/ (tm/in-minutes (tm/interval a b)) 1440))

;; -- preise EDN shape conversions --

(defn- timify-saisonen [saisonen]
  (into [] (map #(update-multiple % [:beginn :ende] tm.coerce/from-string) saisonen)))

(defn- stringify-saisonen [saisonen]
  (into [] (map #(update-multiple % [:beginn :ende] to-iso-day) saisonen)))

(defn timify-daten
  "EDN → in-memory: parses :saisonen dates into cljs-time objects."
  [m]
  (update m :saisonen timify-saisonen))

(defn serialize-daten
  "in-memory → EDN: stringifies :saisonen dates back to ISO."
  [m]
  (update m :saisonen stringify-saisonen))
