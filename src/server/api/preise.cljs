(ns api.preise
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [macchiato.util.response :as r]
            [db.setup :as db]
            [db.schema :as s]
            [preise.cache :as cache]
            [preise.persistence :as persist]
            [preise.speichern :as pruefung]))

(defhandler data [_req]
  (if-let [s (persist/read-edn-string)]
    (-> (r/ok s)
        (r/content-type "application/edn"))
    {:status  503
     :headers {"Content-Type" "application/edn"}
     :body    (pr-str {:error :preise-edn-missing})}))

(defhandler save [req]
  (pruefung/respond
   {:write!      persist/write-edn-string!
    :after-save! (fn []
                   (cache/refresh!)
                   (-> (r/ok (persist/read-edn-string))
                       (r/content-type "application/edn")))}
   req))

(defhandler refdata [_req]
  (p/let [rows (db/query
                {:select   [s/wohnungen-id
                            s/wohnungen-name
                            s/wohnungen-hauptbild]
                 :from     [s/wohnungen]
                 :order-by [s/wohnungen-name]})
          ;; wohnungen.id is bigInteger → pg returns int8 as a string.
          ;; preise.edn keys :felder/:basisdaten by integer wohnung-id,
          ;; so coerce here to keep client-side lookups type-aligned.
          wohnungen (mapv #(update % :id js/parseInt) rows)]
    (-> (r/ok (pr-str {:wohnungen wohnungen}))
        (r/content-type "application/edn"))))
