(ns seiten.buchung
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :buchung))
