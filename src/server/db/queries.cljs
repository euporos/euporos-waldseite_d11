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

(defn haus-detail [locale haus-id]
  {:select [s/haeuser-id
            s/haeuser-name
            s/haeuser-adresse
            s/haeuser-hauptbild
            s/haeuser-google_maplink
            (db/localized s/haeuser-beschreibung locale)
            (db/localized s/haeuser-ausstattung locale)
            (db/localized s/haeuser-anreisetext locale)
            (db/localized s/haeuser-buchungstext locale)
            (db/localized s/haeuser-meta_description locale)
            (db/localized s/haeuser-ausstattung_tabelle locale)]
   :from   [[s/haeuser_t s/haeuser]]
   :where  [:= s/haeuser-id haus-id]
   :limit  1})

(defn haus-bilder [haus-id]
  {:select   [:haeuser_directus_files.directus_files_id]
   :from     [:haeuser_directus_files]
   :where    [:= :haeuser_directus_files.haeuser_id haus-id]
   :order-by [:haeuser_directus_files.id]})

(defn wohnungen-by-haus [locale haus-id]
  {:select   [s/wohnungen-id
              s/wohnungen-name
              s/wohnungen-hauptbild
              s/wohnungen-dtvsterne
              (db/localized s/wohnungen-beschreibung locale)]
   :from     [[s/wohnungen_t s/wohnungen]]
   :where    [:= s/wohnungen-haus haus-id]
   :order-by [s/wohnungen-name]})

(defn ausfluege-by-haus [locale haus-id]
  {:select   [s/ausfluege-id
              s/ausfluege-bild
              (db/localized s/ausfluege-titel locale)
              (db/localized s/ausfluege-beschreibung locale)]
   :from     [[s/ausfluege_t s/ausfluege]]
   :join     [:ausfluege_haeuser
              [:= :ausfluege_haeuser.ausfluege_id s/ausfluege-id]]
   :where    [:= :ausfluege_haeuser.haeuser_id haus-id]
   :order-by [s/ausfluege-id]})

(defn ausfluege-overview [locale]
  {:select   [s/ausfluege-id
              s/ausfluege-bild
              (db/localized s/ausfluege-titel locale)
              (db/localized s/ausfluege-beschreibung locale)]
   :from     [[s/ausfluege_t s/ausfluege]]
   :order-by [s/ausfluege-id]})

(defn allgemeines-content [locale]
  {:select [s/allgemeines-id
            (db/localized s/allgemeines-ausstattung_tabelle locale)]
   :from   [[s/allgemeines_t s/allgemeines]]
   :limit  1})

(defn wohnungen-with-ical []
  {:select   [s/wohnungen-id
              s/wohnungen-name
              s/wohnungen-ical
              s/wohnungen-hauptbild
              s/wohnungen-haus]
   :from     [s/wohnungen]
   :order-by [s/wohnungen-name]})

(defn wohnung-detail [locale wohnung-id]
  {:select [s/wohnungen-id
            s/wohnungen-name
            s/wohnungen-hauptbild
            s/wohnungen-dtvsterne
            s/wohnungen-haus
            (db/localized s/wohnungen-beschreibung locale)
            (db/localized s/wohnungen-ausstattung_tabelle locale)]
   :from   [[s/wohnungen_t s/wohnungen]]
   :where  [:= s/wohnungen-id wohnung-id]
   :limit  1})

(defn haus-ausstattung-tabelle [locale haus-id]
  {:select [(db/localized s/haeuser-ausstattung_tabelle locale)]
   :from   [[s/haeuser_t s/haeuser]]
   :where  [:= s/haeuser-id haus-id]
   :limit  1})

(defn wohnung-bilder [wohnung-id]
  {:select   [:wohnungen_directus_files.directus_files_id]
   :from     [:wohnungen_directus_files]
   :where    [:= :wohnungen_directus_files.wohnungen_id wohnung-id]
   :order-by [:wohnungen_directus_files.id]})

(defn galerie-overview [locale]
  {:select    [s/galerie-id
               s/galerie-bild
               s/galerie-haus
               (db/localized s/galerie-beschreibung locale)
               [:directus_files.width :width]
               [:directus_files.height :height]]
   :from      [[s/galerie_t s/galerie]]
   :left-join [:directus_files [:= :directus_files.id s/galerie-bild]]
   :where     [:is-not s/galerie-bild nil]
   :order-by  [s/galerie-haus s/galerie-id]})

(defn einzelseite-detail [locale einzelseite-id]
  {:select [s/einzelseiten-id
            (db/localized s/einzelseiten-titel locale)
            (db/localized s/einzelseiten-text locale)
            (db/localized s/einzelseiten-meta_description locale)]
   :from   [[s/einzelseiten_t s/einzelseiten]]
   :where  [:= s/einzelseiten-id einzelseite-id]
   :limit  1})

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
