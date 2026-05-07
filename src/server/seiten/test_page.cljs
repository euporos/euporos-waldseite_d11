(ns seiten.test-page
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :test))
