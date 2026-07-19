(ns plumbing.operation-test
  "End-to-end tests driving the real `plumbing.operation/build` StateGraph
  through `langgraph.graph/run*` -- closes a real test-coverage gap: every
  other test in this suite exercises one namespace's functions in
  isolation (governor/check directly, phase/gate directly, registry fns
  directly), but nothing previously drove the actual wired graph. That
  gap is exactly where two real bugs hid (see `plumbing.render-html`'s ns
  docstring for the full list found while building the flagship demo,
  2026-07-19): `plumbing.phase/verdict->disposition` reading a key
  `governor/check` never sets, and `:decide`/`:request-approval`/`:commit`
  in `plumbing.operation` never actually calling `store/commit-record!`
  (so no op's effect ever reached the store's `:projects`/`:progress`/
  `:hazards`/etc, only the audit ledger)."
  (:require [clojure.test :refer [deftest is]]
            [plumbing.store :as store]
            [plumbing.operation :as op]
            [langgraph.graph :as g]))

(def ^:private ctx {:actor-id "op-1" :actor-role :test :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context ctx} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(deftest phase-3-clean-progress-log-auto-commits-and-writes-store-test
  (let [db (store/seed-db)
        actor (op/build db)
        result (exec! actor "op-t1" {:op :log-progress-record :subject "proj-1"
                                      :milestone "rough-in complete"})]
    (is (= :done (:status result)))
    (is (= :commit (get-in result [:state :disposition])))
    ;; The real regression: before the fix, disposition was :commit but
    ;; `store/progress-history` stayed empty forever.
    (is (= 1 (count (store/progress-history db))))
    (is (= "rough-in complete" (get-in (first (store/progress-history db)) ["milestone"])))
    (is (= "rough-in complete" (:last-milestone (store/project db "proj-1"))))))

(deftest escalate-then-approve-commits-and-writes-store-test
  (let [db (store/seed-db)
        actor (op/build db)
        r1 (exec! actor "op-t2" {:op :flag-safety-hazard :subject "proj-2"
                                  :hazard-type "gas-leak" :description "odor reported"})]
    (is (= :interrupted (:status r1)))
    (is (= :escalate (get-in r1 [:state :disposition])))
    (is (empty? (store/hazard-history db)) "not yet approved -- must not have written the store")
    (let [r2 (approve! actor "op-t2")]
      (is (= :done (:status r2)))
      (is (= :commit (get-in r2 [:state :disposition])))
      (is (= 1 (count (store/hazard-history db))))
      (is (= "proj-2" (get (first (store/hazard-history db)) "project_id")))
      (is (true? (get (first (store/hazard-history db)) "escalated"))))))

(deftest unregistered-project-hard-holds-and-writes-nothing-test
  (let [db (store/seed-db)
        actor (op/build db)
        result (exec! actor "op-t3" {:op :log-progress-record :subject "proj-9"
                                      :milestone "n/a"})]
    (is (= :done (:status result)))
    (is (= :hold (get-in result [:state :disposition])))
    (is (some #(= :project-unregistered (:rule %)) (get-in result [:state :verdict :violations])))
    (is (empty? (store/progress-history db)))))

(deftest unresolved-hazard-hard-holds-any-later-op-end-to-end-test
  "The full-graph integration proof for the flagship demo's HARD-hold
  claim: once a hazard flag actually commits (through exec!+approve!,
  the real path -- not a hand-seeded fixture), a later op on the same
  project is hard-held, un-overridable, no human involved."
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "op-t4a" {:op :flag-safety-hazard :subject "proj-2"
                            :hazard-type "gas-leak" :description "odor reported"})
    (approve! actor "op-t4a")
    (is (= 1 (count (store/hazard-history db))))
    (let [result (exec! actor "op-t4b" {:op :log-progress-record :subject "proj-2"
                                         :milestone "attempted after hazard"})]
      (is (= :hold (get-in result [:state :disposition])))
      (is (some #(= :unresolved-hazard (:rule %)) (get-in result [:state :verdict :violations])))
      ;; still exactly 1 -- the held attempt must not have mutated progress-history.
      (is (empty? (store/progress-history db))))))
