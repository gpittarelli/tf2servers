(defproject tf2servers "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj" "src/cljc"]

  :test-paths ["test/clj"]

  :dependencies
  [[org.clojure/clojure "1.7.0"]
   [org.clojure/clojurescript "0.0-3058" :scope "provided"]
   [ring "1.3.2"]
   [ring/ring-defaults "0.1.4"]
   [fogus/ring-edn "0.3.0"]
   [compojure "1.3.2"]
   [enlive "1.1.6"]
   [org.omcljs/om "0.8.8"]
   [environ "1.0.0"]
   [prismatic/om-tools "0.3.11"]
   [clj-ssq "0.4.1"]
   [environ "1.0.0"]
   [overtone/at-at "1.2.0"]
   [cljs-ajax "0.3.14" :exclusions [org.clojure/clojurescript]]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-environ "1.0.0"]
            [lein-less "1.7.3"]]

  :main ^:skip-aot tf2servers.server

  :min-lein-version "2.5.0"

  :uberjar-name "tf2servers.jar"

  :cljsbuild
  {:builds {:app {:source-paths ["src/cljs" "src/cljc"]
                  :compiler {:output-to     "resources/public/js/app.js"
                             :output-dir    "resources/public/js/out"
                             :source-map    "resources/public/js/out.js.map"
                             :preamble      ["react/react.min.js"]
                             :optimizations :none
                             :pretty-print  true}}}}


  :less
  {:source-paths ["src/less"]
   :target-path "resources/public/css"}

  :profiles
  {:dev {:source-paths ["env/dev/clj"]
         :test-paths ["test/clj"]

         :dependencies [[figwheel "0.2.5"]
                        [figwheel-sidecar "0.2.5"]
                        [com.cemerick/piggieback "0.1.5"]
                        [weasel "0.6.0"]
                        [org.clojure/tools.namespace "0.2.11"]]

         :repl-options {:init-ns tf2servers.server
                        :init (require '[clojure.tools.namespace.repl
                                         :refer [refresh refresh-all]])
                        :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

         :plugins [[lein-figwheel "0.2.5"]]

         :figwheel {:http-server-root "public"
                    :server-port 3449
                    :css-dirs ["resources/public/css"]
                    :ring-handler tf2servers.server/http-handler}

         :env {:is-dev true}

         :cljsbuild
         {:test-commands
          { "test" ["phantomjs"
                    "env/test/js/unit-test.js"
                    "env/test/unit-test.html"] }
          :builds {:app {:source-paths ["env/dev/cljs"]}
                   :test {:source-paths ["src/cljc" "src/cljs" "test/cljs"]
                          :compiler
                          {:output-to     "resources/public/js/app_test.js"
                           :output-dir    "resources/public/js/test"
                           :source-map    "resources/public/js/test.js.map"
                           :preamble      ["react/react.min.js"]
                           :optimizations :whitespace
                           :pretty-print  false}}}}}

   :uberjar {:source-paths ["env/prod/clj"]
             :hooks [leiningen.cljsbuild leiningen.less]
             :env {:production true}
             :omit-source true
             :aot :all
             :main tf2servers.server
             :cljsbuild {:builds {:app
                                  {:source-paths ["env/prod/cljs"]
                                   :compiler
                                   {:optimizations :advanced
                                    :pretty-print false}}}}}})
