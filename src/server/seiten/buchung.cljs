(ns seiten.buchung
  "Booking page: a thin shell that mounts the Reagent SPA defined in
   src/browser/buchung. The SPA fetches its data from /api/buchungsproxy
   and posts back to /api/anfrage."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.middleware.anti-forgery :as af]
            [seiten.templates :as templates]))

(def ^:private slick-css
  ;; slick-carousel CSS bundled by vite (see js-src/buchung.js). Loading
  ;; from cdnjs would fail under our 'self'-only CSP for style-src.
  ;; Wrapped in a list because templates.cljs splices :into-head onto
  ;; the head vector via `into`.
  (list [:link {:rel  "stylesheet" :type "text/css"
                :href "/compiled/bundle/buchung.css"}]))

(defn- page-body [af-token]
  [:section.section
   ;; Anti-forgery token surfaced for the SPA's AJAX poster — the SPA
   ;; reads the token off this element on submit.
   [:div.afg {:id "ifg" :token af-token}]
   [:div#mainframe]])

(defhandler handler [req]
  ;; Capture the anti-forgery token synchronously before any promise hop.
  ;; macchiato-async/wrap-async re-snapshots :af-token from the dynamic var
  ;; inside its own promise hop, by which time the wrap-anti-forgery
  ;; binding has been popped. See setup.directus-auth for the same trick.
  (let [af-token af/*anti-forgery-token*]
    (templates/render-page
     req
     {:titel        "Buchungsanfrage"
      :beschreibung "Buchen Sie Ihren Aufenthalt in einer unserer Ferienwohnungen"
      :into-head    slick-css
      ;; templates/blank_hiccup composes the app.js script tag with
      ;; onload = "<onload-string>(app.core.readarg(<arg>))". Setting
      ;; onload to the SPA entry name turns that into
      ;; "buchung.core.main(app.core.readarg(...))", which fires our
      ;; SPA after app.js has booted (so SHADOW_ENV is in place).
      :cljs         {:onload "buchung.core.main"}}
     (page-body af-token))))
