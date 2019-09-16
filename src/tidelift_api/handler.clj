(ns tidelift-api.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [semantic-csv.core :as sc]
            [clj-http.client :as http-client]))

(s/defschema Pizza
  {:name s/Str
   (s/optional-key :description) s/Str
   :size (s/enum :L :M :S)
   :origin {:country (s/enum :FI :PO)
            :city s/Str}})

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

(defn find-package-vulnerabilities
  [package_name version]
  (filter
    (fn [vulnerability]
      (and (= (vulnerability :package_name) package_name)
           (= (vulnerability :package_version) version)))
    (with-open [in-file (io/reader "/Users/maybetrinity/Desktop/moi/jobs/tidelift-api/src/tidelift_api/database/vulnerabilities.csv")]
      (->>
        (csv/parse-csv in-file)
        (sc/mappify {:header [:id :package_name :package_version :description :created]})
        doall))))

(defn find-package-license
  [package_name]
  (first
  (filter
    (fn [license_info] (= (license_info :package_name) package_name))
    (with-open [in-file (io/reader "/Users/maybetrinity/Desktop/moi/jobs/tidelift-api/src/tidelift_api/database/licenses.csv")]
      (->>
        (csv/parse-csv in-file)
        (sc/mappify {:header [:package_name :license]})
        doall)))))

(defn build-package-health
  [package_name version]
  (if-let [license (get (find-package-license package_name) :license)]
    (if-let [vulnerabilities (find-package-vulnerabilities package_name version)]
      {:name package_name
       :version version
       :license license
       :vulnerabilities (map #(select-keys % [:id :description :created]) vulnerabilities)})))

(defn build-package-releases
  [package_name]
  (let [request_url (str "https://registry.npmjs.org/" package_name)]

    (try
      ;application/vnd.npm.install-v1+json
      (let [response (http-client/get request_url {:throw-entire-message? true
                                                   :accept :json
                                                   :as :json})]

        {:name package_name
         :latest (get-in response [:body :dist-tags :latest])
         :releases  (map #((second %) :version) (get-in response [:body :versions]))})

      (catch Exception e
        (str "oh no" e)
        nil))



    )




  )

(def app
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Tidelift API"}
             :tags [{:name "api", :description "some apis"}]}}}

    (context "/" []
      :tags [""]

      (GET "/package/health/:package_name/:version" []
        :return PackageHealth
        :path-params [package_name :- s/Str, version :- s/Str]
        :summary "a json dictionary for that package containing information about the license and security vulnerabilities in that version of that package"
        (if-let [package_health (build-package-health package_name version)]
          (ok package_health)
          (not-found (str "There were no vulnerabilities found for package_name " package_name " version " version))))

      (GET "/package/releases/:package_name" []
        :return PackageReleases
        :path-params [package_name :- s/Str]
        :summary "a list of the versions available from npm (from the versions in the response from npmjs) and latest should be the most recently published version based on looking at the values of the time dictionary in the response from npmjs"
        (if-let [package_releases (build-package-releases package_name)]
          (ok package_releases)
          (not-found (str "There were no releases found for package_name " package_name))))


      )

    ))


;(def app
;  (api
;    {:swagger
;     {:ui "/"
;      :spec "/swagger.json"
;      :data {:info {:title "Tidelift-api"
;                    :description "Compojure Api example"}
;             :tags [{:name "api", :description "some apis"}]}}}
;
;    (context "/api" []
;      :tags ["api"]
;
;      (GET "/plus" []
;        :return {:result Long}
;        :query-params [x :- Long, y :- Long]
;        :summary "adds two numbers together"
;        (ok {:result (+ x y)}))
;
;      (POST "/echo" []
;        :return Pizza
;        :body [pizza Pizza]
;        :summary "echoes a Pizza"
;        (ok pizza)))))
