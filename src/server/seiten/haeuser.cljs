(ns seiten.haeuser
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :haeuser))
