(ns seiten.components.dates
  (:require [psite-datetime.core :as td]))

(defn date-parts
  "Extract [day month year] from a date value. Handles goog.date.UtcDateTime
   (which the pg pool returns for date columns via cljs-time.coerce/from-date,
   shifted into UTC) by reading the original JS Date wrapped inside, and falls
   back to JS Date and ISO strings."
  [datum]
  (cond
    (nil? datum) nil
    (and (some? datum) (fn? (.-getTime datum)))
    (let [js-d (js/Date. (.getTime datum))]
      [(.getDate js-d) (inc (.getMonth js-d)) (.getFullYear js-d)])
    (string? datum)
    (let [d (js/Date. datum)]
      (when-not (js/isNaN (.getTime d))
        [(.getDate d) (inc (.getMonth d)) (.getFullYear d)]))))

(defn fmt-datum [datum locale]
  (when-let [[day month year] (date-parts datum)]
    (let [mname (td/month->string {:locale (or locale :de) :variant 1} month)]
      (str day ". " mname " " year))))
