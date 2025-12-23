(ns cycling-metrics.analysis)

;; 1. Utility Functions
(defn mean [coll]
  (if (empty? coll)
    0.0
    (double (/ (apply + coll) (count coll)))))

;; 2. Core Estimation Logic
(defn calculate-ftp-stats
  "Finds the best 20-minute power segment and estimates FTP and LTHR."
  [records]
  (if (or (empty? records) (< (count records) 1200))
    {:ftp 0 :lthr-est 0}
    (let [window-size 1200
          windows (partition window-size 1 records)
          best-window (apply max-key #(mean (map :power %)) windows)
          avg-power (mean (map :power best-window))
          avg-hr    (mean (map :heart-rate best-window))]
      {:ftp (int (* 0.95 avg-power))
       :lthr-est (int avg-hr)})))

(defn calculate-normalized-power [records]
  (if (empty? records)
    0.0
    (let [powers (map :power records)
          rolling-avgs (map mean (partition 30 1 powers))
          fourth-powers (map #(Math/pow % 4) rolling-avgs)
          avg-fourth (mean fourth-powers)]
      (Math/pow avg-fourth 0.25))))

(defn calculate-decoupling [records]
  (let [count-recs (count records)
        mid (quot count-recs 2)]
    (if (< count-recs 120)
      0.0
      (let [[first-half second-half] (split-at mid records)
            calc-ef (fn [recs]
                      (let [ap (mean (map :power recs))
                            ahr (mean (map :heart-rate recs))]
                        (if (pos? ahr) (/ ap ahr) 0)))
            ef1 (calc-ef first-half)
            ef2 (calc-ef second-half)]
        (if (and (pos? ef1) (pos? ef2))
          (double (* (/ (- ef1 ef2) ef1) 100))
          0.0)))))

;; 3. Zone Calculations
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
    {:z1 [0 (int (* 0.59 max-hr))]
     :z2 [(int (* 0.60 max-hr)) (int (* 0.69 max-hr))]
     :z3 [(int (* 0.70 max-hr)) (int (* 0.79 max-hr))]
     :z4 [(int (* 0.80 max-hr)) (int (* 0.89 max-hr))]
     :z5 [(int (* 0.90 max-hr)) max-hr]}))

(defn calculate-zone-stats [records zones]
  (let [initial-stats (reduce (fn [acc k] (assoc acc k {:time 0 :hr-sum 0 :hr-count 0})) {} (keys zones))]
    (->> records
         (reduce (fn [stats record]
                   (let [power (:power record)
                         hr    (:heart-rate record)
                         [zone-name _] (first (filter (fn [[_ [min max]]] (<= min power max)) zones))]
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

;; 4. Performance Metrics & Classification
(defn calculate-wkg [ftp weight]
  (if (and weight (pos? weight))
    (double (/ ftp weight))
    0.0))

(defn estimate-max-hr [age]
  (if (and age (pos? age))
    (int (- 208 (* 0.7 age)))
    nil))

(defn classify-performance [wkg gender]
  (let [gender-key (if (contains? #{"male" "trans_ftm" "nonbinary_male"} (str gender)) :male :female)
        thresholds {:male   [2.0 2.5 3.0 3.5 4.0 4.5 5.0]
                    :female [1.5 2.0 2.5 3.0 3.5 4.0 4.5]}
        vals (gender-key thresholds)]
    (cond
      (< wkg (nth vals 0)) "Recovery"
      (< wkg (nth vals 1)) "Fair"
      (< wkg (nth vals 2)) "Moderate"
      (< wkg (nth vals 3)) "Good"
      (< wkg (nth vals 4)) "Very Good"
      (< wkg (nth vals 5)) "Advanced"
      (< wkg (nth vals 6)) "Excellent"
      :else "Elite")))

(defn estimate-cycling-ftp-from-running-ftp [running-ftp]
  (if (and running-ftp (pos? running-ftp))
    {:cycling-ftp-est (int (* running-ftp 0.8))
     :running-ftp running-ftp}
    {:cycling-ftp-est 0 :running-ftp 0}))

;; 5. Main Analysis Entry Point
(defn analyse-ride [data & [{:keys [weight gender max-hr manual-ftp] :as _profile}]]
  (let [records (:records data)
        {:keys [ftp lthr-est]} (calculate-ftp-stats records)
        effective-ftp (if (and manual-ftp (pos? manual-ftp)) manual-ftp ftp)
        avg-power (mean (map :power records))
        avg-hr (mean (map :heart-rate records))
        np (calculate-normalized-power records)
        wkg (calculate-wkg effective-ftp weight)
        zones (calculate-zones effective-ftp)]
    {:ftp effective-ftp
     :estimated-ftp ftp
     :max-hr max-hr
     :lthr-est lthr-est
     :zones zones
     :zone-stats (calculate-zone-stats records zones)
     :hr-zones (calculate-hr-zones max-hr)
     :wkg wkg
     :classification (classify-performance wkg gender)
     :avg-power (int avg-power)
     :normalized-power (int np)
     :variability-index (if (pos? avg-power) (/ np avg-power) 1.0)
     :efficiency-factor (if (pos? avg-hr) (/ avg-power avg-hr) 0.0)
     :decoupling (calculate-decoupling records)}))
