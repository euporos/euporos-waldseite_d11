(ns buchung.datechecking
  "Validates a guest's selected stay against the apartment's blocked-date
   list and the season's minimum-stay rules. Returns a German error
   string (truthy) or nil (no problem)."
  (:require [cljs-time.core :as t]
            [buchung.utils :as u]))

(defn err-anreise-vor-abreise? [anreise abreise]
  (when-not (t/before? anreise abreise)
    "Das Anreisedatum muss vor dem Abreisedatum liegen."))

(defn err-contains-blocked? [aufenthalt whg-id wohnungen]
  (let [belegung (u/get-by-id wohnungen whg-id :belegung)]
    (when (and aufenthalt
               (some (fn [{:keys [dtstart dtend]}]
                       ;; Abreisetage dürfen Anreisetage des nächsten Gasts sein
                       (t/overlap aufenthalt
                                  (t/interval dtstart (t/minus dtend (t/days 1)))))
                     belegung))
      "Der gewählte Zeitraum umfasst bereits belegte Tage.")))

(defn- saisons-overlapping
  "Per-apartment fields of every season whose interval overlaps the stay."
  [whg-id aufenthalt saisonen]
  (when aufenthalt
    (->> saisonen
         (filter (fn [{:keys [beginn ende]}]
                   (t/overlaps? aufenthalt (t/interval beginn ende))))
         (map #(get (:felder %) whg-id)))))

(defn err-mindestaufenthalt? [whg-id aufenthalt preise wohnungen]
  (let [saisonal-mins (->> (saisons-overlapping whg-id aufenthalt (:saisonen preise))
                           (keep #(some-> (:mindestaufenthalt %) js/parseInt))
                           seq)
        standard-min  (some-> (get-in preise [:basisdaten whg-id :mindestaufenthalt_standard])
                              js/parseInt)
        min-required  (or (when saisonal-mins (apply min saisonal-mins))
                          standard-min)
        whg-name      (u/get-by-id wohnungen whg-id :name)]
    (when (and aufenthalt min-required
               (< (t/in-days aufenthalt) min-required))
      (str "Für die gewählte Saison beträgt die Mindestbuchung der Wohnung "
           whg-name " " min-required " Nächte."))))

(defn err-dates-bad?
  "Composes the three checks; returns the first error message or nil."
  [anreise abreise aufenthalt whg-id wohnungen preise]
  (when (and anreise abreise)
    (or (err-anreise-vor-abreise? anreise abreise)
        (err-contains-blocked? aufenthalt whg-id wohnungen)
        (err-mindestaufenthalt? whg-id aufenthalt preise wohnungen))))

(defn anzeige
  "Renders the error banner if `err-msg` is truthy, otherwise the
   success component (or nothing)."
  [err-msg success-comp]
  (cond
    err-msg
    [:div.notification.is-danger.belegungswarnung.has-text-centered err-msg]
    success-comp
    success-comp))
