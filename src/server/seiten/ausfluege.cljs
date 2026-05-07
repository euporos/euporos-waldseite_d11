(ns seiten.ausfluege
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :ausfluege))
