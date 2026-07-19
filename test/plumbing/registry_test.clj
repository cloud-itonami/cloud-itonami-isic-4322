(ns plumbing.registry-test
  (:require [clojure.test :refer [deftest is]]
            [plumbing.registry :as registry]))

(deftest register-progress-record-test
  (let [result (registry/register-progress-record "proj-1" "JPN" 0 "rough-in-complete")]
    (is (contains? result "record"))
    (is (contains? result "record_number"))
    (is (= "JPN-PRG-000000" (get result "record_number")))
    (is (= "progress-record-draft" (get-in result ["record" "kind"])))))

(deftest register-progress-record-validation-test
  (is (thrown? clojure.lang.ExceptionInfo
        (registry/register-progress-record "" "JPN" 0 "test")))
  (is (thrown? clojure.lang.ExceptionInfo
        (registry/register-progress-record "proj-1" "" 0 "test")))
  (is (thrown? clojure.lang.ExceptionInfo
        (registry/register-progress-record "proj-1" "JPN" -1 "test"))))

(deftest register-crew-dispatch-test
  (let [result (registry/register-crew-dispatch "proj-1" "USA" 2 "licensed-plumber")]
    (is (= "USA-DSP-000002" (get result "dispatch_number")))
    (is (= "crew-dispatch-proposal-draft" (get-in result ["record" "kind"])))))

(deftest register-safety-hazard-flag-test
  (let [result (registry/register-safety-hazard-flag "proj-1" "DEU" 0 "gas-leak" "detected in heating system")]
    (is (= "DEU-HAZ-000000" (get result "flag_number")))
    (is (= "safety-hazard-flag-draft" (get-in result ["record" "kind"])))
    (is (true? (get-in result ["record" "escalated"])))))

(deftest register-inspection-review-request-test
  (let [result (registry/register-inspection-review-request "proj-1" "JPN" 1 "pressure-test")]
    (is (= "JPN-INS-000001" (get result "request_number")))
    (is (true? (get-in result ["record" "requires_human_sign_off"])))))

(deftest render-hazard-alert-test
  (let [project {:id "proj-1" :name "Test Plumbing" :jurisdiction "JPN"}
        alert (registry/render-hazard-alert project "gas-leak" "smell detected" "JPN-HAZ-000000")]
    (is (string? alert))
    (is (clojure.string/includes? alert "URGENT"))
    (is (clojure.string/includes? alert "gas-leak"))))

(deftest append-test
  (let [history []
        result (registry/register-progress-record "proj-1" "JPN" 0 "start")
        new-history (registry/append history result)]
    (is (= 1 (count new-history)))
    (is (contains? (first new-history) "record_id"))))
