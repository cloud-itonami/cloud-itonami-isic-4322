(ns plumbing.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [plumbing.store :as store]))

(deftest seed-db-test
  "MemStore can be seeded with demo data."
  (let [db (store/seed-db)]
    (is (contains? (store/all-projects db) {:id "proj-1" :name "Cherry Blossom Apartments Water System"}))
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
