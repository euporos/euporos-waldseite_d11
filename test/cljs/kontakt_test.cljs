(ns kontakt-test
  "JavaScript-free contact form: shared schema (`specs.kontakt`), signed
   timestamp token, honeypot, link heuristic and the submit decision
   (`kontakt.pruefung/respond`) with a stubbed mailer; plus the form hiccup
   (`kontakt.formular`) keeping the user's input on re-render."
  (:require [cljs.test :refer [deftest is testing async]]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [hiccups.runtime :as hiccups]
            [kontakt.formular :as formular]
            [kontakt.pruefung :as kp]
            [specs.kontakt :as spec]))

(def key-a (js/Buffer.from "a-test-key-of-some-length-000000"))
(def key-b (js/Buffer.from "another-test-key-000000000000000"))

(def t0 1800000000000)

(def gueltig
  {:name "Erika Muster" :email "erika@example.com"
   :kontaktnachricht "Ist die Wohnung im Mai frei?" :datenschutz? true})

;; ---------------------------------------------------------------------------
;; Schema
;; ---------------------------------------------------------------------------

(deftest schema
  (is (spec/valid? gueltig))
  (is (= #{:name} (spec/fehler-felder (assoc gueltig :name ""))))
  (is (= #{:name} (spec/fehler-felder (assoc gueltig :name (apply str (repeat 257 "x"))))))
  (is (= #{:email} (spec/fehler-felder (assoc gueltig :email "erika@example"))))
  (is (= #{:email} (spec/fehler-felder (assoc gueltig :email "kein mail"))))
  (is (= #{:kontaktnachricht} (spec/fehler-felder (assoc gueltig :kontaktnachricht ""))))
  (is (= #{:kontaktnachricht}
         (spec/fehler-felder (assoc gueltig :kontaktnachricht (apply str (repeat 5001 "x"))))))
  (is (spec/valid? (assoc gueltig :kontaktnachricht (apply str (repeat 5000 "x")))))
  (is (= #{:datenschutz?} (spec/fehler-felder (assoc gueltig :datenschutz? false))))
  (is (= #{:name :email :kontaktnachricht :datenschutz?}
         (spec/fehler-felder {:name "" :email "" :kontaktnachricht "" :datenschutz? false})))
  (is (= #{:name :email :kontaktnachricht :datenschutz?} (spec/fehler-felder nil))))

;; ---------------------------------------------------------------------------
;; Token
;; ---------------------------------------------------------------------------

(deftest token-sign-verify
  (let [tok (kp/token key-a t0)]
    (testing "valid after the minimum age, within 24 h"
      (is (= :ok (kp/token-status key-a tok (+ t0 kp/min-alter-ms))))
      (is (= :ok (kp/token-status key-a tok (+ t0 kp/max-alter-ms)))))
    (testing "too fast"
      (is (= :zu-schnell (kp/token-status key-a tok t0)))
      (is (= :zu-schnell (kp/token-status key-a tok (+ t0 3999))))
      (is (= :zu-schnell (kp/token-status key-a tok (- t0 10000))) "from the future"))
    (testing "expired"
      (is (= :abgelaufen (kp/token-status key-a tok (+ t0 kp/max-alter-ms 1)))))
    (testing "tampered / missing / other key (e.g. after a restart)"
      (is (= :ungueltig (kp/token-status key-a (assoc tok :ts (str (- t0 60000))) t0)))
      (is (= :ungueltig (kp/token-status key-a (update tok :sig #(str (subs % 1) "0"))
                                         (+ t0 5000))))
      (is (= :ungueltig (kp/token-status key-a (assoc tok :sig "") (+ t0 5000))))
      (is (= :ungueltig (kp/token-status key-a (assoc tok :sig "abc") (+ t0 5000))))
      (is (= :ungueltig (kp/token-status key-a {} (+ t0 5000))))
      (is (= :ungueltig (kp/token-status key-a {:ts "" :sig ""} (+ t0 5000))))
      (is (= :ungueltig (kp/token-status key-a {:ts "12x" :sig (:sig tok)} (+ t0 5000))))
      (is (= :ungueltig (kp/token-status key-b tok (+ t0 5000)))))))

;; ---------------------------------------------------------------------------
;; Heuristics
;; ---------------------------------------------------------------------------

(deftest link-heuristik
  (is (not (kp/zu-viele-links? "Hallo, ist im Mai noch frei?")))
  (is (not (kp/zu-viele-links? nil)))
  (is (not (kp/zu-viele-links? "siehe https://www.example.com und http://b.de")))
  (is (not (kp/zu-viele-links? "https://a.de www.b.de")))
  (is (kp/zu-viele-links? "https://a.de http://b.de www.c.de"))
  (is (kp/zu-viele-links? "HTTPS://a.de https://b.de https://c.de"))
  (is (kp/zu-viele-links? "buy [url=http://x]now[/url]"))
  (is (kp/zu-viele-links? "click <a href=\"x\">here</a>"))
  (is (kp/zu-viele-links? "<A\nhref=x>")))

(deftest honeypot
  (is (not (kp/honeypot? {:website ""})))
  (is (not (kp/honeypot? {:website "  "})))
  (is (not (kp/honeypot? {})))
  (is (kp/honeypot? {:website "http://spam.example"})))

(deftest formular-werte
  (is (= {:name "Erika" :email "e@x.de" :kontaktnachricht "Hallo"
          :datenschutz? true :website "" :ts "1" :sig "ab"}
         (kp/formular-werte {:name " Erika " :email "e@x.de\n" :kontaktnachricht " Hallo "
                             :datenschutz? "on" :ts "1" :sig "ab"})))
  (is (false? (:datenschutz? (kp/formular-werte {}))))
  (is (= "" (:name (kp/formular-werte {})))))

;; ---------------------------------------------------------------------------
;; respond
;; ---------------------------------------------------------------------------

(defn- params
  "Form params as the browser posts them, token issued at t0."
  [& {:as overrides}]
  (merge {:name "Erika Muster" :email "erika@example.com"
          :kontaktnachricht "Ist die Wohnung im Mai frei?"
          :datenschutz? "on" :website ""}
         (kp/token key-a t0)
         overrides))

(defn- stub-deps [& {:keys [jetzt send-mail!] :or {jetzt (+ t0 10000)}}]
  (let [calls (atom {:mails [] :logs [] :tracks []})]
    [{:schluessel key-a
      :jetzt      (constantly jetzt)
      :send-mail! (fn [m]
                    (swap! calls update :mails conj m)
                    (if send-mail! (send-mail! m) (js/Promise.resolve :sent)))
      :log!       (fn [msg] (swap! calls update :logs conj msg))
      :track!     (fn [ev props] (swap! calls update :tracks conj [ev props]))}
     calls]))

(defn- run [done p check & opts]
  (let [[deps calls] (apply stub-deps opts)]
    (-> (kp/respond deps p)
        (.then (fn [outcome] (check outcome @calls)))
        (.catch (fn [e] (is (nil? e) "respond must not reject")))
        (.finally done))))

(deftest respond-sendet
  (async done
    (run done (params)
         (fn [outcome {:keys [mails tracks]}]
           (is (= {:seite :gesendet} outcome))
           (is (= 1 (count mails)))
           (is (= {:replyTo "erika@example.com"
                   :subject "neue Kontaktanfrage von Erika Muster"
                   :text    (str "Ist die Wohnung im Mai frei?"
                                 "\n\n--------\ngesendet von Erika Muster <erika@example.com>")}
                  (first mails)))
           (is (= 1 (count tracks)))))))

(deftest respond-honeypot
  (async done
    (run done (params :website "http://spam.example")
         (fn [outcome {:keys [mails logs]}]
           (is (= {:seite :gesendet} outcome) "pretends success")
           (is (empty? mails))
           (is (some #(str/includes? % "honeypot") logs))))))

(deftest respond-links
  (async done
    (run done (params :kontaktnachricht "a https://a.de b https://b.de c https://c.de")
         (fn [outcome {:keys [mails logs]}]
           (is (= {:seite :gesendet} outcome) "pretends success")
           (is (empty? mails))
           (is (some #(str/includes? % "links") logs))))))

(defn- erwartete-werte [p]
  {:name (:name p) :email (:email p) :kontaktnachricht (:kontaktnachricht p)
   :datenschutz? true})

(deftest respond-zu-schnell
  (async done
    (run done (params)
         (fn [outcome {:keys [mails]}]
           (is (empty? mails))
           (is (= :formular (:seite outcome)))
           (is (= :erneut-senden (:hinweis outcome)))
           (is (= (erwartete-werte (params)) (:werte outcome)) "input preserved"))
         :jetzt (+ t0 1000))))

(deftest respond-abgelaufen
  (async done
    (run done (params)
         (fn [outcome {:keys [mails]}]
           (is (empty? mails))
           (is (= :erneut-senden (:hinweis outcome)))
           (is (= (erwartete-werte (params)) (:werte outcome))))
         :jetzt (+ t0 kp/max-alter-ms 1))))

(deftest respond-ohne-token
  (testing "no-JS user whose token died with a server restart / missing token"
    (async done
      (run done (dissoc (params) :ts :sig)
           (fn [outcome {:keys [mails]}]
             (is (empty? mails))
             (is (= :formular (:seite outcome)))
             (is (= :erneut-senden (:hinweis outcome))))))))

(deftest respond-validierung
  (async done
    (run done (params :email "kaputt" :datenschutz? nil)
         (fn [outcome {:keys [mails]}]
           (is (empty? mails))
           (is (= :formular (:seite outcome)))
           (is (nil? (:hinweis outcome)))
           (is (= #{:email :datenschutz?} (:fehler outcome)))
           (is (= {:name "Erika Muster" :email "kaputt"
                   :kontaktnachricht "Ist die Wohnung im Mai frei?" :datenschutz? false}
                  (:werte outcome)))))))

(deftest respond-mail-fehler
  (async done
    (run done (params)
         (fn [outcome {:keys [mails logs]}]
           (is (= {:seite :fehlgeschlagen} outcome))
           (is (= 1 (count mails)))
           (is (some #(str/includes? % "sending failed") logs)))
         :send-mail! (fn [_] (js/Promise.reject (js/Error. "smtp down"))))))

;; ---------------------------------------------------------------------------
;; Form hiccup
;; ---------------------------------------------------------------------------

(defn- render [opts]
  (hiccups/render-html
   (formular/formular (merge {:locale :de :action "/de/kontaktform" :af-token "AF"
                              :token (kp/token key-a t0)}
                             opts))))

(defn- elements [tree]
  (let [acc (atom [])]
    (walk/prewalk (fn [x] (when (and (vector? x) (keyword? (first x))) (swap! acc conj x)) x)
                  tree)
    @acc))

(deftest formular-rerender-behaelt-werte
  (let [werte {:name "Erika <Muster>" :email "kaputt"
               :kontaktnachricht "Zeile 1\n</textarea><script>x</script>" :datenschutz? true}
        html  (render {:werte werte :fehler #{:email} :hinweis :erneut-senden})]
    (is (str/includes? html "value=\"Erika &lt;Muster&gt;\"") "name kept, escaped")
    (is (str/includes? html "value=\"kaputt\"") "email kept")
    (is (str/includes? html "Zeile 1\n&lt;/textarea&gt;&lt;script&gt;x&lt;/script&gt;</textarea>")
        "message kept, escaped")
    (is (re-find #"<input[^>]*checked" html) "checkbox stays ticked")
    (is (str/includes? html "Bitte geben Sie ein gültige Emailadresse ein."))
    (is (str/includes? html "is-danger"))
    (is (str/includes? html "Ihre Nachricht wurde noch nicht verschickt"))
    (is (not (str/includes? html "Bitte geben Sie Ihren Namen ein.")))
    (is (not (re-find #"(?i)<script" html)) "no script tag in the form")))

(deftest formular-token-und-honeypot
  (let [tok  (kp/token key-a t0)
        tree (formular/formular {:locale :en :action "/en/kontaktform" :af-token "AF"
                                 :token tok :datenschutz-href "/en/7-privacy"})
        els  (elements tree)
        attrs-of (fn [n] (some #(when (= n (:name (second %))) (second %)) els))]
    (is (= (:ts tok) (:value (attrs-of "ts"))))
    (is (= (:sig tok) (:value (attrs-of "sig"))))
    (is (= "AF" (:value (attrs-of "__anti-forgery-token"))))
    (is (= {:type "text" :id "kontakt-website" :name "website" :value ""
            :autocomplete "off" :tabindex "-1"}
           (attrs-of "website")))
    (is (some #(and (= :div (first %)) (= "true" (:aria-hidden (second %)))) els))
    (is (some #(and (= :a (first %)) (= "/en/7-privacy" (:href (second %)))) els)
        "real privacy link")
    (is (not-any? #(= "#" (:href (second %))) (filter (comp map? second) els)))
    (is (not (str/includes? (render {}) "is-danger")) "pristine form has no errors")))

(deftest datenschutz-seite
  (let [de [{:id 3 :titel "Impressum"} {:id 7 :titel "Datenschutzerklärung"}]
        en [{:id "3" :titel "Imprint"} {:id "7" :titel "Privacy policy"}]]
    (is (= {:id 7 :titel "Privacy policy"} (formular/datenschutz-seite de en)))
    (is (= {:id 7 :titel "Datenschutzerklärung"} (formular/datenschutz-seite de [])))
    (is (nil? (formular/datenschutz-seite [{:id 3 :titel "Impressum"}] en)))))
