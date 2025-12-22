(ns cycling-metrics.analysis)

(defn calculate-ftp [power-data]
  ;; Simple estimation: 95% of best 20-minute average power
  ;; power-data is a sequence of watts (integers)
  (if (empty? power-data)
    0
    (let [window-size 1200 ;; 20 minutes * 60 seconds
          moving-averages (map #(/ (apply + %) (count %))
                               (partition window-size 1 power-data))
          best-20m (if (seq moving-averages) (apply max moving-averages) 0)]
      (int (* 0.95 best-20m)))))

(defn calculate-zones [ftp]
  {:active-recovery [0 (int (* 0.55 ftp))]
   :endurance       [(int (* 0.56 ftp)) (int (* 0.75 ftp))]
   :tempo           [(int (* 0.76 ftp)) (int (* 0.90 ftp))]
   :threshold       [(int (* 0.91 ftp)) (int (* 1.05 ftp))]
   :vo2-max         [(int (* 1.06 ftp)) (int (* 1.20 ftp))]
   :anaerobic       [(int (* 1.21 ftp)) (int (* 1.50 ftp))]
   :neuromuscular   [(int (* 1.51 ftp)) 9999]})

(defn calculate-wkg [ftp weight]
  (if (and weight (pos? weight))
    (double (/ ftp weight))
    0.0))

(defn classify-performance [wkg gender]
  ;; Rough approximation of 20-min power / FTP categorization
  ;; Based on Coggan Power Profile (simplified)
  (let [gender-key (keyword (or gender "male"))
        ;; Thresholds: [Untrained, Fair, Moderate, Good, Very Good, Excellent, Elite]
        thresholds {:male   [2.0 2.5 3.0 3.5 4.0 4.5 5.0]
                    :female [1.5 2.0 2.5 3.0 3.5 4.0 4.5]}]
    (cond
      (< wkg (nth (gender-key thresholds) 0)) "Untrained"
      (< wkg (nth (gender-key thresholds) 1)) "Fair"
      (< wkg (nth (gender-key thresholds) 2)) "Moderate"
      (< wkg (nth (gender-key thresholds) 3)) "Good"
      (< wkg (nth (gender-key thresholds) 4)) "Very Good"
      (< wkg (nth (gender-key thresholds) 5)) "Excellent"
      :else "Elite")))

(defn analyze-ride [data & [{:keys [weight gender] :as _profile}]]
  (let [power-records (:power data) ;; Expecting a list of power values
        ftp (calculate-ftp power-records)
        zones (calculate-zones ftp)
        wkg (calculate-wkg ftp weight)
        classification (classify-performance wkg gender)]
    {:ftp ftp
     :zones zones
     :wkg wkg
     :classification classification}))