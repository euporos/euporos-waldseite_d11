(ns buchung.anfrage-pruefung
  "Server-side check and dispatch of a booking request (POST /api/anfrage).

   The handler (`api.anfrage`) only wires the real side effects into `deps`;
   everything else lives here so the Node tests can run it with stubs:

   `deps` keys (all required):
   - `:read-prices`   (fn [] raw-preise-or-nil) — on-disk shape, `preise.cache`
   - `:fetch-wohnung` (fn [wohnungsid] Promise<wohnung-or-nil>), wohnung =
                      `{:name s :belegung [vevents] :belegung-unvollstaendig? bool}`
                      as `buchung.fetch/fetch-ical-feeds` attaches it; may reject
   - `:send-mail!`    (fn [anfrage] Promise) — `buchung.mailer/send!`
   - `:track!`        (fn [event-name props]) — analytics, fire and forget
   - `:warn!`         (fn [msg]) — log line

   Order of checks (first failure wins, all → HTTP 400 with an EDN body):
   1. payload not a map / schema mismatch  → `{:error :invalid :fields [path …]}`
   2. wohnungsid unknown to the price file → `{:error :invalid :fields [[:wohnungsid]]}`
   3. departure not after arrival          → `{:error :anreise-nach-abreise}`
   4. guests > maximalbelegung             → `{:error :ueberbelegung :max n}`
   5. stay shares a night with a booking   → `{:error :belegt}`
   6. stay shorter than the minimum        → `{:error :mindestaufenthalt :min n}`
   A filled honeypot (`:website`) is answered like a success but nothing is
   sent. If the occupancy cannot be (fully) fetched, the request is accepted
   — checked against whatever events did arrive — and the mail carries a
   warning line. The mail always quotes the server-computed price; a
   differing client `:gesamtsumme` is only logged, and the apartment name
   in the mail is the DB name (client `:wohnungsname` only as fallback)."
  (:require [clojure.string :as str]
            [buchung.preis :as preis]
            [buchung.verfuegbarkeit :as vf]
            [preise.model :as pm]
            [specs.anfrage :as specs]))

(def hinweis-belegung-ungeprueft
  "Mail warning when the iCal occupancy could not be (fully) checked."
  "Die Belegung konnte nicht vollständig geprüft werden (Kalender-Abruf fehlgeschlagen) — bitte vor der Zusage manuell prüfen.")

(defn edn-response [status body]
  {:status  status
   :headers {"Content-Type" "application/edn"}
   :body    (pr-str body)})

(defn- fehler [body] (edn-response 400 body))

(defn- honeypot-filled? [{:keys [website]}]
  (and (some? website)
       (not (and (string? website) (str/blank? website)))))

(defn- pruefe-ohne-belegung
  "Checks 1–4. Returns `{:abgelehnt response}` or `{:preise normalized}`."
  [deps data]
  (if-let [fields (if (map? data) (specs/invalid-fields data) [])]
    {:abgelehnt (fehler {:error :invalid :fields fields})}
    (let [raw (or ((:read-prices deps)) {})
          id  (:wohnungsid data)]
      (if-not (contains? (:basisdaten raw) id)
        {:abgelehnt (fehler {:error :invalid :fields [[:wohnungsid]]})}
        (let [preise (pm/normalize raw)
              maxbel (get-in preise [:basisdaten id :maximalbelegung])]
          (cond
            (= :anreise-nach-abreise (:error (vf/stay-error data {:belegung []} preise)))
            {:abgelehnt (fehler {:error :anreise-nach-abreise})}

            (and (number? maxbel) (> (preis/gaestezahl data) maxbel))
            {:abgelehnt (fehler {:error :ueberbelegung :max maxbel})}

            :else {:preise preise}))))))

(defn- belegung-laden
  "Promise of `{:belegung prepared :vollstaendig? bool}`; never rejects."
  [deps id]
  (-> (js/Promise.resolve nil)
      (.then (fn [_] ((:fetch-wohnung deps) id)))
      (.then (fn [w]
               (when-not w
                 ((:warn! deps) (str "anfrage: wohnung " id " not found for occupancy check")))
               {:belegung      (vf/prepare-belegung (:belegung w))
                :vollstaendig? (boolean (and w (not (:belegung-unvollstaendig? w))))
                :name          (:name w)}))
      (.catch (fn [e]
                ((:warn! deps) (str "anfrage: occupancy fetch failed: " (some-> e .-message)))
                {:belegung [] :vollstaendig? false}))))

(defn- senden [deps data preise {:keys [vollstaendig?] db-name :name}]
  (let [;; The apartment name comes from the DB when the lookup succeeded;
        ;; the client's `:wohnungsname` is only a fallback.
        data         (cond-> data (string? db-name) (assoc :wohnungsname db-name))
        server-preis (preis/berechnen data preise)
        client-summe (let [p (:preis data)] (when (map? p) (:gesamtsumme p)))
        hinweise     (cond-> [] (not vollstaendig?) (conj hinweis-belegung-ungeprueft))
        mail-daten   (-> data
                         (dissoc :website)
                         (assoc :preis server-preis :hinweise hinweise))]
    (when (and (some? client-summe) (not= client-summe (:gesamtsumme server-preis)))
      ((:warn! deps) (str "anfrage: client price " (pr-str client-summe)
                          " differs from server price " (:gesamtsumme server-preis)
                          " (wohnung " (:wohnungsid data) ", " (:anreise data)
                          "–" (:abreise data) ") — mail uses the server price")))
    (-> (js/Promise.resolve nil)
        (.then (fn [_] ((:send-mail! deps) mail-daten)))
        (.then (fn [_]
                 ((:track! deps) "Buchungsanfrage gesendet"
                                 {:wohnung (:wohnungsname data)})
                 (edn-response 200 {:ok true})))
        (.catch (fn [e]
                  ((:warn! deps) (str "anfrage: mail failed: " (some-> e .-message)))
                  ((:track! deps) "Buchungsanfrage fehlgeschlagen"
                                  {:wohnung (:wohnungsname data)
                                   :error   (some-> e .-message)})
                  (edn-response 500 {:error :send-failed}))))))

(defn respond
  "Promise of the HTTP response for booking request `data` (the request's
   `:edn-params`)."
  [deps data]
  (cond
    (and (map? data) (honeypot-filled? data))
    (do ((:warn! deps) (str "anfrage: honeypot filled, dropping request from "
                            (pr-str (get-in data [:gast :email]))))
        (js/Promise.resolve (edn-response 200 {:ok true})))

    :else
    (let [{:keys [abgelehnt preise]} (pruefe-ohne-belegung deps data)]
      (if abgelehnt
        (do ((:warn! deps) (str "anfrage rejected: " (:body abgelehnt)))
            (js/Promise.resolve abgelehnt))
        (.then (belegung-laden deps (:wohnungsid data))
               (fn [{:keys [belegung] :as bel}]
                 (if-let [err (vf/stay-error data {:belegung belegung} preise)]
                   (let [body (select-keys err [:error :min])]
                     ((:warn! deps) (str "anfrage rejected: " (pr-str body)))
                     (fehler body))
                   (senden deps data preise bel))))))))
