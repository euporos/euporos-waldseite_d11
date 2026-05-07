(ns api.wohnungen-voll
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :wohnungen-voll))
