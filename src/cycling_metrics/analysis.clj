(ns cycling-metrics.analysis)

(defn mean [coll]
  (if (empty? coll)
    0.0
    (/ (apply + coll) (count coll))))

(defn calculate-ftp-stats [records]
  "Finds the best 20-minute power segment and estimates FTP and LTHR."
  (if (or (empty? records) (< (count records) 1200))
    {:ftp 0 :lthr-est 0}
    (let [window-size 1200
          ;; Create sliding windows of records
          windows (partition window-size 1 records)
          ;; Find the window with the highest average power
          ;; Note: This can be slow for very long files, but sufficient for now.
          best-window (apply max-key #(mean (map :power %)) windows)
          avg-power (mean (map :power best-window))
          avg-hr    (mean (map :heart-rate best-window))]
      {:ftp (int (* 0.95 avg-power))
       :lthr-est (int avg-hr)})))

(defn calculate-zones [ftp]
  {:active-recovery [0 (int (* 0.55 ftp))]
   :endurance       [(int (* 0.56 ftp)) (int (* 0.75 ftp))]
   :tempo           [(int (* 0.76 ftp)) (int (* 0.90 ftp))]
   :threshold       [(int (* 0.91 ftp)) (int (* 1.05 ftp))]
   :vo2-max         [(int (* 1.06 ftp)) (int (* 1.20 ftp))]
   :anaerobic       [(int (* 1.21 ftp)) (int (* 1.50 ftp))]
   :neuromuscular   [(int (* 1.51 ftp)) 9999]})

(defn calculate-hr-zones [max-hr]
  (when (and max-hr (pos? max-hr))
    {:z1 [0 (int (* 0.59 max-hr))]      ;; Recovery
     :z2 [(int (* 0.60 max-hr)) (int (* 0.69 max-hr))] ;; Endurance
     :z3 [(int (* 0.70 max-hr)) (int (* 0.79 max-hr))] ;; Tempo
     :z4 [(int (* 0.80 max-hr)) (int (* 0.89 max-hr))] ;; Threshold
     :z5 [(int (* 0.90 max-hr)) max-hr]})) ;; VO2 Max

(defn calculate-wkg [ftp weight]
  (if (and weight (pos? weight))
    (double (/ ftp weight))
    0.0))

(defn classify-performance [wkg gender]
  (let [gender-key (keyword (or gender "male"))
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

(defn analyze-ride [data & [{:keys [weight gender max-hr] :as _profile}]]
  (let [records (:records data)
        {:keys [ftp lthr-est]} (calculate-ftp-stats records)
        zones (calculate-zones ftp)
        hr-zones (calculate-hr-zones max-hr)
        wkg (calculate-wkg ftp weight)
        classification (classify-performance wkg gender)]
    {:ftp ftp
     :lthr-est lthr-est
     :zones zones
     :hr-zones hr-zones
     :wkg wkg
     :classification classification}))
