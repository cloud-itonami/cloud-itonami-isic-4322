(ns plumbing.facts-test
  (:require [clojure.test :refer [deftest is]]
            [plumbing.facts :as facts]))

(deftest spec-basis-test
  (is (contains? (facts/spec-basis "JPN") :legal-basis))
  (is (contains? (facts/spec-basis "USA") :legal-basis))
  (is (contains? (facts/spec-basis "DEU") :legal-basis))
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-test
  (let [cov (facts/coverage)]
    (is (> (:covered cov) 0))
    (is (contains? cov :covered-jurisdictions))
    (is (contains? cov :missing-jurisdictions))))

(deftest hazard-types-test
  (let [jpn-hazards (facts/hazard-types "JPN")
        usa-hazards (facts/hazard-types "USA")]
    (is (seq jpn-hazards))
    (is (contains? (set jpn-hazards) "gas-leak"))
    (is (seq usa-hazards))))

(deftest inspection-checklist-test
  (let [checklist (facts/inspection-checklist "JPN")]
    (is (seq checklist))
    (is (some #(clojure.string/includes? % "pressure") %)))
  (is (empty? (facts/inspection-checklist "ATL"))))
