(ns preise.fx
  "Side effects and coeffects of the price admin. HTTP goes through
   `day8.re-frame.http-fx` (`:http-xhrio`), registered by requiring it here.
   Tests re-register these with stubs."
  (:require [day8.re-frame.http-fx]
            [kalender.core :as kal]
            [re-frame.core :as rf]))

(rf/reg-cofx ::heute
  (fn [cofx _]
    (assoc cofx :heute (kal/today-iso))))

(rf/reg-cofx ::jetzt
  (fn [cofx _]
    (assoc cofx :jetzt (.now js/Date))))

(rf/reg-cofx ::csrf-token
  (fn [cofx _]
    (assoc cofx :csrf-token
           (when (exists? js/document)
             (some-> (.getElementById js/document "ifg") (.getAttribute "token"))))))

(rf/reg-fx ::redirect
  (fn [uri]
    (set! js/window.location uri)))

(rf/reg-fx ::warn
  (fn [args]
    (apply js/console.warn args)))
