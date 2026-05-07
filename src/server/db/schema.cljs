(ns db.schema
  (:require-macros [directus-schema.core :refer [defschema]]))

;; :locales must match the `code` column of the live `languages` table.
(defschema "schema/snapshot.json"
  {:locales              ["de" "en" "nl"]
   :translations-suffix  "_translations"
   :language-key         "languages_code"})
