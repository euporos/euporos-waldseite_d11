(ns preise-state-test
  "The price admin's re-frame state layer (roadmap step 12): `preise.db` /
   `preise.events` / `preise.subs` / `preise.fx` and the shared
   `preise.calculations`, run in Node without DOM.

   Parity against the frozen legacy oracle (`legacy.preise.*`, fed UTC
   dates as legacy `timify-daten` made them): next-saison, clone/shift by a
   year, overlap detection, weighted average on the fixture. Event tests
   cover every action, in particular id-based editing of two identical
   seasons (legacy matched seasons by value and edited both)."
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [cljs-time.coerce :as t.coerce]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [re-frame.core :as rf]
            [re-frame.db :as rfdb]
            [reagent.ratom :as ratom]
            [kalender.core :as kal]
            [legacy.preise.calculations :as lcalc]
            [legacy.preise.components :as lcomps]
            [legacy.preise.util :as lu]
            [preise.calculations :as calc]
            [preise.db :as db]
            [preise.events :as ev]
            [preise.fx :as fx]
            [preise.model :as pm]
            [preise.subs :as subs]
            [test-support.fixtures :as fxt]))

(def num-tests 500)

(defn- check [p]
  (let [r (tc/quick-check num-tests p :seed (.now js/Date))]
    (is (true? (:pass? r))
        (pr-str (select-keys r [:seed :num-tests :fail :shrunk])))
    r))

(defn- utc [iso] (t.coerce/from-string iso))

(defn- legacy-saison
  "On-disk season → what legacy `timify-daten` made of it."
  [s]
  (-> s (update :beginn utc) (update :ende utc)))

(defn- same-number? [a b]
  (or (= a b) (and (js/isNaN a) (js/isNaN b))))

;; ---------------------------------------------------------------------------
;; Generators

(def start-day "2020-01-01")

(def gen-iso
  "ISO days 2020–2040, with extra weight on the end of February."
  (gen/frequency
   [[8 (gen/fmap #(kal/plus-days start-day %) (gen/choose 0 7300))]
    [2 (gen/fmap (fn [[y d]] (str y "-02-" d))
                 (gen/tuple (gen/choose 2020 2040) (gen/elements ["27" "28" "29"])))]]))

(def gen-iso-dicht
  "ISO days within one month, so seasons touch and overlap often."
  (gen/fmap #(kal/plus-days "2030-03-01" %) (gen/choose 0 30)))

(def gen-saison-dicht
  (gen/let [b gen-iso-dicht
            n (gen/choose 1 12)]
    {:beginn b :ende (kal/plus-days b n) :felder {}}))

;; ---------------------------------------------------------------------------
;; Parity: season rules

(deftest next-saison-parity
  (let [ss (:saisonen (fxt/preise))]
    (testing "fixture: day after the latest ende, two weeks"
      (let [l (lcomps/next-saison (map legacy-saison ss))
            n (db/next-saison ss "2026-07-01")]
        (is (= (kal/date->iso (:beginn l)) (:beginn n)))
        (is (= (kal/date->iso (:ende l)) (:ende n)))
        (is (= {:beginn "2028-01-07" :ende "2028-01-21" :felder {}} n)))))
  (testing "random season sets"
    (check (prop/for-all [ss (gen/vector (gen/let [b gen-iso n (gen/choose 1 60)]
                                       {:beginn b :ende (kal/plus-days b n)})
                                     1 12)]
             (let [l (lcomps/next-saison (map legacy-saison ss))
                   n (db/next-saison ss "2026-07-01")]
               (and (= (kal/date->iso (:beginn l)) (:beginn n))
                    (= (kal/date->iso (:ende l)) (:ende n)))))))
  (testing "no seasons → starts today"
    (is (= {:beginn "2026-07-01" :ende "2026-07-15" :felder {}}
           (db/next-saison [] "2026-07-01")))))

(deftest plus-jahre-parity
  (check (prop/for-all [iso gen-iso]
           (let [iso (if (kal/valid-iso? iso) iso "2024-02-29")]
             (= (kal/date->iso (:beginn (lcomps/shift-saison-by-years 1 {:beginn (utc iso) :ende (utc iso)})))
                (db/plus-jahre iso 1)))))
  (is (= "2025-02-28" (db/plus-jahre "2024-02-29" 1)))
  (is (= "2028-02-28" (db/plus-jahre "2027-02-28" 1)) "no day shift otherwise")
  (is (= "2028-03-01" (db/plus-jahre "2027-03-01" 1))))

(deftest clone-parity-on-fixture
  (let [raw     (:saisonen (fxt/preise))
        sel     (into #{} (comp (filter :selected) (map :id)) raw)
        legacy  (lcomps/shift-saisonen-by-one-year (map legacy-saison raw))
        neu     (db/clone-naechstes-jahr (mapv #(dissoc % :selected) raw) sel)
        klone   (drop (count raw) neu)]
    (is (= #{39 40} sel))
    (is (= (count legacy) (count klone)))
    (is (= (map (comp kal/date->iso :beginn) legacy) (map :beginn klone)))
    (is (= (map (comp kal/date->iso :ende) legacy) (map :ende klone)))
    (is (= (map :felder legacy) (map :felder klone)) "prices copied")
    (is (= [62 63] (map :id klone)) "fresh ids max+1, one after the other")
    (is (= (mapv #(dissoc % :selected) raw) (vec (take (count raw) neu))) "originals untouched")))

(deftest overlap-parity
  (testing "random valid season pairs (touching and intersecting often)"
    (check (prop/for-all [a gen-saison-dicht b gen-saison-dicht]
             (= (lcomps/seasons-overlap? (legacy-saison a) (legacy-saison b))
                (db/seasons-overlap? a b "2026-07-01")))))
  (testing "pinned cases"
    (let [s (fn [b e] {:beginn b :ende e})]
      (is (db/seasons-overlap? (s "2030-01-01" "2030-01-10") (s "2030-01-10" "2030-01-20") "2026-07-01"))
      (is (not (db/seasons-overlap? (s "2030-01-01" "2030-01-10") (s "2030-01-11" "2030-01-20") "2026-07-01")))))
  (testing "a season with bad dates is the empty interval at today"
    (let [heute (kal/today-iso)
          bad   {:beginn "2030-01-05" :ende "2030-01-05"}
          um    {:beginn "2000-01-01" :ende "2100-01-01"}
          alt   {:beginn "1990-01-01" :ende "1990-02-01"}]
      (doseq [[x y] [[bad um] [um bad] [bad alt] [alt bad] [bad bad]]]
        (is (= (lcomps/seasons-overlap? (legacy-saison x) (legacy-saison y))
               (db/seasons-overlap? x y heute))
            (pr-str x y)))))
  (testing "fixture: legacy saison-overlaps? == saison-fehler"
    (let [ss (mapv #(dissoc % :selected) (:saisonen (fxt/preise)))
          ss (conj ss (assoc (nth ss 3) :id 99 :beginn "2025-06-10"))
          ls (mapv legacy-saison ss)]
      ;; legacy excludes the checked season by value: pass the identical objects
      (is (= (set (map :id (filter #(lcomps/saison-overlaps? % ls) ls)))
             (set (keep (fn [[id e]] (when (:overlaps? e) id)) (db/saison-fehler ss "2026-07-01")))))
      (is (= #{42 99} (set (keys (db/saison-fehler ss "2026-07-01"))))))))

(deftest saison-fehler-data
  (let [ss [{:id 1 :beginn "2030-01-01" :ende "2030-01-10"}
            {:id 2 :beginn "2030-01-10" :ende "2030-01-20"}
            {:id 3 :beginn "2030-03-01" :ende "2030-02-01"}
            {:id 4 :beginn "2031-01-01" :ende "2031-01-10"}]]
    (is (= {1 {:dates-bad? false :overlaps? true}
            2 {:dates-bad? false :overlaps? true}
            3 {:dates-bad? true  :overlaps? false}}
           (db/saison-fehler ss "2026-07-01")))
    (testing "two identical seasons (different ids) flag each other"
      (let [a {:id 1 :beginn "2030-01-01" :ende "2030-01-10"}]
        (is (= #{1 2} (set (keys (db/saison-fehler [a (assoc a :id 2)] "2026-07-01")))))))))

;; ---------------------------------------------------------------------------
;; Parity: weighted average

(deftest weighted-average-parity
  (let [raw (:saisonen (fxt/preise))
        ls  (:saisonen (lu/timify-daten (fxt/preise)))]
    (doseq [wid (range 1 10) k [:preis_tag :preis_woche]]
      (let [l (lcalc/gewichte-preise ls wid k)
            n (calc/gewichte-preise raw wid k)]
        (is (same-number? l n) (pr-str wid k l n))))
    (is (js/isNaN (calc/gewichte-preise raw 9 :preis_tag)) "unknown wohnung → NaN")
    (is (js/isNaN (calc/gewichte-preise [] 1 :preis_tag))))
  (testing "random string prices incl. \"\" and missing"
    (check (prop/for-all [ss (gen/vector
                              (gen/let [b gen-iso-dicht
                                        n (gen/choose 1 40)
                                        p (gen/elements [nil "" "0" "50" "78.5" "115.00" "-3" 42])]
                                {:beginn b :ende (kal/plus-days b n)
                                 :felder (if (nil? p) {} {1 {:preis_tag p}})})
                              0 12)]
             (same-number? (lcalc/gewichte-preise (map legacy-saison ss) 1 :preis_tag)
                           (calc/gewichte-preise ss 1 :preis_tag))))))

;; ---------------------------------------------------------------------------
;; Event harness

(def heute "2026-07-01")
(def jetzt 1234567)
(def token "csrf-test-token")

(defonce requests (atom []))
(defonce redirects (atom []))
(defonce warnings (atom []))

(defn- install-stubs! []
  (rf/clear-fx :http-xhrio)
  (rf/reg-fx :http-xhrio (fn [req] (swap! requests conj req)))
  (rf/clear-fx ::fx/redirect)
  (rf/reg-fx ::fx/redirect (fn [uri] (swap! redirects conj uri)))
  (rf/clear-fx ::fx/warn)
  (rf/reg-fx ::fx/warn (fn [args] (swap! warnings conj args)))
  (rf/clear-cofx ::fx/heute)
  (rf/reg-cofx ::fx/heute (fn [cofx _] (assoc cofx :heute heute)))
  (rf/clear-cofx ::fx/jetzt)
  (rf/reg-cofx ::fx/jetzt (fn [cofx _] (assoc cofx :jetzt jetzt)))
  (rf/clear-cofx ::fx/csrf-token)
  (rf/reg-cofx ::fx/csrf-token (fn [cofx _] (assoc cofx :csrf-token token))))

(use-fixtures :each
  {:before (fn []
             (install-stubs!)
             (reset! requests [])
             (reset! redirects [])
             (reset! warnings [])
             (rf/clear-subscription-cache!)
             (reset! rfdb/app-db {}))})

(defn- sub [q]
  (let [!v (atom nil)
        r  (ratom/run! (reset! !v @(rf/subscribe q)))]
    (ratom/dispose! r)
    @!v))

(defn- app-db [] @rfdb/app-db)

(def refdata
  ;; refdata is ordered by name, not id
  {:wohnungen (vec (for [id [3 1 2 8 4 5 6 7]]
                     {:id id :name (str "Wohnung " id) :hauptbild nil}))})

(defn- boot!
  ([] (boot! (fxt/preise)))
  ([daten]
   (rf/dispatch-sync [::ev/load])
   (rf/dispatch-sync [::ev/refdata-loaded refdata])
   (rf/dispatch-sync [::ev/daten-loaded daten])))

(defn- saison [id] (pm/saison-by-id (get-in (app-db) [:daten :saisonen]) id))

(defn- saved-state [] (sub [::subs/save-state]))

;; ---------------------------------------------------------------------------
;; Load

(deftest load-requests
  (rf/dispatch-sync [::ev/load])
  (is (= :loading (sub [::subs/status])))
  (testing "refdata and data in parallel, cache-busted, as EDN"
    (is (= 1 (count @requests)))
    (let [[r d] (first @requests)]
      (is (= [:get :get] [(:method r) (:method d)]))
      (is (= "/api/preise/refdata?t=1234567" (:uri r)))
      (is (= "/api/preise/data?t=1234567" (:uri d)))
      (is (= [::ev/refdata-loaded] (:on-success r)))
      (is (= [::ev/daten-loaded] (:on-success d)))
      (is (= [::ev/load-failed] (:on-failure r) (:on-failure d))))))

(deftest load-success-any-order
  (doseq [order [[:r :d] [:d :r]]]
    (reset! rfdb/app-db {})
    (rf/dispatch-sync [::ev/load])
    (doseq [x order]
      (is (= :loading (sub [::subs/status])))
      (rf/dispatch-sync (case x
                          :r [::ev/refdata-loaded refdata]
                          :d [::ev/daten-loaded (fxt/preise)])))
    (is (= :ready (sub [::subs/status])))
    (testing "wohnungen sorted by id, legacy shape"
      (is (= [1 2 3 4 5 6 7 8] (keys (sub [::subs/wohnungen]))))
      (is (= {:name "Wohnung 3" :hauptbild nil} (get (sub [::subs/wohnungen]) 3))))
    (testing "data kept as typed strings, :selected lifted into the UI set"
      (is (= #{39 40} (sub [::subs/selected])))
      (is (not-any? #(contains? % :selected) (sub [::subs/saisonen])))
      (is (= "78" (get-in (saison 39) [:felder 1 :preis_tag])))
      (is (= (:allgdaten (fxt/preise)) (sub [::subs/allgdaten]))))
    (is (= :ungespeichert (saved-state)))
    (is (= {} (get-in (app-db) [:ui :geladen])))))

(deftest load-failures
  (testing "401 → not logged in, redirect to the admin page (→ Directus login)"
    (rf/dispatch-sync [::ev/load])
    (rf/dispatch-sync [::ev/load-failed {:status 401 :response {:error :unauthenticated}}])
    (is (= :not-logged-in (sub [::subs/status])))
    (is (= ["/admin/preise"] @redirects))
    (testing "the second failure of the pair is ignored"
      (rf/dispatch-sync [::ev/load-failed {:status 401}])
      (is (= 1 (count @redirects)))))
  (testing "other failure → load-failed, late success ignored"
    (rf/dispatch-sync [::ev/load])
    (rf/dispatch-sync [::ev/load-failed {:status 503 :response {:error :preise-edn-missing}}])
    (is (= :load-failed (sub [::subs/status])))
    (rf/dispatch-sync [::ev/refdata-loaded refdata])
    (rf/dispatch-sync [::ev/daten-loaded (fxt/preise)])
    (is (= :load-failed (sub [::subs/status])))
    (is (= 1 (count @warnings))))
  (testing "garbage body → load-failed"
    (rf/dispatch-sync [::ev/load])
    (rf/dispatch-sync [::ev/refdata-loaded refdata])
    (rf/dispatch-sync [::ev/daten-loaded "nope"])
    (is (= :load-failed (sub [::subs/status])))))

;; ---------------------------------------------------------------------------
;; Edits

(deftest set-allg-and-basis
  (boot!)
  (rf/dispatch-sync [::ev/set-allg :gaestebeitrag "3.0"])
  (is (= "3.0" (:gaestebeitrag (sub [::subs/allgdaten]))))
  (rf/dispatch-sync [::ev/set-basis 2 :grundpreis ""])
  (is (= "" (get-in (sub [::subs/basisdaten]) [2 :grundpreis])) "strings as typed, \"\" kept")
  (is (= 2 (:rev (app-db))))
  (testing "unknown keys are ignored"
    (let [before (app-db)]
      (rf/dispatch-sync [::ev/set-allg :quatsch "1"])
      (rf/dispatch-sync [::ev/set-basis 2 :quatsch "1"])
      (rf/dispatch-sync [::ev/set-saison-feld 39 1 :quatsch "1"])
      (is (= before (app-db))))))

(deftest id-based-editing-of-identical-seasons
  (let [a  {:id 1 :beginn "2030-01-01" :ende "2030-01-10" :felder {1 {:preis_tag "50"}}}
        d  {:saisonen [a (assoc a :id 2)] :allgdaten {} :basisdaten {}}]
    (boot! d)
    (rf/dispatch-sync [::ev/set-saison-feld 2 1 :preis_tag "60"])
    (is (= "50" (get-in (saison 1) [:felder 1 :preis_tag])) "the twin is untouched")
    (is (= "60" (get-in (saison 2) [:felder 1 :preis_tag])))
    (rf/dispatch-sync [::ev/set-saison-feld 1 3 :mindestaufenthalt "4"])
    (is (= {:mindestaufenthalt "4"} (get-in (saison 1) [:felder 3])) "new wohnung entry")
    (is (nil? (get-in (saison 2) [:felder 3])))
    (rf/dispatch-sync [::ev/set-saison-datum 1 :ende "2030-01-15"])
    (is (= "2030-01-15" (:ende (saison 1))))
    (is (= "2030-01-10" (:ende (saison 2))))
    (rf/dispatch-sync [::ev/toggle-select 2])
    (is (= #{2} (sub [::subs/selected])))
    (rf/dispatch-sync [::ev/arm-trash 1 true])
    (rf/dispatch-sync [::ev/trash-saison 1])
    (is (= [2] (map :id (sub [::subs/saisonen]))) "only the armed one is gone")))

(deftest set-saison-datum-rejects-bad-order
  (boot!)
  (rf/dispatch-sync [::ev/set-saison-datum 39 :ende "2025-02-01"])
  (is (= "2025-02-01" (:ende (saison 39))))
  (let [rev (:rev (app-db))]
    (doseq [[feld iso] [[:beginn "2025-02-01"] [:beginn "2025-03-01"] [:ende "2025-01-27"] [:ende ""]]]
      (rf/dispatch-sync [::ev/set-saison-datum 39 feld iso])
      (is (= {:id 39 :feld feld :error :beginn-nicht-vor-ende} (sub [::subs/datum-fehler])))
      (is (= ["2025-01-27" "2025-02-01"] ((juxt :beginn :ende) (saison 39))) "old value kept"))
    (is (= rev (:rev (app-db))) "a rejected change is no edit"))
  (rf/dispatch-sync [::ev/set-saison-datum 39 :beginn "2025-01-20"])
  (is (nil? (sub [::subs/datum-fehler])) "a valid change clears the message")
  (is (= "2025-01-20" (:beginn (saison 39)))))

(deftest add-saison
  (boot!)
  (rf/dispatch-sync [::ev/add-saison])
  (let [s (last (sub [::subs/saisonen]))]
    (is (= {:id 62 :beginn "2028-01-07" :ende "2028-01-21" :felder {}} s))
    (is (pm/valid? (db/auf-disk (app-db))) "a new season satisfies the save schema"))
  (testing "no seasons → today, id 1"
    (reset! rfdb/app-db {})
    (boot! {:saisonen [] :allgdaten {} :basisdaten {}})
    (rf/dispatch-sync [::ev/add-saison])
    (is (= [{:id 1 :beginn heute :ende "2026-07-15" :felder {}}] (sub [::subs/saisonen])))))

(deftest selection-clone-delete
  (boot!)
  (rf/dispatch-sync [::ev/toggle-select 39])
  (is (= #{40} (sub [::subs/selected])))
  (rf/dispatch-sync [::ev/toggle-select 41])
  (is (= #{40 41} (sub [::subs/selected])))
  (is (= 0 (:rev (app-db))) "selection is no edit")
  (rf/dispatch-sync [::ev/clone-selected-next-year])
  (let [ss (sub [::subs/saisonen])]
    (is (= 25 (count ss)))
    (is (= [[62 "2026-03-10" "2026-04-07"] [63 "2026-04-08" "2026-05-01"]]
           (map (juxt :id :beginn :ende) (take-last 2 ss))))
    (is (= (:felder (saison 40)) (:felder (saison 62)))))
  (is (= #{} (sub [::subs/selected])) "clone unselects all")
  (is (= :ungespeichert (saved-state)))
  (testing "clones overlap the 2026 seasons → flagged"
    (is (:overlaps? (get (sub [::subs/saison-fehler]) 62))))
  (testing "clone/delete with nothing selected do nothing"
    (let [before (app-db)]
      (rf/dispatch-sync [::ev/clone-selected-next-year])
      (rf/dispatch-sync [::ev/delete-selected])
      (is (= before (app-db)))))
  (rf/dispatch-sync [::ev/toggle-select 62])
  (rf/dispatch-sync [::ev/toggle-select 63])
  (rf/dispatch-sync [::ev/arm-trash 62 true])
  (rf/dispatch-sync [::ev/delete-selected])
  (is (= 23 (count (sub [::subs/saisonen]))))
  (is (nil? (saison 62)))
  (is (= #{} (sub [::subs/selected]) (sub [::subs/trash-armed])))
  (is (empty? (sub [::subs/saison-fehler])))
  (rf/dispatch-sync [::ev/toggle-select 41])
  (rf/dispatch-sync [::ev/unselect-all])
  (is (= #{} (sub [::subs/selected]))))

(deftest trash
  (boot!)
  (testing "an unarmed trash does nothing"
    (rf/dispatch-sync [::ev/trash-saison 41])
    (is (some? (saison 41))))
  (rf/dispatch-sync [::ev/arm-trash 41 true])
  (is (= #{41} (sub [::subs/trash-armed])))
  (rf/dispatch-sync [::ev/arm-trash 41 false])
  (rf/dispatch-sync [::ev/trash-saison 41])
  (is (some? (saison 41)) "disarmed again")
  (rf/dispatch-sync [::ev/toggle-select 41])
  (rf/dispatch-sync [::ev/arm-trash 41 true])
  (rf/dispatch-sync [::ev/trash-saison 41])
  (is (nil? (saison 41)))
  (is (= #{39 40} (sub [::subs/selected])))
  (is (= #{} (sub [::subs/trash-armed])))
  (is (= :ungespeichert (saved-state))))

;; ---------------------------------------------------------------------------
;; Subs

(deftest derived-subs
  (boot!)
  (testing "seasons sorted by beginn"
    (rf/dispatch-sync [::ev/set-saison-datum 61 :beginn "2024-12-01"])
    (rf/dispatch-sync [::ev/set-saison-datum 61 :ende "2024-12-10"])
    (is (= 61 (:id (first (sub [::subs/sortierte-saisonen])))))
    (is (= 39 (:id (second (sub [::subs/sortierte-saisonen]))))))
  (testing "weighted averages per wohnung = shared calc on the raw seasons"
    (let [avg (sub [::subs/durchschnittspreise])
          ss  (sub [::subs/saisonen])]
      (is (= [1 2 3 4 5 6 7 8] (keys avg)))
      (doseq [[id m] avg]
        (is (= {:preis_tag   (calc/gewichte-preise ss id :preis_tag)
                :preis_woche (calc/gewichte-preise ss id :preis_woche)}
               m)))))
  (testing "edits flow into the averages"
    (let [before (get-in (sub [::subs/durchschnittspreise]) [1 :preis_tag])]
      (rf/dispatch-sync [::ev/set-saison-feld 39 1 :preis_tag "500"])
      (is (< before (get-in (sub [::subs/durchschnittspreise]) [1 :preis_tag])))))
  (is (= {} (sub [::subs/saison-fehler]))))

;; ---------------------------------------------------------------------------
;; Save

(deftest save-roundtrip-and-states
  (boot!)
  (rf/dispatch-sync [::ev/save])
  (is (= :ongoing (saved-state)))
  (let [req (last @requests)]
    (is (= :post (:method req)))
    (is (= "/api/preise/save" (:uri req)))
    (is (= {"X-CSRF-Token" token} (:headers req)))
    (testing "unchanged data: fixture minus the :selected false flags"
      (is (= (update (fxt/preise) :saisonen
                     (fn [ss] (mapv #(if (:selected %) % (dissoc % :selected)) ss)))
             (get-in req [:params :preisstruktur])))
      (is (pm/valid? (get-in req [:params :preisstruktur]))))
    (testing "a second save while ongoing is ignored"
      (rf/dispatch-sync [::ev/save])
      (is (= 2 (count @requests))))
    (rf/dispatch-sync (conj (:on-success req) {}))
    (is (= :erfolgreich (saved-state))))
  (testing "selection written back as :selected true"
    (rf/dispatch-sync [::ev/unselect-all])
    (rf/dispatch-sync [::ev/toggle-select 45])
    (rf/dispatch-sync [::ev/save])
    (let [ss (get-in (last @requests) [:params :preisstruktur :saisonen])]
      (is (= [45] (map :id (filter :selected ss))))
      (is (every? #(or (true? (:selected %)) (not (contains? % :selected))) ss)))
    (rf/dispatch-sync (conj (:on-success (last @requests)) {})))
  (testing "failure states"
    (doseq [[status state] [[401 :falsches-passwort] [400 :fehlgeschlagen]
                            [500 :req-failed] [0 :req-failed]]]
      (rf/dispatch-sync [::ev/save])
      (rf/dispatch-sync (conj (:on-failure (last @requests)) {:status status}))
      (is (= state (saved-state)) (str status))))
  (testing "any edit sets :ungespeichert"
    (rf/dispatch-sync [::ev/set-allg :gaestebeitrag "1"])
    (is (= :ungespeichert (saved-state))))
  (testing "an edit while the save is in flight keeps it unsaved"
    (rf/dispatch-sync [::ev/save])
    (let [req (last @requests)]
      (rf/dispatch-sync [::ev/set-allg :gaestebeitrag "2"])
      (is (= :ungespeichert (saved-state)))
      (rf/dispatch-sync (conj (:on-success req) {}))
      (is (= :ungespeichert (saved-state))))))

(deftest save-needs-loaded-data
  (rf/dispatch-sync [::ev/load])
  (rf/dispatch-sync [::ev/save])
  (is (= 1 (count @requests)) "only the load request"))
