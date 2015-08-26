(ns tf2servers.server
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tf2servers.dev :refer [is-dev? inject-devmode-html
            browser-repl start-figwheel start-less]]
            [compojure.core :refer [GET defroutes context]]
            [compojure.route :refer [resources not-found]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [net.cgrand.reload :refer [auto-reload]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [ring.adapter.jetty :refer [run-jetty]]
            [environ.core :refer [env]]
            [overtone.at-at :as at]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [tf2servers.game-servers :as servers])
  (:gen-class))

(def at-pool (at/mk-pool))

(defn api-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(deftemplate page (io/resource "index.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (context "/api" []
    (GET "/servers" []
      (api-response
       (->> @servers/server-list
            (map #(dissoc % :rules :players))
            (take 30))))
    (context "/stats" []
      (GET "/count" [] (api-response {:count (count @servers/server-list)}))))
  (GET "/" req (page))
  (not-found "404 Don't do that."))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (wrap-defaults #'routes api-defaults))
    (wrap-defaults routes api-defaults)))

(defn run-web-server [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (println (format "Starting web server on port %d." port))
    (run-jetty
     (-> http-handler
         wrap-edn-params)
     {:port port :join? false})))

(defn run-auto-reload [& [port]]
  (auto-reload *ns*)
  (start-figwheel)
  (start-less))

(defn stop-server []
  (at/stop-and-reset-pool! at-pool :strategy :kill)
  (reset! servers/server-list {}))

(defn run [& [port]]
  (servers/start-server-monitoring)
  (when is-dev?
    (run-auto-reload))
  (run-web-server port))

(defn -main [& [port]]
  (run port))
