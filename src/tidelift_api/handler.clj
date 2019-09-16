(ns tidelift-api.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [semantic-csv.core :as sc]
            [clj-http.client :as http-client]
            [clojure.tools.logging :as log]
            [ring.middleware.gzip :as ring-middleware]))

; ------------
; SWAGGER DEFINITION
; ------------
(def swagger-definition
  {:swagger
   {:ui "/"
    :spec "/swagger.json"
    :data {:info {:title "Tidelift API"}}}})

; ------------
; SCHEMAS
; ------------
(s/defschema PackageVulnerability
  {:id s/Str
   :description s/Str
   :created s/Str})

(s/defschema PackageHealth
  {:name s/Str
   :version s/Str
   :license s/Str
   :vulnerabilities [PackageVulnerability]})

(s/defschema PackageReleases
  {:name s/Str
   :latest s/Str
   :releases [s/Str]})

; ------------
; CSV FNS
; ------------
(defn filter-csv
  "reads the CSV at the given path into a lazy sequence of rows mapped to the given headers.
   then, filters that sequence by applying the given filter_fn"
  [path headers filter_fn]
  (filter
    #(filter_fn %)
    (with-open [in-file (io/reader (io/resource path))]
      (->>
        (csv/parse-csv in-file)
        (sc/mappify {:header headers})
        doall))))

; ------------
; VULNERABILITIES
; ------------
(def vulnerabilities_csv_path "resources/database/vulnerabilities.csv")
(def vulnerabilities_csv_headers [:id :package_name :package_version :description :created])

(defn vulnerability-matches-package-name-and-version?
  [vulnerability package_name version]
  (and (= (get vulnerability :package_name) package_name)
       (= (get vulnerability :package_version) version)))

(defn find-package-vulnerabilities
  [package_name version]
  (log/debug "Searching" vulnerabilities_csv_path "for" {:package_name package_name :version version})
  (filter-csv
    vulnerabilities_csv_path
    vulnerabilities_csv_headers
    (fn [vulnerability] (vulnerability-matches-package-name-and-version? vulnerability package_name version))))

; ------------
; LICENSES
; ------------
(def licenses_csv_path "resources/database/licenses.csv")
(def licenses_csv_headers [:package_name :license])

(defn license-info-matches-package-name?
  [license_info package_name]
  (= (get license_info :package_name) package_name))

(defn find-package-license-info
  [package_name]
  (log/debug "Searching" licenses_csv_path "for" {:package_name package_name})
  (first
    (filter-csv
      licenses_csv_path
      licenses_csv_headers
      (fn [license-info] (license-info-matches-package-name? license-info package_name)))))

; ------------
; NPM REGISTRY SEARCH
; ------------
(def npm_registry_url "https://registry.npmjs.org/")
(def npm_abbreviated_json_header "application/vnd.npm.install-v1+json")
(def npm_registry_headers {:headers {:accept npm_abbreviated_json_header}
                           :throw-entire-message? true
                           :as :json})

(defn get_npm_release_info
  [package_name]
  (let [request_url (str npm_registry_url package_name)]
    (try
      (log/debug "Making GET request" {:request_url request_url :headers npm_registry_headers})
      (let [response (http-client/get request_url npm_registry_headers)]
        (log/debug "Call succeeded" {:response response})
        (response :body))
      (catch Exception e
        (log/error "Call failed" {:exception e})
        nil))))

; ------------
; PACKAGE HEALTH
; ------------
(defn format-license-info
  [license-info]
  (get license-info :license))

(defn format-vulnerabilities
  [vulnerabilities]
  (map #(select-keys % [:id :description  :created]) vulnerabilities))

(defn build-package-health
  [package_name version]
  (log/debug "Attempting to build PackageHealth for" {:package_name package_name :version version})
  (if-let [formatted-license (format-license-info (find-package-license-info package_name))]
    (if-let [formatted-vulnerabilities (format-vulnerabilities (find-package-vulnerabilities package_name version))]
      {:name package_name
       :version version
       :license formatted-license
       :vulnerabilities formatted-vulnerabilities})))

; ------------
; PACKAGE RELEASES
; ------------
(defn format-release-info
  [package_name npm_release_info]
  {:name package_name
   :latest (get-in npm_release_info [:dist-tags :latest])
   :releases  (map #((second %) :version) (get npm_release_info :versions))})

(defn build-package-releases
  [package_name]
  (log/debug "Attempting to build PackageHealth for" {:package_name package_name})
  (if-let [npm_release_info (get_npm_release_info package_name)]
    (format-release-info package_name npm_release_info)))

; ------------
; MAIN
; ------------
(def handler
  (api
    swagger-definition
    (context "/" []

      (context "/package" []

        (GET "/health/:package_name/:version" []
          :return PackageHealth
          :path-params [package_name :- s/Str
                        version :- s/Str]
          :summary "a json dictionary for that package containing information about the license and security vulnerabilities in that version of that package"
          (if-let [package_health (build-package-health package_name version)]
            (ok package_health)
            (not-found (str "There were no vulnerabilities found for package_name " package_name " version " version))))

        (GET "/releases/:package_name" []
          :return PackageReleases
          :path-params [package_name :- s/Str]
          :summary "a list of the versions available from npm (from the versions in the response from npmjs) and latest should be the most recently published version based on looking at the values of the time dictionary in the response from npmjs"
          (if-let [package_releases (build-package-releases package_name)]
            (ok package_releases)
            (not-found (str "There were no releases found for package_name " package_name))))))))

(def app
  (-> handler
      (ring-middleware/wrap-gzip)))