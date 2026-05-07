(ns db.queries
  (:require [db.setup :as db]
            [db.schema :as s]))

;; ===========================
;; Waldseite — Startseite
;; ===========================

(defn startseite-content [locale]
  {:select [s/startseite-id
            s/startseite-familienbild
            (db/localized s/startseite-hauptueberschrift locale)
            (db/localized s/startseite-haupttext locale)]
   :from   [[s/startseite_t s/startseite]]
   :limit  1})

(defn haeuser-overview [_locale]
  {:select   [s/haeuser-id
              s/haeuser-name
              s/haeuser-hauptbild]
   :from     [[s/haeuser_t s/haeuser]]
   :order-by [s/haeuser-name]})

(defn news-overview [locale]
  {:select   [s/news-id
              s/news-bild
              s/news-datum
              (db/localized s/news-titel locale)
              (db/localized s/news-text locale)]
   :from     [[s/news_t s/news]]
   :order-by [[s/news-datum :desc]]})

(defn news-for-haus [locale haus-id]
  {:select   [s/news-id
              s/news-bild
              s/news-datum
              (db/localized s/news-titel locale)
              (db/localized s/news-text locale)]
   :from     [[s/news_t s/news]]
   :join     [:news_haeuser [:= :news_haeuser.news_id s/news-id]]
   :where    [:= :news_haeuser.haeuser_id haus-id]
   :order-by [[s/news-datum :desc]]})

(defn einzelseiten-for-menus [locale]
  {:select   [s/einzelseiten-id
              s/einzelseiten-menue
              (db/localized s/einzelseiten-titel locale)]
   :from     [[s/einzelseiten_t s/einzelseiten]]
   :where    [:is-not s/einzelseiten-menue nil]
   :order-by [s/einzelseiten-id]})
