(ns tf2servers.server
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tf2servers.dev :refer [is-dev? inject-devmode-html
            browser-repl start-figwheel start-less]]
            [compojure.core :refer [GET defroutes context]]
            [compojure.route :refer [resources]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [net.cgrand.reload :refer [auto-reload]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [environ.core :refer [env]]
            [overtone.at-at :as at]
            [clj-ssq.core :as ssq]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]])
  (:import [java.net InetAddress UnknownHostException])
  (:gen-class))

(def at-pool (at/mk-pool))
(def server-list (atom #{}))

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
      (api-response @server-list)))
  (GET "/*" req (page)))

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

(defn- valid-server-str? [s]
  (not (or (empty? s) (= (first s) \#))))

(defn host->ip [host]
  (try
    (.getHostAddress (first (InetAddress/getAllByName host)))
    (catch UnknownHostException err
      (.println *err* (str "Could not resolve: " host)))))

(defn- str-to-host-port [s]
  (let [[host port] (str/split s #":")
        host (host->ip host)
        port (Integer/parseInt (or port "27015"))]
    (when host {:ip host :port port})))

(defn- update-server-list [servers]
  (println "UPDATE" servers)
  (->> servers
       (map (fn [{:keys [ip port] :as server}]
              (assoc server
                     :info (ssq/info ip port)
                     :players (ssq/players ip port)
                     :rules (ssq/rules ip port))))
       (map #(reduce
              (fn [data key] (update-in data [key] deref))
              %
              [:info :players :rules]))
       (reset! server-list)))

(defn start-server-monitoring []
  (let [servers
        (->> (env "TF2SERVERS_LIST")
             (#(or % "server-list.txt"))
             io/reader
             line-seq
             (map str/trim)
             (filter valid-server-str?)
             (map str-to-host-port)
             (remove nil?)
             set)]
    (println "Server list:" (count servers))
    (update-server-list servers)
    (println "Server list updated" @server-list)
    (at/every 60000 #(update-server-list servers) at-pool)))

(defn stop-server []
  (at/stop-and-reset-pool! at-pool :strategy :kill)
  (reset! server-list {}))

(defn run [& [port]]
  (start-server-monitoring)
  (when is-dev?
    (run-auto-reload))
  (run-web-server port))

(defn -main [& [port]]
  (run port))
