(ns cycling-metrics.web
  (:require [reitit.ring :as ring]
            [hiccup.page :refer [html5 include-css]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [cycling-metrics.analysis :as analysis]
            [cycling-metrics.fit :as fit]
            [clojure.java.io :as io]))

(defn home-handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (html5
          [:head
           [:title "Cycling Metrics"]
           (include-css "https://cdn.jsdelivr.net/npm/@picocss/pico@1/css/pico.min.css")]
          [:body
           [:main.container
            [:h1 "Cycling Metrics"]
            [:p "Upload your Zwift .fit file to analyze your performance."]
            [:form {:action "/upload" :method "post" :enctype "multipart/form-data"}
             [:input {:type "file" :name "file" :accept ".fit"}]
             [:button {:type "submit"} "Analyze"]]]])})

(defn upload-handler [request]
  (let [file (get-in request [:params "file"])]
    (if file
      (let [temp-file (:tempfile file)
            data (fit/parse-fit temp-file)
            analysis (analysis/analyze-ride data)]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (html5
                [:head
                 [:title "Analysis Result"]
                 (include-css "https://cdn.jsdelivr.net/npm/@picocss/pico@1/css/pico.min.css")]
                [:body
                 [:main.container
                  [:h1 "Ride Analysis"]
                  [:h3 "Estimated FTP: " (:ftp analysis) " W"]
                  [:h3 "Training Zones"]
                  [:ul
                   (for [[zone range] (:zones analysis)]
                     [:li (name zone) ": " (first range) " - " (second range) " W"])]
                  [:a {:href "/"} "Upload another"]]])})
      {:status 400
       :body "No file uploaded"})))

(def app
  (ring/ring-handler
   (ring/router
    [["/" {:get home-handler}]
     ["/upload" {:post upload-handler}]])
   (ring/routes
    (ring/create-default-handler))
   {:middleware [wrap-multipart-params]}))
