(ns cycling-metrics.web
  (:require [reitit.ring :as ring]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [cycling-metrics.analysis :as analysis]
            [cycling-metrics.fit :as fit]
            [cheshire.core :as json]
            [clojure.string :as str]))

;; --- Helpers ---

(defn parse-double-safe [s default]
  (if (str/blank? s) default (try (Double/parseDouble s) (catch Exception _ default))))

(defn parse-int-safe [s default]
  (if (str/blank? s) default (try (Integer/parseInt s) (catch Exception _ default))))

;; --- UI Components (Called with parentheses) ---

(defn metric-card [label value subtext & [{:keys [border-color status-text]}]]
  [:div.metric-card {:style (str "border-left: 5px solid " (or border-color "#009688") ";")}
   [:div.metric-label label]
   [:div.metric-val value]
   [:small subtext]
   (when status-text [:div {:style "margin-top: 0.2rem; font-weight: bold; color: #4caf50; font-size: 0.85rem;"} status-text])])

(defn profile-banner [gender weight height max-hr]
  [:div.profile-banner
   [:span [:strong "Rider Profile: "]
    (str (or gender "Female") " | " weight "kg | " height "cm")]
   [:span (when max-hr (str "Max HR: " max-hr " bpm"))]])

(defn chart-script [res ordered-zones]
  (let [chart-data {:labels (map name ordered-zones)
                    :datasets [{:label "Time (min)"
                                :data (map #(double (/ (get-in res [:zone-stats % :time] 0) 60)) ordered-zones)
                                :backgroundColor ["#9e9e9e" "#2196f3" "#4caf50" "#ffeb3b" "#ff9800" "#f44336" "#9c27b0"]
                                :yAxisID "y-time" :order 2}
                               {:label "Avg HR (bpm)"
                                :data (map #(get-in res [:zone-stats % :avg-hr] 0) ordered-zones)
                                :type "line" :borderColor "#ffa726" :borderWidth 3 :pointRadius 5
                                :yAxisID "y-hr" :order 1}]}]
    (str "const ctx = document.getElementById('zonesChart').getContext('2d');
          new Chart(ctx, {
            type: 'bar',
            data: " (json/generate-string chart-data) ",
            options: {
              responsive: true,
              scales: {
                'y-time': { type: 'linear', position: 'left', title: { display: true, text: 'Time (min)', color: '#bbb' }, ticks: { color: '#888' } },
                'y-hr': { type: 'linear', position: 'right', title: { display: true, text: 'Heart Rate (bpm)', color: '#bbb' }, ticks: { color: '#888' }, grid: { drawOnChartArea: false } }
              },
              plugins: { legend: { labels: { color: '#eee' } } }
            }
          });")))

;; --- Handlers ---

(defn home-handler [_request]
  {:status 200 :headers {"Content-Type" "text/html"}
   :body (html5
          [:head [:title "Cycling Metrics"] (include-css "https://cdn.jsdelivr.net/npm/@picocss/pico@1/css/pico.min.css")]
          [:body {:style "background-color: #121212; color: #eee;"}
           [:main.container
            [:h1 "Cycling Metrics"]
            [:form {:action "/upload" :method "post" :enctype "multipart/form-data"}
             [:div.grid
              [:label "Upload .fit" [:input {:type "file" :name "file" :required true}]]
              [:label "Weight (kg)" [:input {:type "number" :name "weight" :placeholder "70.0"}]]
              [:label "Height (cm)" [:input {:type "number" :name "height" :placeholder "170"}]]]
             [:div.grid
              [:label "Gender" [:select {:name "gender"} [:option {:value "female"} "Female"] [:option {:value "male"} "Male"]]]
              [:label "Age" [:input {:type "number" :name "age"}]]
              [:label "Manual FTP" [:input {:type "number" :name "manual_ftp"}]]]
             [:button {:type "submit"} "Analyse"]]]])})

(defn upload-handler [request]
  (let [params (:params request)
        file (get params "file")
        weight (parse-double-safe (get params "weight") 70.0)
        height (parse-double-safe (get params "height") 170.0)
        age (parse-int-safe (get params "age") nil)
        m-ftp (parse-int-safe (get params "manual_ftp") nil)
        gender (get params "gender")
        res (analysis/analyse-ride (fit/parse-fit (:tempfile file)) {:weight weight :gender gender :manual-ftp m-ftp :max-hr (analysis/estimate-max-hr age)})
        ordered-zones [:active-recovery :endurance :tempo :threshold :vo2-max :anaerobic :neuromuscular]]
    {:status 200 :headers {"Content-Type" "text/html"}
     :body (html5
            [:head [:title "Analysis Result"]
             (include-css "https://cdn.jsdelivr.net/npm/@picocss/pico@1/css/pico.min.css")
             (include-js "https://cdn.jsdelivr.net/npm/chart.js")
             [:style "body { background-color: #121212; color: #e0e0e0; }
                     .metric-card { text-align: center; padding: 1.5rem; background: #1e1e1e; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }
                     .metric-val { font-size: 2.2rem; font-weight: bold; color: #fff; line-height: 1; margin: 0.5rem 0; }
                     .metric-label { font-size: 0.8rem; text-transform: uppercase; color: #888; letter-spacing: 1px; font-weight: 600; }
                     .profile-banner { background: #2c2c2c; padding: 1rem; border-radius: 8px; margin-bottom: 2rem; display: flex; justify-content: space-between; border-left: 5px solid #ffa726; box-shadow: 0 2px 4px rgba(0,0,0,0.2); }
                     article { background: #1e1e1e !important; border: none; padding: 1.5rem; border-radius: 8px; }
                     nav a { color: #2196f3; }"]]
            [:body
             [:main.container
              [:nav [:ul [:li [:a {:href "/"} "Home"]] [:li "Analysis"]]]
              (profile-banner gender weight height (:max-hr res))
              [:div.grid
               (metric-card "FTP" (str (:ftp res) " W") (when (not= (:ftp res) (:estimated-ftp res)) "(Manual)") {:border-color "#2196f3"})
               (metric-card "Normalized Power" (str (:normalized-power res) " W") (str "Avg: " (:avg-power res) "W") {:border-color "#f44336"})
               (metric-card "Performance" (format "%.2f" (:wkg res)) "W/kg" {:status-text (:classification res) :border-color "#4caf50"})]
              [:div.grid {:style "margin-top: 1rem;"}
               (metric-card "Efficiency (EF)" (format "%.2f" (double (:efficiency-factor res))) "Watts/BPM" {:border-color "#ffa726"})
               (metric-card "Decoupling" (format "%.1f%%" (:decoupling res)) (if (> (:decoupling res) 5.0) "High Drift" "Stable") {:border-color "#9c27b0"})
               (metric-card "Variability (VI)" (format "%.2f" (double (:variability-index res))) (if (> (:variability-index res) 1.05) "Surgy" "Steady") {:border-color "#607d8b"})]
              [:article [:canvas#zonesChart]]
              [:script (chart-script res ordered-zones)]]])}))

(def app
  (ring/ring-handler
   (ring/router [["/" {:get home-handler}] ["/upload" {:post upload-handler}]])
   (ring/routes (ring/create-default-handler))
   {:middleware [wrap-multipart-params]}))
