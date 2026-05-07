(ns api.anfrage
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :anfrage))
