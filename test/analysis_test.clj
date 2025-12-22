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
  
  ;; ... other calculate-ftp-stats tests can remain ...
)

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

;; ... other tests ...

(deftest classify-performance-test
  (testing "classifies performance based on W/kg and gender"
    (is (= "Good" (analysis/classify-performance 3.2 "male")))
    (is (= "Excellent" (analysis/classify-performance 4.1 "female")))
    (is (= "Untrained" (analysis/classify-performance 1.0 "male")))
    (is (= "Elite" (analysis/classify-performance 6.0 "male"))))
  
  (testing "defaults to female if gender is not provided"
    ;; Male threshold for 3.2 is "Good" (3.0-3.5)
    ;; Female threshold for 3.2 is "Very Good" (3.0-3.5 is Good, >3.5 Very Good? No. Female: 3.0 Good, 3.5 Very Good. So 3.2 is Good.)
    ;; Let's try 2.8. Male: 2.5-3.0 is Fair? No.
    ;; Male: 2.5-3.0 -> Fair (2.5) to Moderate (3.0). So 2.8 is Moderate.
    ;; Female: 2.5-3.0 -> Moderate. 2.8 is Moderate.
    ;; Let's try 4.1.
    ;; Male: 4.0-4.5 -> Very Good. 4.1 is Very Good.
    ;; Female: 4.0-4.5 -> Excellent. 4.1 is Excellent.
    (is (= "Excellent" (analysis/classify-performance 4.1 nil))))

  (testing "maps inclusive gender options to correct standards"
    ;; Trans MTF (Female standard): 4.1 W/kg -> Excellent (same as female)
    (is (= "Excellent" (analysis/classify-performance 4.1 "trans_mtf")))
    ;; Trans FTM (Male standard): 3.2 W/kg -> Good (same as male)
    (is (= "Good" (analysis/classify-performance 3.2 "trans_ftm")))
    ;; Non-binary Male (Male standard)
    (is (= "Good" (analysis/classify-performance 3.2 "nonbinary_male")))
    ;; Non-binary Female (Female standard)
    (is (= "Excellent" (analysis/classify-performance 4.1 "nonbinary_female")))))

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
      (is (= (* 20 60) (get (:time-in-zones result) :vo2-max)))))

  (testing "uses manual FTP if provided"
    (let [records (make-records 300 160 (* 20 60))
          data {:records records}
          ;; Even though estimated FTP is 285, we override with 350
          profile {:weight 75.0 :gender "female" :manual-ftp 350}
          result (analysis/analyze-ride data profile)]
      (is (= 350 (:ftp result)))
      (is (= 285 (:estimated-ftp result)))
      ;; W/kg should use manual FTP
      (is (= (/ 350.0 75.0) (:wkg result)))
      ;; Zones should be based on 350. 
      ;; Threshold: 350 * 0.91 = 318.5 -> 319. Max 350 * 1.05 = 367.5 -> 368.
      ;; The ride was at 300W. 300 < 319.
      ;; Tempo: 350 * 0.76 = 266. 350 * 0.90 = 315.
      ;; So 300W is in Tempo zone.
      (is (= (* 20 60) (get (:time-in-zones result) :tempo))))))
