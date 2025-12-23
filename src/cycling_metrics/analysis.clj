(ns cycling-metrics.analysis)

(defn mean [coll]
  (if (empty? coll)
    0.0
    (/ (apply + coll) (count coll))))

(defn calculate-ftp-stats 
  "Finds the best 20-minute power segment and estimates FTP and LTHR."
  [records]
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
  (let [z1-max (int (Math/round (* 0.55 ftp)))
        z2-max (int (Math/round (* 0.75 ftp)))
        z3-max (int (Math/round (* 0.90 ftp)))
        z4-max (int (Math/round (* 1.05 ftp)))
        z5-max (int (Math/round (* 1.20 ftp)))
        z6-max (int (Math/round (* 1.50 ftp)))]
    {:active-recovery [0 z1-max]
     :endurance       [(inc z1-max) z2-max]
     :tempo           [(inc z2-max) z3-max]
     :threshold       [(inc z3-max) z4-max]
     :vo2-max         [(inc z4-max) z5-max]
     :anaerobic       [(inc z5-max) z6-max]
     :neuromuscular   [(inc z6-max) 9999]}))

(defn calculate-hr-zones [max-hr]
  (when (and max-hr (pos? max-hr))
    {:z1 [0 (int (* 0.59 max-hr))]      ;; Recovery
     :z2 [(int (* 0.60 max-hr)) (int (* 0.69 max-hr))] ;; Endurance
     :z3 [(int (* 0.70 max-hr)) (int (* 0.79 max-hr))] ;; Tempo
     :z4 [(int (* 0.80 max-hr)) (int (* 0.89 max-hr))] ;; Threshold
     :z5 [(int (* 0.90 max-hr)) max-hr]})) ;; VO2 Max

(defn calculate-zone-stats [records zones]
  (let [initial-stats (reduce (fn [acc k] (assoc acc k {:time 0 :hr-sum 0 :hr-count 0})) {} (keys zones))]
    (->> records
         (reduce (fn [stats record]
                   (let [power (:power record)
                         hr    (:heart-rate record)
                         ;; Find which zone this power value belongs to
                         [zone-name _] (first (filter (fn [[_ [min max]]]
                                                        (<= min power max))
                                                      zones))]
                     (if zone-name
                       (-> stats
                           (update-in [zone-name :time] inc)
                           (cond-> (and hr (pos? hr))
                             (-> (update-in [zone-name :hr-sum] + hr)
                                 (update-in [zone-name :hr-count] inc))))
                       stats)))
                 initial-stats)
         (reduce-kv (fn [m k v]
                      (assoc m k {:time (:time v)
                                  :avg-hr (if (pos? (:hr-count v))
                                            (int (/ (:hr-sum v) (:hr-count v)))
                                            0)}))
                    {}))))

(defn calculate-wkg [ftp weight]
  (if (and weight (pos? weight))
    (double (/ ftp weight))
    0.0))

(defn estimate-max-hr [age]
  (if (and age (pos? age))
    (int (- 208 (* 0.7 age)))
    nil))

(defn estimate-cycling-ftp-from-running-ftp [running-ftp]
  (when (and running-ftp (pos? running-ftp))
    {:cycling-ftp-est (int (* running-ftp 0.8)) ;; Rough estimate: 80% of Running Power
     :running-ftp running-ftp}))

(defn classify-performance [wkg gender]
  (let [gender-str (str (or gender "female"))
        gender-key (cond
                     (or (= gender-str "male") 
                         (= gender-str "trans_ftm") 
                         (= gender-str "nonbinary_male")) :male
                     :else :female)
        thresholds {:male   [2.0 2.5 3.0 3.5 4.0 4.5 5.0]
                    :female [1.5 2.0 2.5 3.0 3.5 4.0 4.5]}]
    (cond
      (< wkg (nth (gender-key thresholds) 0)) "Recovery"
      (< wkg (nth (gender-key thresholds) 1)) "Fair"
      (< wkg (nth (gender-key thresholds) 2)) "Moderate"
      (< wkg (nth (gender-key thresholds) 3)) "Good"
      (< wkg (nth (gender-key thresholds) 4)) "Very Good"
      (< wkg (nth (gender-key thresholds) 5)) "Advanced"
      (< wkg (nth (gender-key thresholds) 6)) "Excellent"
      :else "Elite")))

(defn analyse-ride [data & [{:keys [weight _height gender max-hr manual-ftp] :as _profile}]]
  (let [records (:records data)
        {:keys [ftp lthr-est]} (calculate-ftp-stats records)
        effective-ftp (if (and manual-ftp (pos? manual-ftp)) manual-ftp ftp)
        zones (calculate-zones effective-ftp)
        hr-zones (calculate-hr-zones max-hr)
        zone-stats (calculate-zone-stats records zones)
        wkg (calculate-wkg effective-ftp weight)
        classification (classify-performance wkg gender)]
    {:ftp effective-ftp
     :estimated-ftp ftp ;; Keep original estimate for reference
     :lthr-est lthr-est
     :zones zones
     :zone-stats zone-stats
     :hr-zones hr-zones
     :wkg wkg
     :classification classification}))
