(ns api.buchungsproxy
  (:require [serving.stub :as stub]))

(def handler (stub/make-handler :buchungsproxy))
