(ns db.queries
  (:require [honey.sql.helpers :as hsql]
            [db.setup :as db]
            [db.schema :as s :refer [as]]))

;; ===========================
;; Query Composition Helpers
;; ===========================

(defn- add-year-filter
  [query year]
  (if year
    (hsql/where query [:and
                       [:>= s/concerts-datetime (str year "-01-01T00:00:00.000Z")]
                       [:< s/concerts-datetime (str (inc year) "-01-01T00:00:00.000Z")]])
    query))

(defn- add-time-filter
  [query filter-type]
  (hsql/where query
              (case filter-type
                :past [:< s/concerts-datetime (.toISOString (new js/Date))]
                :current [:> s/concerts-datetime (.toISOString (new js/Date))])))

(defn- add-musician-filter
  [query musician-id]
  (-> query
      (hsql/join [s/concerts_musicians :cm]
                 [:= :cm.concerts_id s/concerts-id]
                 [s/musicians :m]
                 [:= :m.id :cm.musicians_id])
      (hsql/where [:= :m.id musician-id])))

;; ===========================
;; Base Concert Queries
;; ===========================

(defn concerts-for-overview [locale]
  (-> {:select [s/concerts-id s/concerts-datetime s/concerts-image
                s/concerts-venue s/concerts-sold_out
                (db/localized s/concerts-title locale)
                (db/localized s/concerts-meta_description locale)
                (db/localized s/concerts-description_short locale)]
       :from [[s/concerts_t s/concerts]]}
      (hsql/where [:not= s/concerts-datetime nil])
      (hsql/left-join s/concerts_concerts
                      [:= s/concerts-id s/concerts_concerts-related_concerts_id])
      (hsql/select (as s/concerts_concerts-concerts_id))
      (hsql/left-join s/venues
                      [:= s/concerts-venue s/venues-id])
      (hsql/select (as s/venues-Name)
                   (as s/venues-city)
                   (as s/venues-image))
      (assoc :order-by s/concerts-datetime)))

(defn concerts-for-overview-past
  "Returns past concerts, optionally filtered by year"
  ([locale] (concerts-for-overview-past locale nil))
  ([locale year]
   (-> (concerts-for-overview locale)
       (add-time-filter :past)
       (add-year-filter year)
       (hsql/select [true :past]))))

(defn concerts-for-overview-current
  "Returns current/future concerts, optionally filtered by year"
  ([locale] (concerts-for-overview-current locale nil))
  ([locale year]
   (-> (concerts-for-overview locale)
       (add-time-filter :current)
       (add-year-filter year))))

(defn concerts-for-musician-past
  "Returns past concerts for a specific musician"
  [locale musician-id]
  (-> (concerts-for-overview-past locale)
      (add-musician-filter musician-id)))

(defn concerts-for-musician-current
  "Returns current/future concerts for a specific musician"
  [locale musician-id]
  (-> (concerts-for-overview-current locale)
      (add-musician-filter musician-id)))
