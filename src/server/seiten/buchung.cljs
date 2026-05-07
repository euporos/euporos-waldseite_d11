(ns seiten.buchung
  "Booking page: a thin shell that mounts the Reagent SPA defined in
   src/browser/buchung. The SPA fetches its data from /api/buchungsproxy
   and posts back to /api/anfrage."
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [macchiato-async.core :refer-macros [defhandler]]
            [macchiato.middleware.anti-forgery :as af]
            [seiten.templates :as templates]))

(defn- page-body [af-token]
  [:section.section
   ;; Anti-forgery token surfaced for the SPA's AJAX poster.
   ;; Same convention as the old waldseite booking app and the preise
   ;; admin SPA — the SPA reads the token off this element on submit.
   [:div.afg {:id "ifg" :token af-token}]
   ;; react-slick ships with two stylesheets. The npm package is already
   ;; in node_modules but isn't bundled, so we pull them from a CDN —
   ;; same approach the old code used.
   [:link {:rel  "stylesheet" :type "text/css" :charset "UTF-8"
           :href "https://cdnjs.cloudflare.com/ajax/libs/slick-carousel/1.8.1/slick.min.css"}]
   [:link {:rel  "stylesheet" :type "text/css"
           :href "https://cdnjs.cloudflare.com/ajax/libs/slick-carousel/1.8.1/slick-theme.min.css"}]
   [:div#mainframe]
   [:script {:src "/compiled/buchung/buchung.js"}]])

(defhandler handler [req]
  ;; Capture the anti-forgery token synchronously before any promise hop.
  ;; macchiato-async/wrap-async re-snapshots :af-token from the dynamic var
  ;; inside its own promise hop, by which time the wrap-anti-forgery
  ;; binding has been popped. See setup.directus-auth for the same trick.
  (let [af-token af/*anti-forgery-token*]
    (templates/render-page
     req
     {:titel        "Buchungsanfrage"
      :beschreibung "Buchen Sie Ihren Aufenthalt in einer unserer Ferienwohnungen"}
     (page-body af-token))))
