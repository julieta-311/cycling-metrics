(ns cycling-metrics.core
  (:require [org.httpkit.server :as server]
            [reitit.ring :as ring]
            [hiccup.core :as h]
            [hiccup.page :refer [html5 include-css]]
            [cycling-metrics.web :as web]
            [clojure.java.io :as io]))

(defn -main [& args]
  (let [port 8080]
    (println "Starting cycling-metrics on port" port)
    (server/run-server #'web/app {:port port})))
