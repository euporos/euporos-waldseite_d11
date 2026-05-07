(ns seiten.routes
  {:clj-kondo/config {:linters {:unresolved-namespace {:level :off}}}}
  #?(:node (:require
            [seiten.admin :as admin]
            [seiten.aktuelles :as aktuelles]
            [seiten.ausfluege :as ausfluege]
            [seiten.buchung :as buchung]
            [seiten.einzelseite :as einzelseite]
            [seiten.galerie :as galerie]
            [seiten.haeuser :as haeuser]
            [seiten.haus :as haus]
            [seiten.home :as home]
            [seiten.kontakt :as kontakt]
            [seiten.kontaktform :as kontaktform]
            [seiten.wohnung :as wohnung]
            [setup.directus-auth :as directus-auth]))
  #?(:clj  (:require      [psite-routing.macros :as prm])
     :cljs (:require-macros [psite-routing.macros :as prm])))

(def routes
  (#?(:node identity :default prm/routes-reduced-for-matching)
   [["/" {:name    :home
          :handler home/handler}]

    ["/haeuser"   {:name :haeuser   :handler haeuser/handler}]
    ["/galerie"   {:name :galerie   :handler galerie/handler}]
    ["/h/{hausid}-{hausbez}"
     {:name       :haus
      :handler    haus/handler
      :parameters {:path [:map [:hausid :int] [:hausbez :string]]}}]
    ["/w/{wohnungsid}-{wohnungsbez}"
     {:name       :wohnung
      :handler    wohnung/handler
      :parameters {:path [:map [:wohnungsid :int] [:wohnungsbez :string]]}}]
    ["/buchung"   {:name :buchung   :handler buchung/handler}]
    ["/aktuelles" {:name :aktuelles :handler aktuelles/handler}]
    ["/ausfluege/:hausid"
     {:name       :ausfluege
      :handler    ausfluege/handler
      :parameters {:path [:map [:hausid :int]]}}]
    ["/kontakt"     {:name :kontakt     :handler kontakt/handler}]
    ["/kontaktform" {:name :api_kontakt :handler kontaktform/handler}]

    ["/admin" {:middleware [directus-auth/wrap-directus-user]}
     ["" {:handler admin/handler
          :name    :admin}]]

    ;; Catch-all einzelseite must come last so static names match first.
    ["/{einzelseitid}-{einzelseitbez}"
     {:name       :einzelseite
      :handler    einzelseite/handler
      :parameters {:path [:map [:einzelseitid :int] [:einzelseitbez :string]]}}]]))
