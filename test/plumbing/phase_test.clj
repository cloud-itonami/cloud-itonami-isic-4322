(ns plumbing.phase-test
  (:require [clojure.test :refer [deftest is]]
            [plumbing.phase :as phase]))

(deftest phase-0-read-only-test
  (let [result (phase/gate 0 {:op :log-progress-record :subject "proj-1"} :commit)]
    (is (= :hold (:disposition result)))
    (is (= :phase-disabled (:reason result)))))

(deftest phase-1-progress-intake-test
  ;; Phase 1 = "assisted-intake -- ... every write needs human approval"
  ;; (phase.cljc docstring): :log-progress-record is enabled to write but
  ;; not in phase 1's `:auto` set, so a governor-clean proposal still
  ;; escalates rather than auto-committing.
  (let [result (phase/gate 1 {:op :log-progress-record :subject "proj-1"} :commit)]
    (is (= :escalate (:disposition result)))
    (is (= :phase-approval (:reason result))))
  (let [result (phase/gate 1 {:op :flag-safety-hazard :subject "proj-1"} :commit)]
    (is (= :hold (:disposition result)))
    (is (= :phase-disabled (:reason result)))))

(deftest phase-2-hazard-screening-test
  ;; Phase 2 = "assisted-verify -- adds hazard flagging ... still
  ;; approval-gated" (phase.cljc docstring): no op is in phase 2's
  ;; `:auto` set, so a governor-clean proposal still escalates.
  (let [result (phase/gate 2 {:op :flag-safety-hazard :subject "proj-1"} :commit)]
    (is (= :escalate (:disposition result)))
    (is (= :phase-approval (:reason result)))))

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
  ;; Shaped exactly like the real `plumbing.governor/check` return value
  ;; (`:disposition` + derived `:escalate?`), not an invented contract --
  ;; a HARD hold must survive as :hold even when `:escalate?` is false.
  (is (= :hold (phase/verdict->disposition {:disposition :hold :escalate? false})))
  (is (= :escalate (phase/verdict->disposition {:disposition :escalate :escalate? true})))
  (is (= :commit (phase/verdict->disposition {:disposition :commit :escalate? false}))))
