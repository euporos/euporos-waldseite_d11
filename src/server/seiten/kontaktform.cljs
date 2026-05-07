(ns seiten.kontaktform
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :api_kontakt))
