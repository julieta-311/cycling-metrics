(ns cycling-metrics.core
  (:require [org.httpkit.server :as server]
            [cycling-metrics.web :as web]))

(defn -main [& _args]
  (let [port 8080]
    (println "Starting cycling-metrics on port" port)
    (server/run-server #'web/app {:port port})))
