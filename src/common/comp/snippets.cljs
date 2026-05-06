(ns comp.snippets
  (:require
   [psite-i18n.core :refer [defsnips]]
   [comp.localization :as loc])
)

(defsnips loc/fallback
  [[past-concerts-with
    {:de "Vergangene Konzerte mit"
     :en "Past concerts with "
     :uk "Минулі концерти"}]
   [past-event
    {:de "abgeschlossen"
     :en "past event"
     :uk "минулий"}]
   [fully-booked
    {:de "ausgebucht"
     :en "fully booked"
     :uk "заброньовано"}]
   [extra-concert
    {:de "Zusatzkonzert"
     :en "Extra Concert"
     :uk "додатковий"}]
   [reserve-seats
    {:de "Reservieren"
     :en "Reserve seats"
     :uk "Бронювання"}]
   [etwas-zu-schnell
    {:de "Sie waren etwas zu schnell für die Maschine. Bitte versuchen Sie es erneut."
     :en "That was a bit too quick for the machine. Please try again."
     :uk "Це було над швидко для резервації. Спробуйте ще раз "}]

   [datenschutzerklärung
    {:de "Datenschutzerklärung"
     :en "privacy policy"
     :uk "Захист данних "}]
   [telefonnummer
    {:de "Telefonnummer"
     :en "Phone"
     :uk "Номер телефону"}]
   [name-snip
    {:de "Name"
     :en "Name"
     :uk "Ім'я "}]
   [ich-akzeptiere
    {:de "Ich akzeptiere die"
     :en "I accept the"
     :uk "Погодитися"}]
   [anzahl-personen
    {:de "Anzahl Personen"
     :en "Number of people"
     :uk "Кількість осіб"}]
   [personen
    {:de "Personen"
     :en "seats"
     :uk "осіб"}]
   [person
    {:de "Person"
     :en "seat"
     :uk "осіб"}]
   [bitte-nicht-ausfuellen
    {:de "Bitte füllen Sie dieses Feld nicht aus."
     :en "Please do not fill out this field."
     :uk "Заповніть будь ласка це поле"}]
   [bitte-email

    {:de "Bitte geben Sie ein gültige Emailadresse ein."
     :en "Please enter a valid email address."
     :uk "вкажіть свій адрес електронної пошти"}]
   [bitte-name
    {:de "Bitte geben Sie Ihren Namen ein."
     :en "Please Enter your name."
     :uk "Вкажіть своє ім'я"}]
   [additional-message

    {:de "Zusätzliche Nachricht"
     :en "Additional message"
     :uk "Додаткове повідомлення"}]
   [datenschutzregelung
    {:de "Bitte akzeptieren Sie die Datenschutzregelung."
     :en "Please accept the privacy policy."
     :uk "Будь ласка дайте згоду з правилами безпеки сайту"}]
   [leider-nicht-geklappt
    {:de "Das hat leider nicht geklappt. Bitte versuchen Sie es erneut, oder schreiben Sie uns eine E-mail an "
     :en "Something went wrong. Please try again or write an email to "
     :uk "На жаль, це не вдалося. Спробуйте ще раз або напишіть нам e-mail"}]
   [reservierung-erfolgreich
    {:de "Vielen Dank! Ihre Reservierung wurde erfolgreich registriert"
     :en "Thank you! Your reservation has been registered."
     :uk "Дякуємо! Ми зарегестрували Ваш запит"}]
   [spamfilter
    {:de "Ihre Reservierung blieb im Spamfilter hängen. Sollte das Problem weiter auftreten, können Sie die Registrierung auch per E-mail vornehmen."
     :en "Your reservation was caught in the spam filter. In case the problem persists, you can also register via email."
     :uk "Ваше бронювання залишилося у спам-фільтрі. Якщо проблема продовжується, ви також можете зареєструватися за допомогою електронної пошти."}]
   [mehrerfahren
    {:de "mehr erfahren"
     :en "learn more"
     :uk "дізнатися більше"}]
   [musiker
    {:en "Musicians"
     :de "Musiker"
     :it "Musicisti"
     :uk "Музиканти"}]
   [classical-ukrainian-music
    {:de "ukrainische klassische Musik"
     :en "ukrainian classical music"
     :uk "українська класична музика "}]

   [konzerte
    {:de "Konzerte"
     :en "Concerts"
     :uk "Концерти"}]
   [sounds-of-ukraine
    {:de "Sounds of Ukraine"
     :en "Sounds of Ukraine"
     :uk "Звуки України "}]
   [violina-petrychenko
    {:de "Violina Petrychenko"
     :en "Violina Petrychenko "
     :uk "Віоліна Петриченко"}]

   [erleben-sie-
    {:de "Erleben Sie "
     :en "Experience"
     :uk "Ви зможете почути "}]
   [-bei-folgenden-konzerten
    {:de "bei folgenden Konzerten"
     :en "at the following concerts"
     :uk "на наступних концертах "}]
   [impressum
    {:de "Impressum"
     :en "Imprint"
     :uk "Технічна інформація "}]
   [ihr-konzertbesuch
    {:de "Ihr Konzertbesuch bei Sounds of Ukraine"
     :en "Your Booking with Sounds of Ukraine"
     :uk "Ваше бронювання"}]
   [datenschutz
    {:de "Datenschutz"
     :en "Privacy"
     :uk "Захист даних"}]
   [kontakt
    {:de "Kontakt"
     :en "Contact"
     :uk "Контакти"}]

   [programm
    {:de "Programm"
     :en "Program"
     :uk "Програма"
     :it "Programma"}]

   [konzert
    {:de "Konzert"
     :en "concert"
     :uk "концерт"
     :it "concerto "}]

   [abgesagt
    {:de "fällt aus"
     :en "cancelled"
     :uk "відмінено"
     :it "annullato "}]

   [youtube-consent-text
    {:de "Dieses Video wird von YouTube gehostet. Durch Klicken auf \"Video laden\" akzeptieren Sie die "
     :en "This video is hosted by YouTube. By clicking \"Load video\" you accept the "
     :uk "Це відео розміщено на YouTube. Натискаючи \"Завантажити відео\", ви приймаєте "}]

   [google-privacy-policy
    {:de "Datenschutzerklärung von Google"
     :en "Google Privacy Policy"
     :uk "Політику конфіденційності Google"}]

   [load-video
    {:de "Video laden"
     :en "Load video"
     :uk "Завантажити відео"}]])

(defsnips loc/fallback
  [[home
    {:en "Home"
     :de "Startseite"
     :uk "Головна"
     :it "Pagina iniziale"}]

   [termine
    {:en "Calendar"
     :de "Termine"
     :it "Calendario"
     :uk "Календар"}]

   [musicians
    {:en "Musicians"
     :de "Musiker"
     :it "Musicisti"
     :uk "Музиканти"}]

   [programme
    {:en "Programs"
     :de "Programme"
     :it "Programmi"
     :uk "Програми"}]

   [cds
    {:en "CDs"
     :de "CDs"
     :it "CD"
     :uk "Диски"}]

   [galerie
    {:en "Gallery"
     :de "Galerie"
     :it "Galleria"
     :uk "Галерея"}]

   [presse
    {:en "Press"
     :de "Presse"
     :it "Pressa"
     :uk "Преса"}]

   [archiv
    {:en "Archive"
     :de "Archiv"
     :it "Archivio"
     :uk "Архів"}]])
