(ns preise.ajaxing
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]
            [preise.util :as u]))

(def ^:private base
  (str js/window.location.protocol "//" js/window.location.host))

(defn get-data
  "Loads wohnungen (from /api/preise/refdata) and prices (from
   /api/preise/data) in parallel, then resets the two atoms and calls
   `callback`. On 401 the operator is sent to the Directus login page."
  [wohnungsatom datenatom callback]
  (go
    (let [refdata (<! (http/get (str base "/api/preise/refdata")))
          preise  (<! (http/get (str base "/api/preise/data")))]
      (cond
        (= 401 (:status refdata))
        (set! js/window.location (or (get-in refdata [:headers "location"])
                                     "/admin/preise"))

        (not (and (:success refdata) (:success preise)))
        (js/alert "Daten konnten nicht geladen werden.")

        :else
        ;; cljs-http auto-parses application/edn — :body is already data.
        (do
          (reset! datenatom (u/timify-daten (:body preise)))
          (reset! wohnungsatom (into (sorted-map)
                                     (u/mapify-single :id (:wohnungen (:body refdata)))))
          (callback))))))

(defn post-preise!
  "Saves the in-memory price tree back to disk. Auth is via the Directus
   session cookie picked up by the backend middleware; on 401 we surface
   :falsches-passwort (label kept for UI continuity — really 'not logged
   in')."
  [speicherzustand-a daten]
  (reset! speicherzustand-a :ongoing)
  (go
    (let [resp (<! (http/post
                    (str base "/api/preise/save")
                    {:form-params
                     {:__anti-forgery-token
                      (.getAttribute (js/document.getElementById "ifg") "token")
                      :preisstruktur (str (u/serialize-daten daten))}}))]
      (cond
        (= 401 (:status resp)) (reset! speicherzustand-a :falsches-passwort)
        (not (:success resp))  (reset! speicherzustand-a :req-failed)
        (= "fail" (:body resp)) (reset! speicherzustand-a :fehlgeschlagen)
        :else (reset! speicherzustand-a :erfolgreich)))))
