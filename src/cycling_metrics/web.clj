(ns cycling-metrics.web
  (:require [reitit.ring :as ring]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [cycling-metrics.analysis :as analysis]
            [cycling-metrics.fit :as fit]
            [cheshire.core :as json]))

(defn home-handler [_request]
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
              [:label {:data-tooltip "Required to parse the ride data."} "Upload .fit file"
               [:input {:type "file" :name "file" :accept ".fit" :required true}]]
              [:label {:data-tooltip "Used to calculate Watts/kg performance metric."} "Weight (kg)"
               [:input {:type "number" :name "weight" :step "0.1" :placeholder "e.g. 70.0 (Default)" :min "30" :max "200"}]]
              [:label {:data-tooltip "Stored for future metrics (e.g., BMI)."} "Height (cm)"
               [:input {:type "number" :name "height" :step "1" :placeholder "e.g. 170 (Default)" :min "100" :max "250"}]]
              [:label {:data-tooltip "Determines the physiological standards for performance classification."} "Gender / Category"
               [:select {:name "gender"}
                [:option {:value "female" :selected true} "Female"]
                [:option {:value "male"} "Male"]
                [:option {:value "trans_mtf"} "Transgender (MTF)"]
                [:option {:value "trans_ftm"} "Transgender (FTM)"]
                [:option {:value "nonbinary_female"} "Non-Binary (Female Standards)"]
                [:option {:value "nonbinary_male"} "Non-Binary (Male Standards)"]]]]
             [:div.grid
              [:label {:data-tooltip "Defines Heart Rate Zones and validates effort intensity."} "Max Heart Rate (bpm) (Optional)"
               [:input {:type "number" :name "max_hr" :placeholder "e.g. 190"}]]
              [:label {:data-tooltip "Overrides estimation. Use if you know your true FTP."} "Manual FTP (Watts) (Optional)"
               [:input {:type "number" :name "manual_ftp" :placeholder "Override calculated FTP"}]]]
             [:button {:type "submit"} "Analyze"]]]])})

(defn parse-double-safe [s default min-val max-val]
  (try
    (let [val (if (not-empty s) (Double/parseDouble s) default)]
      (if (and (>= val min-val) (<= val max-val))
        val
        default))
    (catch Exception _ default)))

(defn upload-handler [request]
  (let [params (:params request)
        file (get params "file")
        raw-weight (get params "weight")
        raw-height (get params "height")
        weight (parse-double-safe raw-weight 70.0 20.0 300.0)
        height (parse-double-safe raw-height 170.0 100.0 250.0)
        weight-is-default? (or (empty? raw-weight) (not= weight (try (Double/parseDouble raw-weight) (catch Exception _ -1.0))))
        height-is-default? (or (empty? raw-height) (not= height (try (Double/parseDouble raw-height) (catch Exception _ -1.0))))
        
        max-hr (let [h (get params "max_hr")] (if (not-empty h) (Double/parseDouble h) nil))
        manual-ftp (let [f (get params "manual_ftp")] (if (not-empty f) (Integer/parseInt f) nil))
        gender (get params "gender")]
    (if file
      (let [temp-file (:tempfile file)
            data (fit/parse-fit temp-file)
            analysis-result (analysis/analyze-ride data {:weight weight :height height :gender gender :max-hr max-hr :manual-ftp manual-ftp})
            
            ;; Prepare data for Chart.js (Dual Axis: Time & HR)
            ordered-zones [:active-recovery :endurance :tempo :threshold :vo2-max :anaerobic :neuromuscular]
            zone-labels (map name ordered-zones)
            
            ;; Extract data series
            time-data (map (fn [z] (/ (get-in (:zone-stats analysis-result) [z :time] 0) 60.0)) ordered-zones)
            hr-data   (map (fn [z] (get-in (:zone-stats analysis-result) [z :avg-hr] 0)) ordered-zones)
            
            chart-data {:labels zone-labels
                        :datasets [{:label "Time (min)"
                                    :data time-data
                                    :backgroundColor ["#9e9e9e" "#2196f3" "#4caf50" "#ffeb3b" "#ff9800" "#f44336" "#9c27b0"]
                                    :yAxisID "y-time"
                                    :order 2}
                                   {:label "Avg HR (bpm)"
                                    :data hr-data
                                    :type "line"
                                    :borderColor "#000"
                                    :borderWidth 2
                                    :pointBackgroundColor "#000"
                                    :yAxisID "y-hr"
                                    :order 1}]}
            
            ;; Validity Check
            low-effort? (and max-hr 
                             (:lthr-est analysis-result)
                             (< (:lthr-est analysis-result) (* 0.8 max-hr)))]
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
                    [:h3 "FTP"]
                    [:h2 (str (:ftp analysis-result) " W")]
                    (when (not= (:ftp analysis-result) (:estimated-ftp analysis-result))
                      [:small "(Manually set. Estimated: " (:estimated-ftp analysis-result) " W)"])
                    (when (and low-effort? (= (:ftp analysis-result) (:estimated-ftp analysis-result)))
                      [:article {:style "background-color: #fff3cd; color: #856404; border-color: #ffeeba;"}
                       [:strong "Warning: "] "Your estimated LTHR is quite low compared to your Max HR. This suggests this ride might not have been a maximal effort, so this FTP estimate is likely underestimated."])]
                   [:div
                    [:h3 "Performance"]
                    [:p (format "%.2f W/kg (%s)" (:wkg analysis-result) (:classification analysis-result))]
                    [:small 
                     (str "Profile: " (or gender "female (default)"))
                     [:br]
                     (str "Weight: " weight " kg" (when weight-is-default? " (Default)"))
                     [:br]
                     (str "Height: " height " cm" (when height-is-default? " (Default)"))
                     [:br]
                     (str "Max HR: " (or max-hr "-"))]]
                   [:div
                     [:h3 "Est. LTHR"]
                     [:p (str (:lthr-est analysis-result) " bpm")]]]
                  
                  [:article
                   [:h3 "Time in Power Zones & Avg Heart Rate"]
                   [:canvas#zonesChart]]

                  [:div.grid
                   [:div
                    [:h4 "Power Zone Details"]
                    [:table
                     [:thead
                      [:tr [:th "Zone"] [:th "Range (Watts)"] [:th "Time"] [:th "Avg HR"]]]
                     [:tbody
                      (for [zone ordered-zones
                            :let [[min max] (get (:zones analysis-result) zone)
                                  stats (get (:zone-stats analysis-result) zone)
                                  seconds (:time stats)
                                  hr (:avg-hr stats)]]
                        [:tr [:td (name zone)] 
                             [:td (str min " - " max)]
                             [:td (format "%.1f min" (/ seconds 60.0))]
                             [:td (if (pos? hr) (str hr " bpm") "-")]])]]]
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
                             scales: {
                               'y-time': {
                                 type: 'linear',
                                 position: 'left',
                                 beginAtZero: true,
                                 title: { display: true, text: 'Time (min)' }
                               },
                               'y-hr': {
                                 type: 'linear',
                                 position: 'right',
                                 beginAtZero: false,
                                 title: { display: true, text: 'Heart Rate (bpm)' },
                                 grid: { drawOnChartArea: false }
                               }
                             },
                             plugins: {
                               legend: { display: true, position: 'bottom' }
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
