(ns seiten.galerie
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :galerie))
