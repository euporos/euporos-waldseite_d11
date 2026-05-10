(ns api.sitemap
  {:clj-kondo/config '{:lint-as {macchiato-async.core/defhandler clojure.core/defn}}}
  (:require [clojure.string :as str]
            [kitchen-async.promise :as p]
            [macchiato-async.core :refer-macros [defhandler]]
            [psite-routing.core :as routing]
            [db.setup :as db]
            [db.queries :as q]
            [serving.routing :as rt]))

(def ^:private locales [:de :en :nl])
(def ^:private default-locale :de)

(defn- xml-escape [s]
  (-> (str s)
      (str/replace "&"  "&amp;")
      (str/replace "<"  "&lt;")
      (str/replace ">"  "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'"  "&apos;")))

(defn- with-locale [req locale]
  (assoc req :locale locale))

(defn- url-for
  "Returns a map of {locale absolute-url} for the given route reverser fn,
   which must accept a (locale-set) request and return a relative path."
  [req reverser]
  (into {}
        (for [loc locales]
          [loc (routing/make-path-absolute req (reverser (with-locale req loc)))])))

(defn- static-route [name]
  (fn [req] (routing/reverse-match req name {})))

(defn- haus-route [{:keys [id name]}]
  (fn [req] (rt/path-haus req id name)))

(defn- wohnung-route [{:keys [id name]}]
  (fn [req] (rt/path-wohnung req id name)))

(defn- einzelseite-route [{:keys [id titel]}]
  (fn [req] (rt/path-einzelseite req id titel)))

(defn- url-elem [urls-by-locale]
  (let [primary (get urls-by-locale default-locale)]
    (str "  <url>\n"
         "    <loc>" (xml-escape primary) "</loc>\n"
         (str/join
          (for [loc locales]
            (str "    <xhtml:link rel=\"alternate\" hreflang=\""
                 (name loc) "\" href=\""
                 (xml-escape (get urls-by-locale loc))
                 "\"/>\n")))
         "  </url>\n")))

(defn- build-xml [req haeuser wohnungen einzelseiten]
  (let [static-routes [:home :haeuser :galerie :buchung :aktuelles :kontakt]
        reversers     (concat (map static-route static-routes)
                              (map haus-route haeuser)
                              (map wohnung-route wohnungen)
                              (map einzelseite-route einzelseiten))]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\""
         " xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n"
         (str/join (map (comp url-elem (partial url-for req)) reversers))
         "</urlset>\n")))

(defhandler handler [req]
  (p/let [haeuser      (db/query (q/haeuser-overview default-locale))
          wohnungen    (db/query (q/wohnungen-with-ical))
          einzelseiten (db/query (q/einzelseiten-for-sitemap default-locale))]
    {:status  200
     :headers {"Content-Type" "application/xml; charset=utf-8"}
     :body    (build-xml req haeuser wohnungen einzelseiten)}))
