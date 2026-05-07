(ns api.preise
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [kitchen-async.promise :as p]
            [macchiato.util.response :as r]
            [db.setup :as db]
            [db.schema :as s]
            [preise.persistence :as persist]))

(defhandler data [_req]
  (if-let [s (persist/read-edn-string)]
    (-> (r/ok s)
        (r/content-type "application/edn"))
    {:status  503
     :headers {"Content-Type" "application/edn"}
     :body    (pr-str {:error :preise-edn-missing})}))

(defhandler save [req]
  (let [s (or (get-in req [:params :preisstruktur])
              (get-in req [:form-params "preisstruktur"]))]
    (if (or (nil? s) (= "" s))
      {:status  400
       :headers {"Content-Type" "text/plain"}
       :body    "fail"}
      (case (persist/write-edn-string! s)
        :ok          (-> (r/ok (persist/read-edn-string))
                         (r/content-type "application/edn"))
        :parse-fail  {:status 400 :headers {"Content-Type" "text/plain"} :body "fail"}
        :verify-fail {:status 500 :headers {"Content-Type" "text/plain"} :body "fail"}))))

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
