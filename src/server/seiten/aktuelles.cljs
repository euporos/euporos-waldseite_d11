(ns seiten.aktuelles
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :aktuelles))
