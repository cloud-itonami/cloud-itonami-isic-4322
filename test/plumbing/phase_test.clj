(ns plumbing.phase-test
  (:require [clojure.test :refer [deftest is]]
            [plumbing.phase :as phase]))

(deftest phase-0-read-only-test
  (let [result (phase/gate 0 {:op :log-progress-record :subject "proj-1"} :commit)]
    (is (= :hold (:disposition result)))
    (is (= :phase-disabled (:reason result)))))

(deftest phase-1-progress-intake-test
  (let [result (phase/gate 1 {:op :log-progress-record :subject "proj-1"} :commit)]
    (is (= :commit (:disposition result))))
  (let [result (phase/gate 1 {:op :flag-safety-hazard :subject "proj-1"} :commit)]
    (is (= :hold (:disposition result)))
    (is (= :phase-disabled (:reason result)))))

(deftest phase-2-hazard-screening-test
  (let [result (phase/gate 2 {:op :flag-safety-hazard :subject "proj-1"} :commit)]
    (is (= :commit (:disposition result)))))

(deftest phase-3-escalation-required-test
  (let [result (phase/gate 3 {:op :flag-safety-hazard :subject "proj-1"} :commit)]
    (is (= :escalate (:disposition result)))
    (is (= :phase-approval (:reason result))))
  (let [result (phase/gate 3 {:op :request-inspection-review :subject "proj-1"} :commit)]
    (is (= :escalate (:disposition result)))))

(deftest phase-governor-hold-override-test
  (let [result (phase/gate 3 {:op :log-progress-record :subject "proj-1"} :hold)]
    (is (= :hold (:disposition result)))
    (is (nil? (:reason result)))))

(deftest verdict-to-disposition-test
  (is (= :hold (phase/verdict->disposition {:hard? true})))
  (is (= :escalate (phase/verdict->disposition {:escalate? true})))
  (is (= :commit (phase/verdict->disposition {:hard? false :escalate? false}))))
