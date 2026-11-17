(ns anfrage-pruefung-test
  "Server-side booking request check (`buchung.anfrage-pruefung/respond`,
   the logic behind POST /api/anfrage) with stubbed price cache, occupancy
   fetch, mailer and logger; plus the iCal fetch failure flag of
   `buchung.fetch` and the warning line of `buchung.mailtext/body`."
  (:require [cljs.test :refer [deftest is testing async]]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [buchung.anfrage-pruefung :as ap]
            [buchung.fetch :as fetch]
            [buchung.mailtext :as mailtext]
            [buchung.preis :as preis]
            [preise.model :as pm]
            [test-support.fixtures :as fx]))

(def gueltig
  "Wohnung 1, 7 nights in season 58 (min 5), 2 guests (max 5), free."
  {:anreise                  "2027-05-08"
   :abreise                  "2027-05-15"
   :gaestezahl               2
   :haustierzahl             1
   :wohnungsid               1
   :wohnungsname             "Wohnung 1"
   :gast                     {:vorname "Erika" :nachname "Muster"
                              :email "erika@example.com" :telefonnummer "012345"}
   :datenschutz-gelesen?     true
   :mietbedingungen-gelesen? true})

(defn- stub-deps
  "Deps over the fixtures. Returns [deps calls-atom]; calls collects
   {:mails [...] :warns [...] :tracks [...]}."
  [& {:keys [fetch-wohnung send-mail!]}]
  (let [calls (atom {:mails [] :warns [] :tracks []})]
    [{:read-prices   fx/preise
      :fetch-wohnung (or fetch-wohnung
                         (fn [id] (js/Promise.resolve
                                   {:name     (str "Wohnung " id)
                                    :belegung (get (fx/belegung) id)})))
      :send-mail!    (fn [m]
                       (swap! calls update :mails conj m)
                       (if send-mail! (send-mail! m) (js/Promise.resolve :sent)))
      :track!        (fn [ev props] (swap! calls update :tracks conj [ev props]))
      :warn!         (fn [msg] (swap! calls update :warns conj msg))}
     calls]))

(defn- body [resp] (reader/read-string (:body resp)))

(defn- run
  "Calls respond with `data`, then `(check resp calls)`, then done."
  [done data check & opts]
  (let [[deps calls] (apply stub-deps opts)]
    (-> (ap/respond deps data)
        (.then (fn [resp]
                 (is (= "application/edn" (get-in resp [:headers "Content-Type"])))
                 (check resp @calls)))
        (.catch (fn [e] (is (nil? e) "respond must not reject")))
        (.finally done))))

(defn- server-preis [data]
  (preis/berechnen data (pm/normalize (fx/preise))))

(deftest gueltige-anfrage
  (async done
    (run done gueltig
         (fn [resp {:keys [mails warns tracks]}]
           (is (= 200 (:status resp)))
           (is (= {:ok true} (body resp)))
           (is (= 1 (count mails)))
           (let [m (first mails)]
             (is (= (server-preis gueltig) (:preis m)) "mail carries the server price")
             (is (number? (get-in m [:preis :gesamtsumme])))
             (is (= [] (:hinweise m)))
             (is (= "erika@example.com" (get-in m [:gast :email]))))
           (is (empty? warns))
           (is (= "Buchungsanfrage gesendet" (ffirst tracks)))))))

(deftest schema-verletzung
  (async done
    (run done (-> gueltig
                  (assoc-in [:gast :email] "keine-mail")
                  (dissoc :gaestezahl))
         (fn [resp {:keys [mails]}]
           (is (= 400 (:status resp)))
           (let [{:keys [error fields]} (body resp)]
             (is (= :invalid error))
             (is (some #{[:gast :email]} fields))
             (is (some #{[:gaestezahl]} fields)))
           (is (empty? mails))))))

(deftest kein-map-payload
  (async done
    (run done nil
         (fn [resp {:keys [mails]}]
           (is (= 400 (:status resp)))
           (is (= :invalid (:error (body resp))))
           (is (empty? mails))))))

(deftest datum-muss-existieren
  (async done
    (run done (assoc gueltig :abreise "2027-02-30")
         (fn [resp _]
           (is (= 400 (:status resp)))
           (is (= {:error :invalid :fields [[:abreise]]} (body resp)))))))

(deftest unbekannte-wohnung
  (async done
    (run done (assoc gueltig :wohnungsid 99)
         (fn [resp {:keys [mails]}]
           (is (= 400 (:status resp)))
           (is (= {:error :invalid :fields [[:wohnungsid]]} (body resp)))
           (is (empty? mails))))))

(deftest anreise-nach-abreise
  (async done
    (run done (assoc gueltig :anreise "2027-05-15" :abreise "2027-05-08")
         (fn [resp _]
           (is (= 400 (:status resp)))
           (is (= {:error :anreise-nach-abreise} (body resp)))))))

(deftest belegt
  (async done
    (run done (assoc gueltig :anreise "2026-07-05" :abreise "2026-07-12")
         (fn [resp {:keys [mails]}]
           (is (= 400 (:status resp)))
           (is (= {:error :belegt} (body resp)))
           (is (empty? mails))))))

(deftest zu-kurz
  (async done
    (run done (assoc gueltig :abreise "2027-05-10")
         (fn [resp {:keys [mails]}]
           (is (= 400 (:status resp)))
           (is (= {:error :mindestaufenthalt :min 5} (body resp)))
           (is (empty? mails))))))

(deftest ueberbelegung
  (async done
    (run done (assoc gueltig :gaestezahl 6)
         (fn [resp {:keys [mails]}]
           (is (= 400 (:status resp)))
           (is (= {:error :ueberbelegung :max 5} (body resp)))
           (is (empty? mails))))))

(deftest ical-fehler
  (testing "occupancy fetch rejects → accepted, mail warns"
    (async done
      (run done gueltig
           (fn [resp {:keys [mails warns]}]
             (is (= 200 (:status resp)))
             (is (= [ap/hinweis-belegung-ungeprueft] (:hinweise (first mails))))
             (is (= (server-preis gueltig) (:preis (first mails))))
             (is (some #(str/includes? % "occupancy fetch failed") warns)))
           :fetch-wohnung (fn [_] (js/Promise.reject (js/Error. "boom")))))))

(deftest ical-unvollstaendig
  (testing "a feed failed but others arrived → still checked against them, mail warns"
    (async done
      (let [fetch-wohnung (fn [id] (js/Promise.resolve
                                    {:name "W" :belegung (get (fx/belegung) id)
                                     :belegung-unvollstaendig? true}))
            [deps calls] (stub-deps :fetch-wohnung fetch-wohnung)]
        (-> (ap/respond deps (assoc gueltig :anreise "2026-07-05" :abreise "2026-07-12"))
            (.then (fn [resp]
                     (is (= {:error :belegt} (body resp)))
                     (ap/respond deps gueltig)))
            (.then (fn [resp]
                     (is (= 200 (:status resp)))
                     (is (= [ap/hinweis-belegung-ungeprueft]
                            (:hinweise (first (:mails @calls)))))))
            (.finally done))))))

(deftest honeypot
  (async done
    (run done (assoc gueltig :website "http://spam.example")
         (fn [resp {:keys [mails warns]}]
           (is (= 200 (:status resp)) "looks like success to the bot")
           (is (empty? mails))
           (is (some #(str/includes? % "honeypot") warns))))))

(deftest leerer-honeypot
  (async done
    (run done (assoc gueltig :website "")
         (fn [resp {:keys [mails]}]
           (is (= 200 (:status resp)))
           (is (= 1 (count mails)))
           (is (not (contains? (first mails) :website)))))))

(deftest client-preis-abweichend
  (async done
    (run done (assoc gueltig :preis {:gesamtsumme 1})
         (fn [resp {:keys [mails warns]}]
           (is (= 200 (:status resp)))
           (is (= (server-preis gueltig) (:preis (first mails))))
           (is (= 1 (count warns)))
           (is (str/includes? (first warns) "differs from server price"))))))

(deftest client-preis-gleich
  (async done
    (run done (assoc gueltig :preis (server-preis gueltig))
         (fn [resp {:keys [warns]}]
           (is (= 200 (:status resp)))
           (is (empty? warns))))))

(deftest mail-fehler
  (async done
    (run done gueltig
         (fn [resp {:keys [tracks]}]
           (is (= 500 (:status resp)))
           (is (= {:error :send-failed} (body resp)))
           (is (= "Buchungsanfrage fehlgeschlagen" (ffirst tracks))))
         :send-mail! (fn [_] (js/Promise.reject (js/Error. "smtp down"))))))

(deftest mail-text
  (let [preis (server-preis gueltig)
        text  (mailtext/body (assoc gueltig :preis preis
                                  :hinweise [ap/hinweis-belegung-ungeprueft]))]
    (is (str/starts-with? text (str "⚠ " ap/hinweis-belegung-ungeprueft "\n")))
    (is (str/includes? text (str "Berechneter Preis: " (:gesamtsumme preis) "€"))))
  (is (str/starts-with? (mailtext/body (assoc gueltig :hinweise [])) "Erika Muster")))

(deftest fetch-markiert-fehlgeschlagene-feeds
  (async done
    (let [orig (.-fetch js/globalThis)
          restore #(set! (.-fetch js/globalThis) orig)]
      (set! (.-fetch js/globalThis)
            (fn [url]
              (if (str/includes? url "kaputt")
                (js/Promise.reject (js/Error. "ECONNREFUSED"))
                (js/Promise.resolve
                 #js {:ok true
                      :text (fn [] (js/Promise.resolve
                                    "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR\n"))}))))
      ;; kitchen-async's p/all is a thenable without .catch/.finally
      (-> (js/Promise.resolve
           (fetch/fetch-ical-feeds [{:id 1 :ical "http://gut/a.ics"}
                                    {:id 2 :ical "http://gut/b.ics http://kaputt/c.ics"}]))
          (.then (fn [[w1 w2]]
                   (is (= [] (:belegung w1)))
                   (is (not (:belegung-unvollstaendig? w1)))
                   (is (not (contains? w1 :ical)))
                   (is (true? (:belegung-unvollstaendig? w2)))))
          (.catch (fn [e] (is (nil? e))))
          (.finally (fn [] (restore) (done)))))))
