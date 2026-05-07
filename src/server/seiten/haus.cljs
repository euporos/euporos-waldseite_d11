(ns seiten.haus
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :haus))
