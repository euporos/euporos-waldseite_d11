(ns preise-model-test
  (:require [cljs.test :refer [deftest is testing]]
            [kalender.core :as kal]
            [legacy.buchung.ajaxing :as aj]
            [preise.model :as pm]
            [test-support.fixtures :as fx]))

(deftest schema-accepts-fixture
  (is (pm/valid? (fx/preise)) (pr-str (:errors (pm/explain (fx/preise))))))

(deftest schema-accepts-numbers-and-variants
  (let [p (fx/preise)]
    (is (pm/valid? (assoc-in p [:allgdaten :gaestebeitrag] 2.5)))
    (is (pm/valid? (assoc-in p [:basisdaten 1 :grundpreis] 75)))
    (is (pm/valid? (assoc-in p [:saisonen 0 :felder 1 :preis_tag] "")))
    (is (pm/valid? (assoc-in p [:saisonen 0 :felder 1 :preis_tag] "1e2")))
    (is (pm/valid? (update-in p [:saisonen 0] dissoc :selected)))
    (is (pm/valid? (assoc p :saisonen [])))))

(deftest schema-rejects-garbage
  (let [p (fx/preise)]
    (is (not (pm/valid? nil)))
    (is (not (pm/valid? {})))
    (is (not (pm/valid? "preise")))
    (is (not (pm/valid? (dissoc p :saisonen))))
    (is (not (pm/valid? (dissoc p :basisdaten))))
    (is (not (pm/valid? (assoc p :saisonen {}))))
    (is (not (pm/valid? (assoc-in p [:saisonen 0 :beginn] "27.01.2025"))))
    (is (not (pm/valid? (assoc-in p [:saisonen 0 :ende] "2025-02-30"))))
    (is (not (pm/valid? (assoc-in p [:saisonen 0 :ende] nil))))
    (is (not (pm/valid? (assoc-in p [:saisonen 0 :id] "39"))))
    (is (not (pm/valid? (assoc-in p [:saisonen 0 :selected] "ja"))))
    (is (not (pm/valid? (assoc-in p [:saisonen 0 :felder "1"] {}))))
    (is (not (pm/valid? (assoc-in p [:saisonen 0 :felder 1 :preis_tag] "abc"))))
    (is (not (pm/valid? (assoc-in p [:saisonen 0 :felder 1 :preis_tag] nil))))
    (is (not (pm/valid? (assoc-in p [:basisdaten 0] {}))))
    (is (not (pm/valid? (assoc-in p [:allgdaten :gaestebeitrag] [2.5]))))
    (is (some? (pm/explain (assoc-in p [:saisonen 0 :beginn] "x"))))))

(defn- comparable
  "Season dates → ISO, legacy :interval dropped."
  [p]
  (update p :saisonen
          (fn [ss] (mapv #(-> %
                              (dissoc :interval)
                              (update :beginn kal/date->iso)
                              (update :ende kal/date->iso))
                         ss))))

(deftest normalize-equals-legacy-rework-preise
  (let [raw    (fx/preise)
        new    (pm/normalize raw)
        legacy (aj/rework-preise raw)]
    (is (= (comparable legacy) (comparable new)))
    (testing "season dates are UTC-midnight instants equal to legacy's"
      (doseq [[n l] (map vector (:saisonen new) (:saisonen legacy))]
        (is (= (.getTime (:beginn l)) (.getTime (:beginn n))))
        (is (= (.getTime (:ende l)) (.getTime (:ende n))))
        (is (zero? (.getUTCHours (js/Date. (.getTime (:beginn n))))))))
    (testing "legacy number semantics"
      (let [s39 (pm/saison-by-id (:saisonen new) 39)]
        (is (= 78 (get-in s39 [:felder 1 :preis_tag])))
        (is (= 115 (get-in s39 [:felder 2 :preis_tag])))
        (is (= "" (get-in s39 [:felder 4 :preis_woche])) "empty stays empty"))
      (is (= 2.5 (get-in new [:allgdaten :gaestebeitrag])))
      (is (= 22 (get-in new [:allgdaten :reinigung-unter]))))
    (testing "non-numeric strings and numbers stay as they are, like legacy"
      (let [odd (-> raw
                    (assoc-in [:basisdaten 1 :grundpreis] "abc")
                    (assoc-in [:basisdaten 2 :grundpreis] 120)
                    (assoc-in [:basisdaten 3 :grundpreis] "12abc")
                    (assoc-in [:saisonen 0 :beginn] ""))]
        (is (= (comparable (aj/rework-preise odd)) (comparable (pm/normalize odd))))))))

(deftest next-id
  (is (= 62 (pm/next-id (:saisonen (fx/preise)))))
  (is (= 1 (pm/next-id [])))
  (is (= 8 (pm/next-id [{:id 7} {:id 3}])))
  (is (= 39 (:id (pm/saison-by-id (:saisonen (fx/preise)) 39)))))
