(ns buchung.fx
  "Side effects and coeffects of the booking SPA. HTTP goes through
   `day8.re-frame.http-fx` (`:http-xhrio`), registered by requiring it here.
   Tests re-register these with stubs."
  (:require [day8.re-frame.http-fx]
            [kalender.core :as kal]
            [re-frame.core :as rf]))

(rf/reg-cofx ::heute
  (fn [cofx _]
    (assoc cofx :heute (kal/today-iso))))

(rf/reg-cofx ::csrf-token
  (fn [cofx _]
    (assoc cofx :csrf-token
           (when (exists? js/document)
             (some-> (.getElementById js/document "ifg") (.getAttribute "token"))))))

(rf/reg-fx ::warn
  (fn [args]
    (apply js/console.warn args)))
