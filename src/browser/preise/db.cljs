(ns preise.db
  "app-db of the price admin: plain data only, plus the pure functions on it
   that events and subs share.

   ```
   {:status    :loading | :ready | :load-failed | :not-logged-in
    :heute     \"yyyy-MM-dd\"            ; local date at boot (next-saison fallback)
    :wohnungen (sorted-map id {:name … :hauptbild …})   ; legacy shape
    :daten     <preise.edn as on disk, strings as typed, without :selected>
    :rev       n                        ; bumped by every edit
    :ui        {:selected    #{season-id}
                :save-state  :ungespeichert | :ongoing | :erfolgreich
                             | :falsches-passwort | :fehlgeschlagen | :req-failed
                :trash-armed #{season-id}
                :datum-fehler nil | {:id id :feld :beginn|:ende
                                     :error :beginn-nicht-vor-ende}
                :geladen     {:refdata … :daten …}}} ; while loading
   ```

   Seasons are addressed by `:id` everywhere (the legacy admin matched them
   by value, so two identical seasons were edited together). Season dates
   stay ISO strings; all date arithmetic is calendar-day based
   (`kalender.core`)."
  (:require [kalender.core :as kal]
            [preise.model :as pm]))

(def allg-felder
  #{:aufschlag_zus_person :aufschlag_haustier :reinigung-unter
    :gaestebeitrag :gaestebeitrag_kind :energieaufschlag})

(def basis-felder
  #{:maximalbelegung :standardbelegung :grundpreis :reinigung
    :mindestaufenthalt_standard})

(def saison-felder
  #{:preis_tag :preis_woche :mindestaufenthalt})

(defn default-db [heute]
  {:status    :loading
   :heute     heute
   :wohnungen (sorted-map)
   :daten     nil
   :rev       0
   :ui        {:selected     #{}
               :save-state   :ungespeichert
               :trash-armed  #{}
               :datum-fehler nil
               :geladen      {}}})

;; ---------------------------------------------------------------------------
;; Load / save shape

(defn wohnungen-map
  "Refdata rows `[{:id 1 :name …} …]` → `(sorted-map 1 {:name …})`, as legacy
   `(into (sorted-map) (mapify-single :id …))`."
  [rows]
  (into (sorted-map) (map (juxt :id #(dissoc % :id))) rows))

(defn von-disk
  "On-disk preise → `[daten selected-ids]`: the `:selected` flags are lifted
   out of the seasons into a set of ids."
  [preise]
  (let [ss (vec (:saisonen preise))]
    [(assoc preise :saisonen (mapv #(dissoc % :selected) ss))
     (into #{} (comp (filter :selected) (map :id)) ss)]))

(defn auf-disk
  "app-db → the preisstruktur to save: `:daten` with `:selected true` written
   back on the selected seasons only (the legacy admin persisted the flag;
   readers ignore it)."
  [db]
  (let [sel (get-in db [:ui :selected])]
    (update (:daten db) :saisonen
            (fn [ss] (mapv #(cond-> % (sel (:id %)) (assoc :selected true)) ss)))))

;; ---------------------------------------------------------------------------
;; Season rules (legacy preise/components.cljs, on ISO days)

(defn- iso-max [isos] (reduce #(if (pos? (compare %2 %1)) %2 %1) isos))

(defn next-saison
  "Legacy `next-saison`: begins the day after the latest `ende` (gaps are
   ignored), lasts two weeks; with no seasons it begins `heute`. Carries an
   empty `:felder` so the result satisfies `preise.model/schema` (legacy
   left it out, which the step-9 save check would reject)."
  [saisonen heute]
  (let [enden  (filter kal/valid-iso? (map :ende saisonen))
        beginn (if (seq enden) (kal/plus-days (iso-max enden) 1) heute)]
    {:beginn beginn :ende (kal/plus-days beginn 14) :felder {}}))

(defn add-saison
  "Appends `saison` with id max+1 (legacy `add-saison!`)."
  [saisonen saison]
  (conj (vec saisonen) (assoc saison :id (pm/next-id saisonen))))

(defn plus-jahre
  "ISO day shifted by `n` years; 29 Feb onto a non-leap year becomes 28 Feb
   (cljs-time `plus` with `years`, as the legacy clone did)."
  [iso n]
  (let [[y m d] (map #(js/parseInt % 10) (.split iso "-"))
        y'      (+ y n)
        letzter (count (kal/days-in-month y' m))]
    (kal/plus-days (str y' "-" (subs iso 5 8) "01") (dec (min d letzter)))))

(defn clone-naechstes-jahr
  "Legacy \"für nächstes Jahr klonen\": every season whose id is in
   `selected` (in vector order) is copied with both dates one year later and
   appended with a fresh id (max+1, one after the other)."
  [saisonen selected]
  (reduce (fn [ss s]
            (add-saison ss (-> s
                               (dissoc :id)
                               (update :beginn plus-jahre 1)
                               (update :ende plus-jahre 1))))
          (vec saisonen)
          (filter #(selected (:id %)) saisonen)))

(defn dates-bad?
  "Legacy `check-saison`: beginn not strictly before ende."
  [{:keys [beginn ende]}]
  (not (and (kal/valid-iso? beginn) (kal/valid-iso? ende)
            (neg? (compare beginn ende)))))

(defn- intervall-ueberlappt?
  "cljs-time 2-arity `overlaps?` of [b1,e1) and [b2,e2) (touching
   intervals excluded)."
  [b1 e1 b2 e2]
  (and (not (or (= b1 e2) (= e1 b2)))
       (neg? (compare b2 e1))
       (pos? (compare e2 b1))))

(defn seasons-overlap?
  "Legacy `seasons-overlap?`: touching seasons (one's beginn = the other's
   ende) overlap, as do intersecting ones. A season with bad dates stands for
   the empty interval at `heute` (legacy `interval-sn` used `now`–`now`), so
   it overlaps a valid season that contains `heute` after its first day."
  [s1 s2 heute]
  (let [iv (fn [s] (if (dates-bad? s)
                     [(str heute "T12") (str heute "T12")]
                     [(:beginn s) (:ende s)]))
        [b1 e1] (iv s1)
        [b2 e2] (iv s2)]
    (boolean
     (or (= (:beginn s1) (:ende s2))
         (= (:beginn s2) (:ende s1))
         (intervall-ueberlappt? b1 e1 b2 e2)))))

(defn saison-fehler
  "Per season id `{:dates-bad? bool :overlaps? bool}` for every season with
   at least one problem (legacy `check-saison`). A season is never compared
   with itself (same id)."
  [saisonen heute]
  (into {}
        (keep (fn [s]
                (let [bad? (dates-bad? s)
                      ovl? (boolean (some #(and (not= (:id %) (:id s))
                                                (seasons-overlap? s % heute))
                                          saisonen))]
                  (when (or bad? ovl?)
                    [(:id s) {:dates-bad? bad? :overlaps? ovl?}]))))
        saisonen))

(defn sortierte-saisonen
  "Seasons by `beginn` (stable; legacy `sort-by :beginn`)."
  [saisonen]
  (sort-by :beginn saisonen))
