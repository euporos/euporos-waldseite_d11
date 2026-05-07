(ns preise.core
  (:require [preise.ajaxing :as ajx]
            [preise.components :as cmp]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.ratom :as ratom]))

(defonce state
  (r/atom {:daten           {:zusatzdaten nil :saisonen nil}
           :speicherzustand :ungespeichert}))

(defonce daten            (r/cursor state [:daten]))
(defonce saisonen         (r/cursor state [:daten :saisonen]))
(defonce speicherzustand  (r/cursor state [:speicherzustand]))
(defonce allgdaten        (r/cursor state [:daten :allgdaten]))
(defonce basisdaten       (r/cursor state [:daten :basisdaten]))
(defonce wohnungen        (r/atom nil))

(def sorted-saisonen
  (ratom/reaction (sort-by :beginn @saisonen)))

(def loading-screen
  [:div.loadscreen
   [:div.lds-dual-ring__msg "Lade Preisdaten"]
   [:div.lds-dual-ring [:div]]])

(defn alles []
  (fn []
    [:div.panel-group.panelgruppe
     [:div.panel.panel-success
      [:div.panel-heading "Allgemeine Daten"]
      [cmp/einzelfelder allgdaten]]
     [:div.panel.panel-success
      [:div.panel-heading "Basisdaten"]
      [cmp/basisfelder basisdaten wohnungen]]
     [:div.panel.panel-success
      [:div.panel-heading "Saisonale Daten"]
      [cmp/saisonale-daten saisonen @wohnungen]]
     [:div.panel.panel-success
      [:div.panel-heading "Aktionen für ausgewählte Saisonen"]
      [cmp/special-actions-table saisonen cmp/special-actions]]
     [:br]
     [cmp/speicherbericht
      @speicherzustand
      #(ajx/post-preise! speicherzustand @daten)]]))

(defn ^:dev/after-load start []
  (rdom/render [alles] (js/document.getElementById "mainframe")))

(defn ^:export main []
  (rdom/render loading-screen (js/document.getElementById "mainframe"))
  (ajx/get-data wohnungen daten start))
