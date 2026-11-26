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
   [personen
    {:de "Personen"
     :en "people"
     :nl "personen"}]
   [person
    {:de "Person"
     :en "person"
     :nl "persoon"}]
   [bitte-email
    {:de "Bitte geben Sie ein gültige Emailadresse ein."
     :en "Please enter a valid email address."
     :nl "Voer een geldig e-mailadres in."}]
   [bitte-name
    {:de "Bitte geben Sie Ihren Namen ein."
     :en "Please Enter your name."
     :nl "Voer uw naam in."}]
   [datenschutzregelung
    {:de "Bitte akzeptieren Sie die Datenschutzregelung."
     :en "Please accept the privacy policy."
     :nl "Accepteer alstublieft de privacyverklaring."}]
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
   [nachricht-zu-lang
    {:de "Die Nachricht ist zu lang (höchstens 5000 Zeichen)."
     :en "The message is too long (5000 characters at most)."
     :nl "Het bericht is te lang (maximaal 5000 tekens)."}]
   [kontakt-erneut-senden
    {:de "Ihre Nachricht wurde noch nicht verschickt. Bitte prüfen Sie Ihre Angaben und klicken Sie erneut auf „Absenden“."
     :en "Your message has not been sent yet. Please check your details and click “Send” again."
     :nl "Uw bericht is nog niet verzonden. Controleer uw gegevens en klik nogmaals op „Verzenden”."}]
   [zu-viele-anfragen
    {:de "Zu viele Anfragen"
     :en "Too many requests"
     :nl "Te veel aanvragen"}]
   [zu-viele-anfragen-text
    {:de "Von Ihrem Anschluss kamen in kurzer Zeit zu viele Nachrichten. Bitte versuchen Sie es später erneut oder schreiben Sie uns direkt per E-Mail."
     :en "Too many messages were sent from your connection in a short time. Please try again later or write to us directly by email."
     :nl "Er zijn in korte tijd te veel berichten vanaf uw verbinding verzonden. Probeer het later opnieuw of stuur ons direct een e-mail."}]
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

;; ###########################################################################
;; Booking SPA (src/browser/buchung). Strings surfaced by the Reagent
;; single-page app: guest/pet selectors, travel-date panel, contact form,
;; price summary, validation errors and the calendar widget labels.
;; %s placeholders are filled via goog.string/format at the call site.
;; ###########################################################################

(defsnips loc/fallback
  [;; --- guest / pet selectors ---
   [erwachsener
    {:de "Erwachsener" :en "adult" :nl "volwassene"}]
   [erwachsene
    {:de "Erwachsene" :en "adults" :nl "volwassenen"}]
   [kind
    {:de "Kind" :en "child" :nl "kind"}]
   [kinder
    {:de "Kinder" :en "children" :nl "kinderen"}]
   [alter-erwachsene
    {:de "17+" :en "17+" :nl "17+"}]
   [alter-7-16
    {:de "7–16 J." :en "7–16 yrs" :nl "7–16 jr"}]
   [alter-bis-6
    {:de "bis 6 J." :en "up to 6 yrs" :nl "t/m 6 jr"}]
   [haustier
    {:de "Haustier" :en "pet" :nl "huisdier"}]
   [haustiere
    {:de "Haustiere" :en "pets" :nl "huisdieren"}]
   [ueberbelegung-warnung
    ;; %1$s = apartment name, %2$s = maximum number of people
    {:de "Bitte beachten Sie, dass die Wohnung »%s« maximal %s Personen Platz bietet."
     :en "Please note that the apartment “%s” accommodates a maximum of %s people."
     :nl "Let op: het appartement “%s” biedt plaats aan maximaal %s personen."}]

   ;; --- travel-date panel ---
   [reisedaten
    {:de "Reisedaten" :en "Travel dates" :nl "Reisdata"}]
   [belegung-nicht-geladen
    ;; %s = apartment name
    {:de "Belegungsdaten für »%s« konnten nicht geladen werden — Ihre Wunschdaten könnten u.U. nicht frei sein."
     :en "Occupancy data for “%s” could not be loaded — your desired dates may not be available."
     :nl "Bezettingsgegevens voor “%s” konden niet worden geladen — uw gewenste data zijn mogelijk niet beschikbaar."}]
   [anreise-label
    {:de "Anreise" :en "Arrival" :nl "Aankomst"}]
   [abreise-label
    {:de "Abreise" :en "Departure" :nl "Vertrek"}]
   [ab-15-uhr
    {:de " ab 15:00 Uhr" :en " from 3:00 pm" :nl " vanaf 15:00 uur"}]
   [bis-10-uhr
    {:de " bis 10:00 Uhr" :en " until 10:00 am" :nl " tot 10:00 uur"}]

   ;; --- contact form ---
   [vorname
    {:de "Vorname" :en "First name" :nl "Voornaam"}]
   [nachname
    {:de "Nachname" :en "Last name" :nl "Achternaam"}]
   [emailadresse
    {:de "Emailadresse" :en "Email address" :nl "E-mailadres"}]
   [zusatznachricht
    {:de "Zusatznachricht" :en "Additional message" :nl "Aanvullend bericht"}]
   [mietbedingungen
    {:de "Mietbedingungen" :en "rental terms" :nl "huurvoorwaarden"}]

   ;; --- price summary panel ---
   [reisedaten-frei
    {:de "Reisedaten sind frei! Stellen Sie jetzt Ihre unverbindliche Anfrage…"
     :en "Your dates are available! Send your no-obligation request now…"
     :nl "Uw data zijn beschikbaar! Doe nu uw vrijblijvende aanvraag…"}]
   [preiszeile
    ;; %1$s = number of nights, %2$s = apartment name, %3$s = total price
    {:de "%s Nächte in der Wohnung %s für voraussichtlich %s€"
     :en "%s nights in apartment %s for an estimated %s€"
     :nl "%s nachten in appartement %s voor naar verwachting %s€"}]
   [gaestebeitrag-teil
    ;; %s = guest-contribution amount
    {:de " (+ %s€ Gästebeitrag"
     :en " (+ %s€ guest contribution"
     :nl " (+ %s€ gastenbijdrage"}]
   [energieaufschlag-teil
    ;; %s = energy-surcharge amount
    {:de ", + %s€ Energieaufschlag"
     :en ", + %s€ energy surcharge"
     :nl ", + %s€ energietoeslag"}]
   [anfrage-uebermittelt
    {:de "Ihre Anfrage wurde erfolgreich übermittelt."
     :en "Your request has been submitted successfully."
     :nl "Uw aanvraag is succesvol verzonden."}]

   ;; --- submit button ---
   [erneut-versuchen
    {:de "Erneut versuchen" :en "Try again" :nl "Opnieuw proberen"}]
   [jetzt-anfragen-btn
    {:de "Jetzt anfragen!" :en "Enquire now!" :nl "Nu aanvragen!"}]

   ;; --- loading screen ---
   [lade-buchungsdaten
    {:de "Lade Buchungsdaten…"
     :en "Loading booking data…"
     :nl "Boekingsgegevens laden…"}]
   [buchungsdaten-nicht-geladen
    {:de "Die Buchungsdaten konnten nicht geladen werden."
     :en "The booking data could not be loaded."
     :nl "De boekingsgegevens konden niet worden geladen."}]

   ;; --- per-field validation (shown after a submit attempt) ---
   [err-feld-leer
    {:de "Bitte füllen Sie dieses Feld aus."
     :en "Please fill in this field."
     :nl "Vul dit veld in."}]
   [err-email-ungueltig
    {:de "Bitte geben Sie eine gültige Emailadresse an."
     :en "Please enter a valid email address."
     :nl "Vul een geldig e-mailadres in."}]
   [err-zustimmung-fehlt
    {:de "Bitte bestätigen Sie dies, um die Anfrage zu senden."
     :en "Please confirm this to send the request."
     :nl "Bevestig dit om de aanvraag te verzenden."}]
   [err-nachricht-zu-lang
    {:de "Die Nachricht ist zu lang (höchstens 2048 Zeichen)."
     :en "The message is too long (2048 characters at most)."
     :nl "Het bericht is te lang (maximaal 2048 tekens)."}]
   [err-formular-unvollstaendig
    {:de "Bitte prüfen Sie die markierten Angaben."
     :en "Please check the marked fields."
     :nl "Controleer de gemarkeerde gegevens."}]

   ;; --- server answers to a rejected request ---
   [err-server-ungueltig
    {:de "Die Anfrage wurde als unvollständig abgelehnt. Bitte prüfen Sie Ihre Angaben."
     :en "The request was rejected as incomplete. Please check your details."
     :nl "De aanvraag is als onvolledig afgewezen. Controleer uw gegevens."}]
   [err-server-allgemein
    {:de "Die Anfrage konnte nicht übermittelt werden. Bitte versuchen Sie es später erneut oder kontaktieren Sie uns direkt."
     :en "The request could not be sent. Please try again later or contact us directly."
     :nl "De aanvraag kon niet worden verzonden. Probeer het later opnieuw of neem direct contact met ons op."}]

   ;; --- date validation errors (buchung.verfuegbarkeit stay errors) ---
   [err-anreise-vor-abreise
    {:de "Das Anreisedatum muss vor dem Abreisedatum liegen."
     :en "The arrival date must be before the departure date."
     :nl "De aankomstdatum moet vóór de vertrekdatum liggen."}]
   [err-belegt
    {:de "Der gewählte Zeitraum umfasst bereits belegte Tage."
     :en "The selected period includes already-booked days."
     :nl "De gekozen periode bevat reeds bezette dagen."}]
   [err-mindestaufenthalt
    ;; %1$s = apartment name, %2$s = minimum number of nights
    {:de "Für die gewählte Saison beträgt die Mindestbuchung der Wohnung %s %s Nächte."
     :en "For the selected season, the minimum booking for apartment %s is %s nights."
     :nl "Voor het gekozen seizoen bedraagt het minimumverblijf voor appartement %s %s nachten."}]

   ;; --- calendar widget labels (buchung.views.datepicker) ---
   ;; Values are vectors; the datepicker indexes into them by weekday / month.
   [weekday-short
    {:de ["Mo" "Di" "Mi" "Do" "Fr" "Sa" "So"]
     :en ["Mon" "Tue" "Wed" "Thu" "Fri" "Sat" "Sun"]
     :nl ["ma" "di" "wo" "do" "vr" "za" "zo"]}]
   [month-long
    {:de ["Januar" "Februar" "März" "April" "Mai" "Juni"
          "Juli" "August" "September" "Oktober" "November" "Dezember"]
     :en ["January" "February" "March" "April" "May" "June"
          "July" "August" "September" "October" "November" "December"]
     :nl ["januari" "februari" "maart" "april" "mei" "juni"
          "juli" "augustus" "september" "oktober" "november" "december"]}]
   [datum-format
    ;; cljs-time.format pattern — all day-first, locale-appropriate separators.
    {:de "DD.MM.y" :en "DD/MM/y" :nl "DD-MM-y"}]])

