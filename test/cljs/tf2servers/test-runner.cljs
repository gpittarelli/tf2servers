(ns tf2servers.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [tf2servers.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'tf2servers.core-test))
    0
    1))
