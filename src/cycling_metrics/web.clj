(ns cycling-metrics.web
  (:require [reitit.ring :as ring]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [cycling-metrics.analysis :as analysis]
            [cycling-metrics.fit :as fit]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

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
             [:div.grid
              [:label "Upload .fit file"
               [:input {:type "file" :name "file" :accept ".fit" :required true}]]
              [:label "Weight (kg)"
               [:input {:type "number" :name "weight" :step "0.1" :placeholder "e.g. 75.0"}]]
              [:label "Gender"
               [:select {:name "gender"}
                [:option {:value "male"} "Male"]
                [:option {:value "female"} "Female"]]]]
             [:div.grid
              [:label "Max Heart Rate (bpm) (Optional)"
               [:input {:type "number" :name "max_hr" :placeholder "e.g. 190"}]]]
             [:button {:type "submit"} "Analyze"]]]])})

(defn upload-handler [request]
  (let [params (:params request)
        file (get params "file")
        weight (let [w (get params "weight")] (if (not-empty w) (Double/parseDouble w) nil))
        max-hr (let [h (get params "max_hr")] (if (not-empty h) (Double/parseDouble h) nil))
        gender (get params "gender")]
    (if file
      (let [temp-file (:tempfile file)
            data (fit/parse-fit temp-file)
            analysis-result (analysis/analyze-ride data {:weight weight :gender gender :max-hr max-hr})
            ;; Prepare data for Chart.js
            zone-labels (map name (keys (:zones analysis-result)))
            zone-data (map (fn [[_ [min max]]] [min max]) (:zones analysis-result))
            chart-data {:labels zone-labels
                        :datasets [{:label "Power Range (Watts)"
                                    :data zone-data
                                    :backgroundColor ["#4caf50" "#8bc34a" "#ffeb3b" "#ff9800" "#f44336" "#e91e63" "#9c27b0"]}]}]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (html5
                [:head
                 [:title "Analysis Result"]
                 (include-css "https://cdn.jsdelivr.net/npm/@picocss/pico@1/css/pico.min.css")
                 (include-js "https://cdn.jsdelivr.net/npm/chart.js")]
                [:body
                 [:main.container
                  [:h1 "Ride Analysis"]
                  [:div.grid
                   [:div
                    [:h3 "Estimated FTP"]
                    [:h2 (str (:ftp analysis-result) " W")]]
                   [:div
                    [:h3 "Performance"]
                    [:p (format "%.2f W/kg (%s)" (:wkg analysis-result) (:classification analysis-result))]]
                   [:div
                     [:h3 "Est. LTHR"]
                     [:p (str (:lthr-est analysis-result) " bpm")]]]
                  
                  [:article
                   [:h3 "Power Training Zones"]
                   [:canvas#zonesChart]]

                  [:div.grid
                   [:div
                    [:h4 "Power Zone Details"]
                    [:table
                     [:thead
                      [:tr [:th "Zone"] [:th "Range (Watts)"]]]
                     [:tbody
                      (for [[zone [min max]] (:zones analysis-result)]
                        [:tr [:td (name zone)] [:td (str min " - " max)]])]]]
                   (when (:hr-zones analysis-result)
                     [:div
                      [:h4 "Heart Rate Zones (Max HR Based)"]
                      [:table
                       [:thead
                        [:tr [:th "Zone"] [:th "Range (bpm)"]]]
                       [:tbody
                        (for [[zone [min max]] (sort-by first (:hr-zones analysis-result))]
                          [:tr [:td (name zone)] [:td (str min " - " max)]])]]])]

                  [:a {:href "/"} "Upload another"]
                  
                  [:script
                   (str "const ctx = document.getElementById('zonesChart').getContext('2d');
                         const chartData = " (json/generate-string chart-data) ";
                         new Chart(ctx, {
                           type: 'bar',
                           data: chartData,
                           options: {
                             responsive: true,
                             indexAxis: 'y',
                             scales: {
                               x: { beginAtZero: true, title: { display: true, text: 'Watts' } }
                             },
                             plugins: {
                               legend: { display: false },
                               tooltip: {
                                 callbacks: {
                                    label: function(context) {
                                       const raw = context.raw;
                                       return raw[0] + ' - ' + raw[1] + ' W';
                                    }
                                 }
                               }
                             }
                           }
                         });")]]])})
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
