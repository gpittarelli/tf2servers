(ns tf2servers.game-servers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tf2servers.dev :refer [is-dev?]]
            [environ.core :refer [env]]
            [overtone.at-at :as at]
            [clj-ssq.core :as ssq])
  (:import [java.net InetAddress UnknownHostException]))

(def server-list (atom #{}))

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
  (->> servers
       (map (fn [{:keys [ip port] :as server}]
              (assoc server
                     :info (ssq/info ip port)
;;;;                     :players (ssq/players ip port)
;;;;                     :rules (ssq/rules ip port)
                     )))
       (map #(reduce
              (fn [data key] (update-in data [key] deref))
              %
              [:info]))
       (remove
        (fn [server]
          (contains? (:info server) :err)))
       (reset! server-list)))

(defn load-server-list []
  (->> (env "TF2SERVERS_LIST")
       (#(or % "server-list.txt"))
       io/reader
       line-seq
       (map str/trim)
       (filter valid-server-str?)
       (map str-to-host-port)
       (remove nil?)
       set))

(defn start-server-monitoring []
  (update-server-list (load-server-list)))
