(ns plumbing.governor-contract-test
  (:require [clojure.test :refer [deftest is]]
            [plumbing.governor :as governor]
            [plumbing.store :as store]))

(deftest legal-basis-missing-hard-hold-test
  "A proposal with no legal-basis citation is a HARD violation."
  (let [st (store/seed-db)
        request {:op :log-progress-record :subject "proj-1"}
        proposal {:cites [] :summary "test"}
        result (governor/check request proposal st)]
    (is (false? (:ok? result)))
    (is (= :hold (:disposition result)))
    (is (some #(= :no-legal-basis (:rule %)) (:violations result)))))

(deftest project-unregistered-hard-hold-test
  "Unknown project is a HARD violation."
  (let [st (store/seed-db)
        request {:op :log-progress-record :subject "nonexistent"}
        proposal {:cites ["basis"] :summary "test"}
        result (governor/check request proposal st)]
    (is (false? (:ok? result)))
    (is (= :hold (:disposition result)))
    (is (some #(= :project-unregistered (:rule %)) (:violations result)))))

(deftest safety-hazard-always-escalates-test
  "A safety hazard flag ALWAYS escalates, even when clean."
  (let [st (store/seed-db)
        request {:op :flag-safety-hazard :subject "proj-1"}
        proposal {:cites ["spec-basis"] :value {:hazard-type "gas-leak"} :summary "test"}
        result (governor/check request proposal st)]
    (is (= :escalate (:disposition result)))))

(deftest inspection-review-always-escalates-test
  "An inspection review request ALWAYS escalates, even when clean."
  (let [st (store/seed-db)
        request {:op :request-inspection-review :subject "proj-1"}
        proposal {:cites ["spec-basis"] :value {:inspection-scope "pressure-test"} :summary "test"}
        result (governor/check request proposal st)]
    (is (= :escalate (:disposition result)))))

(deftest low-confidence-escalates-test
  "Low confidence triggers escalation."
  (let [st (store/seed-db)
        request {:op :log-progress-record :subject "proj-1"}
        proposal {:cites ["basis"] :confidence 0.4 :summary "test"}
        result (governor/check request proposal st)]
    (is (= :escalate (:disposition result)))))

(deftest clean-progress-log-commits-test
  "A clean progress log with good confidence commits."
  (let [st (store/seed-db)
        request {:op :log-progress-record :subject "proj-1"}
        proposal {:cites ["basis"] :confidence 0.95 :summary "test"}
        result (governor/check request proposal st)]
    (is (= :commit (:disposition result)))))
