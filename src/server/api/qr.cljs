(ns api.qr
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :qr))
