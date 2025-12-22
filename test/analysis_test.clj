(ns analysis-test
  (:require [clojure.test :refer [deftest is testing]]
            [cycling-metrics.analysis :as analysis]))

(deftest calculate-ftp-test
  (testing "calculates FTP correctly for a simple case"
    (let [power-data (into [] (repeat (* 20 60) 300)) ;; 20 minutes at 300W
          ftp (analysis/calculate-ftp power-data)]
      (is (= (int (* 300 0.95)) ftp))))

  (testing "handles shorter data than 20 minutes"
    (let [power-data (into [] (repeat (* 10 60) 250))
          ftp (analysis/calculate-ftp power-data)]
      (is (= 0 ftp)))) ;; Should be 0 since it can't find a 20-min window

  (testing "finds the best 20-minute average"
    (let [power-data (concat (repeat (* 10 60) 200) ;; 10 min at 200W
                             (repeat (* 20 60) 350) ;; 20 min at 350W
                             (repeat (* 10 60) 200)) ;; 10 min at 200W
          ftp (analysis/calculate-ftp power-data)]
      (is (= (int (* 350 0.95)) ftp))))

  (testing "handles empty power data"
    (is (= 0 (analysis/calculate-ftp [])))))

(deftest calculate-zones-test
  (testing "calculates zones correctly for a given FTP"
    (let [ftp 200
          zones (analysis/calculate-zones ftp)]
      (is (= {:active-recovery [0 110]
              :endurance       [112 150]
              :tempo           [152 180]
              :threshold       [182 210]
              :vo2-max         [212 240]
              :anaerobic       [242 300]
              :neuromuscular   [302 9999]} zones)))))
