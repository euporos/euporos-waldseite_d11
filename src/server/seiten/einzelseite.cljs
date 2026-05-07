(ns seiten.einzelseite
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :einzelseite))
