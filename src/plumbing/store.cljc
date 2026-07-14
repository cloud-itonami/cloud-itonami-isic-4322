(ns plumbing.store
  "SSoT for the plumbing/HVAC trade-coordination actor, behind a
  `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api`.

  This actor does NOT perform installation work and NEVER certifies
  installation as code-compliant/safe. It proposes coordination tasks
  (progress logging, crew dispatch, safety hazard escalation, inspection
  review requests) and routes all safety-critical decisions to licensed
  professionals. Audit trail is append-only on every backend."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [plumbing.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (project [s id])
  (all-projects [s])
  (ledger [s])
  (progress-history [s] "the append-only progress-record history")
  (dispatch-history [s] "the append-only crew-dispatch-proposal history")
  (hazard-history [s] "the append-only safety-hazard-flag history")
  (inspection-history [s] "the append-only inspection-review-request history")
  (next-progress-sequence [s jurisdiction])
  (next-dispatch-sequence [s jurisdiction])
  (next-hazard-sequence [s jurisdiction])
  (next-inspection-sequence [s jurisdiction])
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-projects [s projects] "replace/seed the project directory (map id->project)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained project set for dev/test/demo runs offline."
  []
  {:projects
   {"proj-1" {:id "proj-1" :name "Cherry Blossom Apartments Water System"
              :jurisdiction "JPN" :system-type :water :status :intake
              :licensed-professional {:name "Smith Plumbing LLC" :license "CA-P-123456"}}
    "proj-2" {:id "proj-2" :name "Downtown Tower HVAC Retrofit"
              :jurisdiction "USA" :system-type :hvac-heating :status :intake
              :licensed-professional {:name "Jones HVAC Inc." :license "TX-H-789012"}}
    "proj-3" {:id "proj-3" :name "Berlin District Heating Modernization"
              :jurisdiction "DEU" :system-type :hvac-heating :status :intake
              :licensed-professional {:name "Müller Heizung GmbH" :license "DE-HZ-456789"}}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- log-progress!
  [s project-id milestone]
  (let [p (project s project-id)
        seq-n (next-progress-sequence s (:jurisdiction p))
        result (registry/register-progress-record project-id (:jurisdiction p) seq-n milestone)]
    {:result result
     :project-patch {:last-milestone milestone}}))

(defn- dispatch-crew!
  [s project-id crew-type]
  (let [p (project s project-id)
        seq-n (next-dispatch-sequence s (:jurisdiction p))
        result (registry/register-crew-dispatch project-id (:jurisdiction p) seq-n crew-type)]
    {:result result
     :project-patch {:last-dispatch crew-type}}))

(defn- flag-hazard!
  [s project-id hazard-type description]
  (let [p (project s project-id)
        seq-n (next-hazard-sequence s (:jurisdiction p))
        result (registry/register-safety-hazard-flag project-id (:jurisdiction p) seq-n hazard-type description)]
    {:result result
     :project-patch {:has-unresolved-hazard? true}}))

(defn- request-inspection!
  [s project-id inspection-scope]
  (let [p (project s project-id)
        seq-n (next-inspection-sequence s (:jurisdiction p))
        result (registry/register-inspection-review-request project-id (:jurisdiction p) seq-n inspection-scope)]
    {:result result
     :project-patch {:inspection-review-requested? true}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (project [_ id] (get-in @a [:projects id]))
  (all-projects [_] (sort-by :id (vals (:projects @a))))
  (ledger [_] (:ledger @a))
  (progress-history [_] (:progress @a))
  (dispatch-history [_] (:dispatches @a))
  (hazard-history [_] (:hazards @a))
  (inspection-history [_] (:inspections @a))
  (next-progress-sequence [_ jurisdiction] (get-in @a [:progress-sequences jurisdiction] 0))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-hazard-sequence [_ jurisdiction] (get-in @a [:hazard-sequences jurisdiction] 0))
  (next-inspection-sequence [_ jurisdiction] (get-in @a [:inspection-sequences jurisdiction] 0))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :project/upsert
      (swap! a update-in [:projects (:id value)] merge value)

      :progress/log
      (let [project-id (first path)
            {:keys [result project-patch]} (log-progress! s project-id (:milestone payload))
            jurisdiction (:jurisdiction (project s project-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:progress-sequences jurisdiction] (fnil inc 0))
                       (update-in [:projects project-id] merge project-patch)
                       (update :progress registry/append result))))
        result)

      :crew/dispatch
      (let [project-id (first path)
            {:keys [result project-patch]} (dispatch-crew! s project-id (:crew-type payload))
            jurisdiction (:jurisdiction (project s project-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:projects project-id] merge project-patch)
                       (update :dispatches registry/append result))))
        result)

      :hazard/flag
      (let [project-id (first path)
            {:keys [result project-patch]} (flag-hazard! s project-id
                                                         (:hazard-type payload)
                                                         (:description payload))
            jurisdiction (:jurisdiction (project s project-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:hazard-sequences jurisdiction] (fnil inc 0))
                       (update-in [:projects project-id] merge project-patch)
                       (update :hazards registry/append result))))
        result)

      :inspection/request
      (let [project-id (first path)
            {:keys [result project-patch]} (request-inspection! s project-id
                                                                (:inspection-scope payload))
            jurisdiction (:jurisdiction (project s project-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:inspection-sequences jurisdiction] (fnil inc 0))
                       (update-in [:projects project-id] merge project-patch)
                       (update :inspections registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-projects [s projects] (when (seq projects) (swap! a assoc :projects projects)) s))

(defn seed-db
  "A MemStore seeded with the demo project set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :ledger []
                           :progress-sequences {} :progress []
                           :dispatch-sequences {} :dispatches []
                           :hazard-sequences {} :hazards []
                           :inspection-sequences {} :inspections []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values are stored as EDN strings so `langchain.db` doesn't
  expand them into sub-entities -- the same convention every sibling
  actor's store uses."
  {:project/id                              {:db/unique :db.unique/identity}
   :ledger/seq                              {:db/unique :db.unique/identity}
   :progress/seq                            {:db/unique :db.unique/identity}
   :dispatch/seq                            {:db/unique :db.unique/identity}
   :hazard/seq                              {:db/unique :db.unique/identity}
   :inspection/seq                          {:db/unique :db.unique/identity}
   :progress-sequence/jurisdiction          {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction          {:db/unique :db.unique/identity}
   :hazard-sequence/jurisdiction            {:db/unique :db.unique/identity}
   :inspection-sequence/jurisdiction        {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))
