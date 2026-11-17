(ns kontakt.pruefung
  "Server-side check of the JavaScript-free contact form
   (POST /:locale/kontaktform). Config-free so the Node tests can run it;
   the handler (`seiten.kontaktform`) wires in mail, logging and rendering.

   Anti-spam (no secret in settings, nothing needs JS):
   - Signed timestamp token: the form carries hidden `ts` (ms since epoch)
     and `sig` = HMAC-SHA256(ts) under a per-process random key. Missing or
     bad signature, younger than `min-alter-ms` or older than `max-alter-ms`
     → nothing is sent, the form is re-rendered with the input preserved, a
     fresh token and a \"please submit again\" note (a server restart
     invalidates all tokens, so humans must be able to recover).
   - Honeypot field `website`: filled → answered like a success, not sent.
   - Link heuristic (`zu-viele-links?`) → answered like a success, not sent.
   - Per-IP rate limit (bucket `:kontakt`) is route middleware, see
     `seiten.kontaktform/wrap-rate-limit-page`.

   `respond` resolves to an outcome map the handler renders:
   - `{:seite :formular :werte {…} :fehler #{field …} :hinweis kw-or-nil}`
   - `{:seite :gesendet}` (also for silently dropped spam)
   - `{:seite :fehlgeschlagen}` (mail transport failed)"
  (:require ["crypto" :as crypto]
            [clojure.string :as str]
            [specs.kontakt :as spec]))

(def min-alter-ms 4000)
(def max-alter-ms (* 24 60 60 1000))

(defonce schluessel
  ;; Per-process HMAC key; tokens die with the process (see ns doc).
  (.randomBytes crypto 32))

;; ---------------------------------------------------------------------------
;; Token
;; ---------------------------------------------------------------------------

(defn signatur
  "Hex HMAC-SHA256 of the timestamp string `ts` under `key`."
  [key ts]
  (-> (.createHmac crypto "sha256" key)
      (.update (str ts))
      (.digest "hex")))

(defn token
  "Fresh `{:ts \"<ms>\" :sig \"<hex>\"}` for the hidden form fields."
  [key now-ms]
  (let [ts (str now-ms)]
    {:ts ts :sig (signatur key ts)}))

(defn- gleich?
  "Constant-time comparison of two hex strings."
  [a b]
  (and (string? a) (string? b)
       (= (count a) (count b))
       (.timingSafeEqual crypto (js/Buffer.from a) (js/Buffer.from b))))

(defn token-status
  "`:ok`, `:ungueltig` (missing/malformed/tampered), `:zu-schnell`
   (younger than `min-alter-ms`, or from the future) or `:abgelaufen`
   (older than `max-alter-ms`)."
  [key {:keys [ts sig]} now-ms]
  (let [n (when (and (string? ts) (re-matches #"\d{1,15}" ts))
            (js/parseInt ts 10))]
    (cond
      (or (nil? n) (not (gleich? (signatur key ts) sig))) :ungueltig
      (< (- now-ms n) min-alter-ms)                          :zu-schnell
      (> (- now-ms n) max-alter-ms)                          :abgelaufen
      :else                                                  :ok)))

;; ---------------------------------------------------------------------------
;; Spam heuristics
;; ---------------------------------------------------------------------------

(defn honeypot? [werte]
  (not (str/blank? (:website werte))))

(defn zu-viele-links?
  "More than 2 URLs, or any BBCode `[url=` / HTML `<a ` in the message."
  [text]
  (let [t (or text "")]
    (boolean
     (or (> (count (re-seq #"(?i)https?://(?:www\.)?|\bwww\." t)) 2)
         (re-find #"(?i)\[url=|<a\s" t)))))

;; ---------------------------------------------------------------------------
;; Params → values
;; ---------------------------------------------------------------------------

(defn- text [v]
  (str/trim (str (if (sequential? v) (last v) (or v "")))))

(defn formular-werte
  "Form params (keyword keys, as macchiato's params middleware delivers
   them) → trimmed values the schema and the re-render use."
  [params]
  {:name             (text (:name params))
   :email            (text (:email params))
   :kontaktnachricht (text (:kontaktnachricht params))
   :datenschutz?     (not (str/blank? (text (:datenschutz? params))))
   :website          (text (:website params))
   :ts               (text (:ts params))
   :sig              (text (:sig params))})

(def ^:private feld-keys [:name :email :kontaktnachricht :datenschutz?])

(defn mail
  "Mail map (without `:to`) for `mail/send-from-info`."
  [{:keys [name email kontaktnachricht]}]
  {:replyTo email
   :subject (str "neue Kontaktanfrage von " name)
   :text    (str kontaktnachricht
                 "\n\n--------\ngesendet von " name " <" email ">")})

;; ---------------------------------------------------------------------------
;; Decision
;; ---------------------------------------------------------------------------

(defn pruefen
  "Pure decision over `werte` (see `formular-werte`). First match wins:
   honeypot → `{:aktion :verwerfen :grund :honeypot}`;
   token not ok → `{:aktion :formular :hinweis :erneut-senden :token s}`;
   schema errors → `{:aktion :formular :fehler #{…}}`;
   too many links → `{:aktion :verwerfen :grund :links}`;
   else `{:aktion :senden}`."
  [key now-ms werte]
  (let [token-st (token-status key werte now-ms)
        fehler   (spec/fehler-felder (select-keys werte feld-keys))]
    (cond
      (honeypot? werte)
      {:aktion :verwerfen :grund :honeypot}

      (not= :ok token-st)
      {:aktion :formular :hinweis :erneut-senden :token token-st :fehler fehler}

      (seq fehler)
      {:aktion :formular :fehler fehler}

      (zu-viele-links? (:kontaktnachricht werte))
      {:aktion :verwerfen :grund :links}

      :else
      {:aktion :senden})))

(defn respond
  "Checks `params` and acts. `deps` (all required):
   - `:schluessel` HMAC key (`schluessel` in production)
   - `:jetzt`      (fn [] now-ms)
   - `:send-mail!` (fn [mail-map] Promise) — gets `(mail werte)`
   - `:log!`       (fn [msg]) — info log line
   - `:track!`     (fn [event props]) — analytics, fire and forget
   Resolves to an outcome map (see ns doc); never rejects."
  [{:keys [schluessel jetzt send-mail! log! track!]} params]
  (let [werte   (formular-werte params)
        ergebnis (pruefen schluessel (jetzt) werte)
        formular (fn [{:keys [fehler hinweis]}]
                   {:seite   :formular
                    :werte   (select-keys werte feld-keys)
                    :fehler  (or fehler #{})
                    :hinweis hinweis})]
    (case (:aktion ergebnis)
      :verwerfen
      (do (log! (str "kontaktform: dropped as spam (" (name (:grund ergebnis))
                     ") from " (pr-str (:email werte))))
          (js/Promise.resolve {:seite :gesendet}))

      :formular
      (do (when (:token ergebnis)
            (log! (str "kontaktform: token " (name (:token ergebnis))
                       ", form re-rendered")))
          (js/Promise.resolve (formular ergebnis)))

      :senden
      (-> (js/Promise.resolve nil)
          (.then (fn [_] (send-mail! (mail werte))))
          (.then (fn [_]
                   (track! "Kontaktanfrage gesendet" {})
                   {:seite :gesendet}))
          (.catch (fn [e]
                    (log! (str "kontaktform: sending failed: " e))
                    {:seite :fehlgeschlagen}))))))
