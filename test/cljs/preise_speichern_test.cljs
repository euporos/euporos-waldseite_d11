(ns preise-speichern-test
  "Price admin save check (`preise.speichern`) and the file round-trip of
   `preise.edn-io` on a temp path."
  (:require ["fs" :as fs]
            ["os" :as os]
            ["path" :as path]
            [cljs.reader :as reader]
            [cljs.test :refer [deftest is testing]]
            [preise.edn-io :as io]
            [preise.speichern :as sp]
            [test-support.fixtures :as fx]))

(defn- run
  "respond with a recording stub; returns [response written-strings]."
  [m]
  (let [written (atom [])
        resp    (sp/respond {:write!      (fn [s] (swap! written conj s) :ok)
                             :after-save! (fn [] {:status 200 :body "saved"})}
                            {:edn-params {:preisstruktur m}})]
    [resp @written]))

(defn- body [resp] (reader/read-string (:body resp)))

(deftest akzeptiert-fixture
  (let [[resp written] (run (fx/preise))]
    (is (= 200 (:status resp)))
    (is (= [(pr-str (fx/preise))] written))))

(deftest lehnt-kaputte-strukturen-ab
  (testing "not a map / missing"
    (doseq [m [nil "x" []]]
      (let [[resp written] (run m)]
        (is (= 400 (:status resp)))
        (is (= :invalid (:error (body resp))))
        (is (empty? written)))))
  (testing "schema violation"
    (let [[resp written] (run (assoc (fx/preise) :saisonen "nope"))]
      (is (= 400 (:status resp)))
      (is (= :invalid (:error (body resp))))
      (is (empty? written))))
  (testing "no body at all"
    (is (= 400 (:status (sp/respond {:write! (fn [_] :ok) :after-save! (fn [] {})} {}))))))

(deftest lehnt-doppelte-ids-ab
  (let [p  (fx/preise)
        p' (update p :saisonen #(conj (vec %) (first %)))
        [resp written] (run p')]
    (is (= 400 (:status resp)))
    (is (= :doppelte-saison-id (:error (body resp))))
    (is (= [(:id (first (:saisonen p)))] (:ids (body resp))))
    (is (empty? written))))

(deftest lehnt-umgekehrte-saison-ab
  (let [p  (fx/preise)
        s0 (first (:saisonen p))
        p' (update p :saisonen assoc 0 (assoc s0 :beginn "2030-06-01" :ende "2030-05-01"))
        [resp written] (run p')]
    (is (= 400 (:status resp)))
    (is (= :saison-umgekehrt (:error (body resp))))
    (is (= [(:id s0)] (:ids (body resp))))
    (is (empty? written))))

(deftest ueberlappende-saisonen-bleiben-erlaubt
  (let [p  (fx/preise)
        s0 (first (:saisonen p))
        p' (update p :saisonen conj (assoc s0 :id 99999))]
    (is (= 200 (:status (first (run p')))))))

(deftest persistence-round-trip
  (let [dir (.mkdtempSync fs (path/join (os/tmpdir) "preise-"))
        f   (path/join dir "preise.edn")
        m   (fx/preise)]
    (is (nil? (io/read-string-from f)))
    (is (= :ok (io/write-edn-string! f (pr-str m))))
    (is (= m (reader/read-string (io/read-string-from f))))
    (is (= :parse-fail (io/write-edn-string! f "{:a")))
    (is (= m (reader/read-string (io/read-string-from f))) "bad input leaves file intact")))
