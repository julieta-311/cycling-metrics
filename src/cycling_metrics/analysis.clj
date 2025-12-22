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

(defn analyze-ride [data]
  (let [power-records (:power data) ;; Expecting a list of power values
        ftp (calculate-ftp power-records)]
    {:ftp ftp
     :zones (calculate-zones ftp)}))
