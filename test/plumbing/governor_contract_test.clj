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

(deftest unresolved-hazard-hard-holds-any-later-op-test
  "Once a hazard is committed to `store/hazard-history` (via the real
  `:hazard/flag` commit-record! path, string-keyed \"project_id\"/
  \"escalated\" -- see `plumbing.registry/register-safety-hazard-flag`),
  ANY later op on that same project is a HARD, un-overridable hold. This
  exercises the string-vs-keyword key fix in
  `plumbing.governor/unresolved-hazard-violations` end to end through
  the real store commit path, not a hand-rolled fixture."
  (let [st (store/seed-db)]
    (store/commit-record! st {:effect :hazard/flag
                               :path ["proj-1"]
                               :value {:hazard-type "gas-leak" :description "smell reported"}
                               :payload {:hazard-type "gas-leak" :description "smell reported"}})
    (let [request {:op :log-progress-record :subject "proj-1"}
          proposal {:cites ["basis"] :confidence 0.95 :summary "test"}
          result (governor/check request proposal st)]
      (is (= :hold (:disposition result)))
      (is (some #(= :unresolved-hazard (:rule %)) (:violations result))))))

(deftest already-flagged-hard-holds-immediate-repeat-test
  "Flagging the SAME hazard type on the SAME project twice in a row is a
  HARD hold (in addition to the always-on unresolved-hazard hold once
  the first flag is on file) -- exercises the request `:hazard-type`
  field-path fix in `plumbing.governor/already-flagged-violations`."
  (let [st (store/seed-db)]
    (store/commit-record! st {:effect :hazard/flag
                               :path ["proj-2"]
                               :value {:hazard-type "gas-leak" :description "smell reported"}
                               :payload {:hazard-type "gas-leak" :description "smell reported"}})
    (let [request {:op :flag-safety-hazard :subject "proj-2" :hazard-type "gas-leak"
                    :description "smell reported again"}
          proposal {:cites ["basis"] :value {:hazard-type "gas-leak"} :confidence 0.9 :summary "test"}
          result (governor/check request proposal st)]
      (is (= :hold (:disposition result)))
      (is (some #(= :already-flagged (:rule %)) (:violations result)))
      (is (some #(= :unresolved-hazard (:rule %)) (:violations result))))))
