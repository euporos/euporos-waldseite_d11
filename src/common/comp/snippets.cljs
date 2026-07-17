(ns comp.snippets
  (:require
   [psite-i18n.core :refer [defsnips]]
   [comp.localization :as loc])
)

(defsnips loc/fallback
  [[reserve-seats
    {:de "Reservieren"
     :en "Reserve"
     :nl "Reserveren"}]
   [past-event
    {:de "abgeschlossen"
     :en "past event"
     :nl "afgelopen"}]
   [fully-booked
    {:de "ausgebucht"
     :en "fully booked"
     :nl "volgeboekt"}]
   [extra-concert
    {:de "Zusatzkonzert"
     :en "Extra Concert"
     :nl "Extra concert"}]
   [etwas-zu-schnell
    {:de "Sie waren etwas zu schnell für die Maschine. Bitte versuchen Sie es erneut."
     :en "That was a bit too quick for the machine. Please try again."
     :nl "Dat ging net iets te snel voor de machine. Probeer het opnieuw."}]

   [datenschutzerklärung
    {:de "Datenschutzerklärung"
     :en "privacy policy"
     :nl "privacyverklaring"}]
   [telefonnummer
    {:de "Telefonnummer"
     :en "Phone"
     :nl "Telefoonnummer"}]
   [name-snip
    {:de "Name"
     :en "Name"
     :nl "Naam"}]
   [ich-akzeptiere
    {:de "Ich akzeptiere die"
     :en "I accept the"
     :nl "Ik accepteer de"}]
   [anzahl-personen
    {:de "Anzahl Personen"
     :en "Number of people"
     :nl "Aantal personen"}]
   [personen
    {:de "Personen"
     :en "people"
     :nl "personen"}]
   [person
    {:de "Person"
     :en "person"
     :nl "persoon"}]
   [bitte-nicht-ausfuellen
    {:de "Bitte füllen Sie dieses Feld nicht aus."
     :en "Please do not fill out this field."
     :nl "Vul dit veld alstublieft niet in."}]
   [bitte-email
    {:de "Bitte geben Sie ein gültige Emailadresse ein."
     :en "Please enter a valid email address."
     :nl "Voer een geldig e-mailadres in."}]
   [bitte-name
    {:de "Bitte geben Sie Ihren Namen ein."
     :en "Please Enter your name."
     :nl "Voer uw naam in."}]
   [additional-message
    {:de "Zusätzliche Nachricht"
     :en "Additional message"
     :nl "Aanvullend bericht"}]
   [datenschutzregelung
    {:de "Bitte akzeptieren Sie die Datenschutzregelung."
     :en "Please accept the privacy policy."
     :nl "Accepteer alstublieft de privacyverklaring."}]
   [leider-nicht-geklappt
    {:de "Das hat leider nicht geklappt. Bitte versuchen Sie es erneut, oder schreiben Sie uns eine E-mail an "
     :en "Something went wrong. Please try again or write an email to "
     :nl "Dat is helaas niet gelukt. Probeer het opnieuw of stuur ons een e-mail naar "}]
   [reservierung-erfolgreich
    {:de "Vielen Dank! Ihre Reservierung wurde erfolgreich registriert"
     :en "Thank you! Your reservation has been registered."
     :nl "Hartelijk dank! Uw reservering is succesvol geregistreerd."}]
   [spamfilter
    {:de "Ihre Reservierung blieb im Spamfilter hängen. Sollte das Problem weiter auftreten, können Sie die Registrierung auch per E-mail vornehmen."
     :en "Your reservation was caught in the spam filter. In case the problem persists, you can also register via email."
     :nl "Uw reservering bleef in het spamfilter hangen. Als het probleem zich blijft voordoen, kunt u de registratie ook per e-mail doen."}]
   [mehrerfahren
    {:de "mehr erfahren"
     :en "learn more"
     :nl "meer informatie"}]])
