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

;; ###########################################################################
;; Page headings, labels and meta descriptions (hard-coded site chrome).
;; ###########################################################################

(defsnips loc/fallback
  [;; --- section headings on haus / wohnung ---
   [ausstattung
    {:de "Ausstattung" :en "Amenities" :nl "Voorzieningen"}]
   [das-bietet
    ;; heading template — %s is replaced with the house name
    {:de "Das bietet %s:"
     :en "What %s offers:"
     :nl "Dit biedt %s:"}]
   [wohnungen
    {:de "Wohnungen" :en "Apartments" :nl "Appartementen"}]
   [wohnung
    {:de "Wohnung" :en "Apartment" :nl "Appartement"}]
   [anreise
    {:de "Anreise" :en "Getting here" :nl "Bereikbaarheid"}]
   [ausflugtips
    {:de "Ausflugtips" :en "Excursions" :nl "Uitstapjes"}]
   [gaestestimmen
    {:de "Gästestimmen" :en "Guest reviews" :nl "Gastenbeoordelingen"}]
   [jahre
    {:de "Jahre" :en "years" :nl "jaar"}]
   [buchungsanfrage
    {:de "Buchungsanfrage" :en "Booking request" :nl "Reserveringsaanvraag"}]
   [jetzt-anfragen
    {:de "Jetzt Anfragen" :en "Enquire now" :nl "Nu aanvragen"}]
   [ferienhaeuser
    {:de "Ferienhäuser" :en "Holiday homes" :nl "Vakantiehuizen"}]

   ;; --- wohnung price/amenities table ---
   [platz-fuer-bis-zu
    {:de "Platz für bis zu" :en "Space for up to" :nl "Plaats voor maximaal"}]
   [preise-ab
    {:de "Preise ab" :en "Prices from" :nl "Prijzen vanaf"}]
   [eur-pro-nacht
    {:de "€/Nacht" :en "€/night" :nl "€/nacht"}]
   [eur-pro-woche
    {:de "€/Woche" :en "€/week" :nl "€/week"}]
   [mindestaufenthalt
    {:de "Mindestaufenthalt" :en "Minimum stay" :nl "Minimumverblijf"}]
   [ab
    {:de "ab" :en "from" :nl "vanaf"}]
   [naechte
    {:de "Nächte" :en "nights" :nl "nachten"}]
   [dtv-sterne
    {:de "DTV-Sterne" :en "DTV stars" :nl "DTV-sterren"}]

   ;; --- haeuser overview ---
   [unsere-ferienhaeuser
    {:de "Unsere Ferienhäuser" :en "Our holiday homes" :nl "Onze vakantiehuizen"}]
   [haeuser-meta-desc
    {:de "Übersicht unserer Ferienhäuser im Bayerischen Wald."
     :en "Overview of our holiday homes in the Bavarian Forest."
     :nl "Overzicht van onze vakantiehuizen in het Beierse Woud."}]

   ;; --- buchung ---
   [buchung-meta-desc
    {:de "Buchen Sie Ihren Aufenthalt in einer unserer Ferienwohnungen"
     :en "Book your stay in one of our holiday apartments"
     :nl "Boek uw verblijf in een van onze vakantieappartementen"}]

   ;; --- kontakt page + form ---
   [kontakt
    {:de "Kontakt" :en "Contact" :nl "Contact"}]
   [email
    {:de "Email" :en "Email" :nl "E-mail"}]
   [nachricht
    {:de "Nachricht" :en "Message" :nl "Bericht"}]
   [ihre-nachricht
    {:de "Ihre Nachricht" :en "Your message" :nl "Uw bericht"}]
   [ich-habe-die
    {:de "Ich habe die "
     :en "I have read and accepted the "
     :nl "Ik heb de "}]
   [gelesen-akzeptiert
    {:de " gelesen und akzeptiert."
     :en "."
     :nl " gelezen en geaccepteerd."}]
   [absenden
    {:de "Absenden" :en "Send" :nl "Verzenden"}]
   [kontakt-meta-desc
    {:de "Bei Fragen rund um unsere Ferienwohnungen können Sie uns hier kontaktieren."
     :en "Questions about our holiday apartments? Get in touch with us here."
     :nl "Vragen over onze vakantieappartementen? Neem hier contact met ons op."}]

   ;; --- kontaktform result pages + validation ---
   [nachricht-leer
    {:de "Nachrichtenfeld ist leer."
     :en "The message field is empty."
     :nl "Het berichtveld is leeg."}]
   [spam-erkannt
    {:de "Nachricht als Spam erkannt"
     :en "Message flagged as spam"
     :nl "Bericht als spam gemarkeerd"}]
   [kein-bot
    {:de "Falls Sie kein Bot sind, schreiben Sie uns bitte direkt."
     :en "If you are not a bot, please write to us directly."
     :nl "Als u geen bot bent, schrijf ons dan rechtstreeks."}]
   [fehler-versand
    {:de "Fehler beim Versenden der Nachricht"
     :en "Error sending the message"
     :nl "Fout bij het verzenden van het bericht"}]
   [nachricht-verschickt
    {:de "Nachricht verschickt"
     :en "Message sent"
     :nl "Bericht verzonden"}]
   [dank-nachricht
    {:de "Vielen Dank für Ihre Nachricht! "
     :en "Thank you for your message! "
     :nl "Hartelijk dank voor uw bericht! "}]
   [antwort-kuerze
    {:de "Sie erhalten in Kürze eine Antwort."
     :en "You will receive a reply shortly."
     :nl "U ontvangt binnenkort een antwoord."}]
   [versand-fehlgeschlagen
    {:de "Versand fehlgeschlagen"
     :en "Sending failed"
     :nl "Verzending mislukt"}]
   [versand-fehler-text
    {:de "Leider ist beim Versenden Ihrer Nachricht ein Fehler aufgetreten."
     :en "Unfortunately, an error occurred while sending your message."
     :nl "Helaas is er een fout opgetreden bij het verzenden van uw bericht."}]

   ;; --- galerie ---
   [galerie
    {:de "Galerie" :en "Gallery" :nl "Galerij"}]
   [galerie-meta-desc
    {:de "Bildergalerie unserer Ferienhäuser im Bayerischen Wald."
     :en "Photo gallery of our holiday homes in the Bavarian Forest."
     :nl "Fotogalerij van onze vakantiehuizen in het Beierse Woud."}]

   ;; --- ausfluege ---
   [fuer
    {:de " für " :en " for " :nl " voor "}]
   [ausfluege-meta-desc
    {:de "Ausflugtips rund um den Bayerischen Wald."
     :en "Excursion tips around the Bavarian Forest."
     :nl "Uitstaptips rondom het Beierse Woud."}]

   ;; --- error pages ---
   [zurueck-startseite
    {:de "Zurück zur Startseite"
     :en "Back to home"
     :nl "Terug naar de startpagina"}]

   ;; --- home ---
   [herzlich-willkommen
    {:de "Herzlich willkommen" :en "Welcome" :nl "Hartelijk welkom"}]
   [in-unseren-ferienhaeusern
    {:de "in unseren Ferienhäusern…"
     :en "to our holiday homes…"
     :nl "in onze vakantiehuizen…"}]
   [wo-am-schoensten
    {:de "…wo der Bayerische Wald am schönsten ist."
     :en "…where the Bavarian Forest is at its most beautiful."
     :nl "…waar het Beierse Woud op zijn mooist is."}]
   [familie-bickel
    {:de "Ihre Familie Bickel"
     :en "Your Bickel family"
     :nl "Uw familie Bickel"}]
   [home-meta-desc
    {:de "Ferienwohnungen in Falkenstein, Bayerischer Wald."
     :en "Holiday apartments in Falkenstein, Bavarian Forest."
     :nl "Vakantieappartementen in Falkenstein, Beierse Woud."}]

   ;; --- aktuelles ---
   [aktuelles
    {:de "Aktuelles" :en "News" :nl "Nieuws"}]
   [aktuelles-meta-desc
    {:de "Neuigkeiten von Bickels Ferienwohnungen im Bayerischen Wald."
     :en "News from Bickels holiday apartments in the Bavarian Forest."
     :nl "Nieuws van Bickels vakantieappartementen in het Beierse Woud."}]])

