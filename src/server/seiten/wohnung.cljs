(ns seiten.wohnung
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :wohnung))
