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
              [:label "Gender / Category"
               [:select {:name "gender"}
                [:option {:value "female" :selected true} "Female"]
                [:option {:value "male"} "Male"]
                [:option {:value "trans_mtf"} "Transgender (MTF)"]
                [:option {:value "trans_ftm"} "Transgender (FTM)"]
                [:option {:value "nonbinary_female"} "Non-Binary (Female Standards)"]
                [:option {:value "nonbinary_male"} "Non-Binary (Male Standards)"]]]]
             [:div.grid
              [:label "Max Heart Rate (bpm) (Optional)"
               [:input {:type "number" :name "max_hr" :placeholder "e.g. 190"}]]
              [:label "Manual FTP (Watts) (Optional)"
               [:input {:type "number" :name "manual_ftp" :placeholder "Override calculated FTP"}]]]
             [:button {:type "submit"} "Analyze"]]]])})

(defn upload-handler [request]
  (let [params (:params request)
        file (get params "file")
        weight (let [w (get params "weight")] (if (not-empty w) (Double/parseDouble w) nil))
        max-hr (let [h (get params "max_hr")] (if (not-empty h) (Double/parseDouble h) nil))
        manual-ftp (let [f (get params "manual_ftp")] (if (not-empty f) (Integer/parseInt f) nil))
        gender (get params "gender")]
    (if file
      (let [temp-file (:tempfile file)
            data (fit/parse-fit temp-file)
            analysis-result (analysis/analyze-ride data {:weight weight :gender gender :max-hr max-hr :manual-ftp manual-ftp})
            
            ;; Prepare data for Chart.js (Time in Zone)
            ;; Order zones from easy to hard
            ordered-zones [:active-recovery :endurance :tempo :threshold :vo2-max :anaerobic :neuromuscular]
            zone-labels (map name ordered-zones)
            ;; Convert seconds to minutes for better display
            time-data (map (fn [z] (/ (get (:time-in-zones analysis-result) z 0) 60.0)) ordered-zones)
            
            chart-data {:labels zone-labels
                        :datasets [{:label "Time in Zone (Minutes)"
                                    :data time-data
                                    :backgroundColor ["#9e9e9e" "#2196f3" "#4caf50" "#ffeb3b" "#ff9800" "#f44336" "#9c27b0"]}]}
            
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
                    [:small (str "Profile: " (or gender "female (default)") ", " (or weight "-") " kg, Max HR: " (or max-hr "-"))]]
                   [:div
                     [:h3 "Est. LTHR"]
                     [:p (str (:lthr-est analysis-result) " bpm")]]]
                  
                  [:article
                   [:h3 "Time in Power Zones"]
                   [:canvas#zonesChart]]

                  [:div.grid
                   [:div
                    [:h4 "Power Zone Details"]
                    [:table
                     [:thead
                      [:tr [:th "Zone"] [:th "Range (Watts)"] [:th "Time"]]]
                     [:tbody
                      (for [zone ordered-zones
                            :let [[min max] (get (:zones analysis-result) zone)
                                  seconds (get (:time-in-zones analysis-result) zone 0)]]
                        [:tr [:td (name zone)] 
                             [:td (str min " - " max)]
                             [:td (format "%.1f min" (/ seconds 60.0))]])]]]
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
                               y: { beginAtZero: true, title: { display: true, text: 'Minutes' } }
                             },
                             plugins: {
                               legend: { display: false }
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
