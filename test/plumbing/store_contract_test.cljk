(ns plumbing.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [plumbing.store :as store]))

(deftest seed-db-test
  "MemStore can be seeded with demo data."
  (let [db (store/seed-db)]
    ;; `all-projects` returns a seq, not an associative/indexed
    ;; collection -- `contains?` on a seq checks for an index, not a
    ;; value (throws IllegalArgumentException), so use `some` to find
    ;; the seeded project by id + name instead.
    (is (some #(and (= "proj-1" (:id %))
                     (= "Cherry Blossom Apartments Water System" (:name %)))
               (store/all-projects db)))
    (is (= 3 (count (store/all-projects db))))))

(deftest project-lookup-test
  "Project lookup by id."
  (let [db (store/seed-db)
        proj (store/project db "proj-1")]
    (is (= "proj-1" (:id proj)))
    (is (= "Cherry Blossom Apartments Water System" (:name proj)))))

(deftest project-not-found-test
  "Nonexistent project returns nil."
  (let [db (store/seed-db)]
    (is (nil? (store/project db "nonexistent")))))

(deftest ledger-append-test
  "Facts can be appended to the ledger."
  (let [db (store/seed-db)
        _ (store/append-ledger! db {:t :test-fact :data "test"})
        ledger (store/ledger db)]
    (is (= 1 (count ledger)))
    (is (= :test-fact (:t (first ledger))))))

(deftest empty-histories-test
  "New MemStore has empty histories."
  (let [db (store/seed-db)]
    (is (empty? (store/progress-history db)))
    (is (empty? (store/dispatch-history db)))
    (is (empty? (store/hazard-history db)))
    (is (empty? (store/inspection-history db)))))

(deftest sequence-counters-test
  "Sequence counters start at zero."
  (let [db (store/seed-db)]
    (is (= 0 (store/next-progress-sequence db "JPN")))
    (is (= 0 (store/next-dispatch-sequence db "USA")))
    (is (= 0 (store/next-hazard-sequence db "DEU")))))
