(ns smoke-test
  (:require [cljs.test :refer [deftest is testing]]
            [specs.anfrage :as anfrage]))

(def sample
  {:anreise "2026-07-04"
   :abreise "2026-07-11"
   :gaestezahl 2
   :haustierzahl 0
   :wohnungsid 1
   :wohnungsname "Waldseite"
   :gast {:vorname "Erika" :nachname "Muster"
          :email "erika@example.com" :telefonnummer "012345"}
   :datenschutz-gelesen? true
   :mietbedingungen-gelesen? true})

(deftest anfrage-payload
  (testing "valid sample"
    (is (anfrage/valid? sample)))
  (testing "invalid sample"
    (is (not (anfrage/valid? (assoc sample :gaestezahl 0))))
    (is (not (anfrage/valid? (assoc sample :anreise "04.07.2026"))))))
