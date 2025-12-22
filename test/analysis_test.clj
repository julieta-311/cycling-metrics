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
              :endurance       [111 150]
              :tempo           [151 180]
              :threshold       [181 210]
              :vo2-max         [211 240]
              :anaerobic       [241 300]
              :neuromuscular   [301 9999]} zones)))))

;; ... (other tests)

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
      (is (some? (:hr-zones result)))
      (is (= (* 20 60) (get (:time-in-zones result) :vo2-max)))))) ;; 300W is in anaerobic zone for FTP 285?
      ;; 285 * 1.05 = 299.25 (Threshold upper)
      ;; 285 * 1.06 = 302.1 (VO2 max lower)
      ;; Wait, 300 is > 299. It should be VO2 Max or Threshold depending on rounding?
      ;; Let's check ranges: Threshold 260-299, VO2 300-342. So 300 is VO2 Max.