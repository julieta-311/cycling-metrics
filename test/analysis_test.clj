(ns analysis-test
  (:require [clojure.test :refer [deftest is testing]]
            [cycling-metrics.analysis :as analysis]))

(defn make-records [power-val hr-val duration-seconds]
  (into [] (repeat duration-seconds {:power power-val :heart-rate hr-val})))

(deftest calculate-ftp-stats-test
  (testing "calculates FTP and LTHR correctly for a simple case"
    (let [records (make-records 300 160 (* 20 60)) ;; 20 minutes at 300W, 160bpm
          stats (analysis/calculate-ftp-stats records)]
      (is (= (int (* 300 0.95)) (:ftp stats)))
      (is (= 160 (:lthr-est stats)))))

  (testing "handles shorter data than 20 minutes"
    (let [records (make-records 250 140 (* 10 60))
          stats (analysis/calculate-ftp-stats records)]
      (is (= 0 (:ftp stats)))
      (is (= 0 (:lthr-est stats)))))

  (testing "finds the best 20-minute average"
    (let [records (concat (make-records 200 130 (* 10 60)) ;; 10 min at 200W
                          (make-records 350 170 (* 20 60)) ;; 20 min at 350W
                          (make-records 200 130 (* 10 60))) ;; 10 min at 200W
          stats (analysis/calculate-ftp-stats records)]
      (is (= (int (* 350 0.95)) (:ftp stats)))
      (is (= 170 (:lthr-est stats)))))

  (testing "handles empty data"
    (let [stats (analysis/calculate-ftp-stats [])]
      (is (= 0 (:ftp stats))))))

(deftest calculate-zones-test
  (testing "calculates power zones correctly for a given FTP"
    (let [ftp 200
          zones (analysis/calculate-zones ftp)]
      (is (= {:active-recovery [0 110]
              :endurance       [112 150]
              :tempo           [152 180]
              :threshold       [182 210]
              :vo2-max         [212 240]
              :anaerobic       [242 300]
              :neuromuscular   [302 9999]} zones)))))

(deftest calculate-hr-zones-test
  (testing "calculates HR zones correctly"
    (let [max-hr 200
          zones (analysis/calculate-hr-zones max-hr)]
      (is (= [0 118] (:z1 zones)))
      (is (= [120 138] (:z2 zones)))
      (is (= [180 200] (:z5 zones))))))

(deftest calculate-wkg-test
  (testing "calculates watts per kg correctly"
    (is (= 4.0 (analysis/calculate-wkg 300 75.0)))
    (is (= 0.0 (analysis/calculate-wkg 300 nil)))
    (is (= 0.0 (analysis/calculate-wkg 300 0)))))

(deftest classify-performance-test
  (testing "classifies performance based on W/kg and gender"
    (is (= "Good" (analysis/classify-performance 3.2 "male")))
    (is (= "Elite" (analysis/classify-performance 4.1 "female")))
    (is (= "Untrained" (analysis/classify-performance 1.0 "male")))
    (is (= "Elite" (analysis/classify-performance 6.0 "male")))))

(deftest analyze-ride-test
  (testing "analyzes ride with profile data including HR"
    (let [records (make-records 300 160 (* 20 60))
          data {:records records}
          profile {:weight 75.0 :gender "male" :max-hr 190}
          result (analysis/analyze-ride data profile)]
      (is (= 285 (:ftp result))) ;; 300 * 0.95 = 285
      (is (= 3.8 (:wkg result))) ;; 285 / 75.0 = 3.8
      (is (= "Very Good" (:classification result)))
      (is (= 160 (:lthr-est result)))
      (is (some? (:hr-zones result))))))
