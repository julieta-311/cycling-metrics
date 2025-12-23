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
            
            [:article {:style "margin-bottom: 2rem; border: 1px solid #cddc39;"}
             [:header "Estimate Cycling FTP from Run"]
             [:p "Upload a running activity .fit file to estimate your Cycling FTP."]
             [:ul
               [:li "File must contain Power data (e.g. Stryd, Garmin Power)."]
               [:li "For best results, the activity should be a maximal 20-minute steady-state effort."]
               [:li "Cycling FTP is estimated at approx 80% of Running FTP due to efficiency differences."]
             ]
             [:form {:action "/upload-run" :method "post" :enctype "multipart/form-data"}
              [:div.grid
               [:label "Upload Running .fit file"
                [:input {:type "file" :name "file" :accept ".fit" :required true}]]
               [:button {:type "submit" :class "secondary"} "Estimate from Run"]]]]

            [:h2 "Analyse Cycling Activity"]
            [:p "Upload your Zwift/Cycling .fit file to analyse your performance."]
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
              [:label {:data-tooltip "Used to estimate Max HR (208 - 0.7*Age) if not provided."} "Age (Optional)"
               [:input {:type "number" :name "age" :step "1" :placeholder "e.g. 30" :min "10" :max "100"}]]
              [:label {:data-tooltip "Defines Heart Rate Zones and validates effort intensity."} "Max Heart Rate (bpm) (Optional)"
               [:input {:type "number" :name "max_hr" :placeholder "e.g. 190"}]]
              [:label {:data-tooltip "Overrides estimation. Use if you know your true FTP."} "Manual FTP (Watts) (Optional)"
               [:input {:type "number" :name "manual_ftp" :placeholder "Override calculated FTP"}]]]
             [:button {:type "submit"} "Analyse"]]]])})

(defn parse-double-safe [s default min-val max-val]
  (try
    (let [val (if (not-empty s) (Double/parseDouble s) default)]
      (if (and (>= val min-val) (<= val max-val))
        val
        default))
    (catch Exception _ default)))

(defn upload-run-handler [request]
  (let [params (:params request)
        file (get params "file")]
    (if file
      (let [temp-file (:tempfile file)
            data (fit/parse-fit temp-file)
            {:keys [ftp]} (analysis/calculate-ftp-stats (:records data))
            estimation (analysis/estimate-cycling-ftp-from-running-ftp ftp)]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (html5
                [:head
                 [:title "Running Analysis Result"]
                 (include-css "https://cdn.jsdelivr.net/npm/@picocss/pico@1/css/pico.min.css")]
                [:body
                 [:main.container
                  [:h1 "Running to Cycling FTP Estimation"]
                  (if estimation
                    [:div
                     [:div.grid
                      [:div
                       [:h3 "Calculated Running FTP"]
                       [:h2 (str (:running-ftp estimation) " W")]
                       [:small "Based on 95% of best 20min power."]]
                      [:div
                       [:h3 "Est. Cycling FTP"]
                       [:h2 (str (:cycling-ftp-est estimation) " W")]
                       [:small "Approx. 80% of Running Power (Rough Estimate)."]]]
                     [:article {:style "margin-top: 2rem;"}
                      [:header "Note on Accuracy"]
                      [:p "This is a rough estimation. Cycling efficiency is typically lower than running efficiency due to mechanical differences and muscle recruitment. For accurate results, perform a dedicated 20-minute cycling FTP test."]]]
                    [:article {:style "background-color: #fff3cd; color: #856404; border-color: #ffeeba;"}
                     [:strong "Error: "] "Could not detect sufficient power data in the uploaded file to estimate FTP. Ensure you uploaded a running activity with power metrics."])
                  [:a {:href "/"} "Back to Home"]]])})
      {:status 400
       :body "No file uploaded"})))

(defn upload-handler [request]
  (let [params (:params request)
        file (get params "file")
        raw-weight (get params "weight")
        raw-height (get params "height")
        weight (parse-double-safe raw-weight 70.0 20.0 300.0)
        height (parse-double-safe raw-height 170.0 100.0 250.0)
        weight-is-default? (or (empty? raw-weight) (not= weight (try (Double/parseDouble raw-weight) (catch Exception _ -1.0))))
        height-is-default? (or (empty? raw-height) (not= height (try (Double/parseDouble raw-height) (catch Exception _ -1.0))))
        
        raw-max-hr (get params "max_hr")
        age (let [a (get params "age")] (if (not-empty a) (Integer/parseInt a) nil))
        
        ;; Calculate Max HR: Use provided Max HR, or estimate from Age if available
        max-hr (if (not-empty raw-max-hr)
                 (Double/parseDouble raw-max-hr)
                 (analysis/estimate-max-hr age))
                 
        manual-ftp (let [f (get params "manual_ftp")] (if (not-empty f) (Integer/parseInt f) nil))
        gender (get params "gender")]
    (if file
      (let [temp-file (:tempfile file)
            data (fit/parse-fit temp-file)
            analysis-result (analysis/analyse-ride data {:weight weight :height height :gender gender :max-hr max-hr :manual-ftp manual-ftp})
            
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
                 (include-js "https://cdn.jsdelivr.net/npm/chart.js")
                 [:style "
                  body { background-color: #f0f0f0; } /* Force light grey bg for better contrast with white cards */
                  .container { max-width: 1000px; }
                  .profile-banner { background-color: #fff; padding: 0.5rem 1rem; border-radius: 4px; margin-bottom: 0.5rem; display: flex; justify-content: space-between; align-items: center; font-size: 0.9em; border-left: 5px solid #009688; color: #222; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                  .metric-card { text-align: center; padding: 0.75rem; background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 0.5rem; height: 100%; }
                  .table-card { background: white; padding: 1rem; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 0.5rem; }
                  .metric-val { font-size: 1.8rem; font-weight: bold; line-height: 1.1; color: #111; }
                  .metric-label { font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.5px; color: #444; margin-bottom: 0.1rem; font-weight: 600; }
                  .zone-table { margin-bottom: 0; }
                  .zone-table th, .zone-table td { font-size: 0.85rem; padding: 0.3rem 0.5rem; color: #111; border-color: #eee; }
                  h1 { font-size: 1.5rem; margin-bottom: 0.5rem; color: #111; margin-top: 0; }
                  h6 { margin-bottom: 0.5rem; color: #222; font-weight: 600; font-size: 1rem; }
                  article { margin-bottom: 0.5rem !important; padding: 1rem; background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                  nav { margin-bottom: 0.5rem; font-size: 0.9rem; }
                  canvas { max-height: 300px; }
                  "]]
                [:body
                 [:main.container {:style "padding-top: 1rem;"}
                  [:nav {:aria-label "breadcrumb"}
                   [:ul [:li [:a {:href "/"} "Home"]] [:li "Analysis"]]]

                  ;; 1. Profile Summary Top Block
                  [:div.profile-banner
                   [:span [:strong "Rider Profile: "] 
                    (or gender "Female") " | " 
                    weight " kg" (when weight-is-default? " (Default)") " | "
                    height " cm" (when height-is-default? " (Default)")]
                   [:span 
                    (when max-hr (str "Max HR: " max-hr " bpm"))]]
                  
                  [:h1 "Ride Analysis"]

                  ;; 2. Compact Metrics Grid
                  [:div.grid {:style "margin-bottom: 0.5rem; grid-column-gap: 0.5rem;"}
                   [:div.metric-card
                    [:div.metric-label "FTP"]
                    [:div.metric-val (str (:ftp analysis-result) " W")]
                    (when (not= (:ftp analysis-result) (:estimated-ftp analysis-result))
                      [:small "(Manual)"])
                    (when (and low-effort? (= (:ftp analysis-result) (:estimated-ftp analysis-result)))
                      [:div {:style "color: #d9534f; font-size: 0.7rem; margin-top: 0.2rem; line-height: 1.1;"} "Low Estimate (Low HR)"])]
                   
                   [:div.metric-card
                    [:div.metric-label "Performance"]
                    [:div.metric-val (format "%.2f" (:wkg analysis-result))]
                    [:small "W/kg"]
                    [:div {:style "margin-top: 0.1rem; font-weight: bold; color: #009688; font-size: 0.9rem;"} (:classification analysis-result)]]
                   
                   [:div.metric-card
                    [:div.metric-label "Threshold HR"]
                    [:div.metric-val (str (:lthr-est analysis-result))]
                    [:small "bpm (Est.)"]]]
                  
                  ;; 3. Chart Section
                  [:article
                   [:header {:style "padding: 0 0 0.5rem 0; margin-bottom: 0; border-bottom: none;"} 
                    [:h6 {:style "margin:10; color:white"} "Zone Distribution"]]
                   [:div {:style "position: relative;"}
                    [:canvas#zonesChart]]]

                  ;; 4. Tables Side-by-Side
                  [:div.grid {:style "grid-column-gap: 0.5rem;"}
                   [:div.table-card
                    [:h6 "Power Zones"]
                    [:figure {:style "margin: 0;"}
                     [:table.zone-table {:role "grid"}
                      [:thead
                       [:tr [:th "Zone"] [:th "Range (W)"] [:th "Time"]]]
                      [:tbody
                       (for [zone ordered-zones
                             :let [[min max] (get (:zones analysis-result) zone)
                                   stats (get (:zone-stats analysis-result) zone)
                                   seconds (:time stats)]]
                         [:tr [:td (name zone)] 
                              [:td (str min " - " max)]
                              [:td (format "%.1f min" (/ seconds 60.0))]])]]]]
                   (when (:hr-zones analysis-result)
                     [:div.table-card
                      [:h6 "Heart Rate Zones"]
                      [:figure {:style "margin: 0;"}
                       [:table.zone-table {:role "grid"}
                        [:thead
                         [:tr [:th "Zone"] [:th "Range (bpm)"]]]
                        [:tbody
                         (for [[zone [min max]] (sort-by first (:hr-zones analysis-result))]
                           [:tr [:td (name zone)] [:td (str min " - " max)]])]]]])]

                  [:div {:style "text-align: center; margin-top: 1rem;"}
                   [:a {:href "/" :role "button" :class "secondary outline"} "Upload Another File"]]
                  
                  [:script
                   (str "const ctx = document.getElementById('zonesChart').getContext('2d');
                         const chartData = " (json/generate-string chart-data) ";
                         new Chart(ctx, {
                           type: 'bar',
                           data: chartData,
                           options: {
                             responsive: true,
                             maintainAspectRatio: true,
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
     ["/upload" {:post upload-handler}]
     ["/upload-run" {:post upload-run-handler}]])
   (ring/routes
    (ring/create-default-handler))
   {:middleware [wrap-multipart-params]}))
