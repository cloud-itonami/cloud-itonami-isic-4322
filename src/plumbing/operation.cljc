(ns plumbing.operation
  "OperationActor -- one plumbing/HVAC trade-coordination operation = one
  supervised actor run, expressed as a langgraph-clj StateGraph. The advisor
  (Plumbing Advisor) is sealed into a single node (:advise); its proposal is
  ALWAYS routed through the Plumbing Governor (:govern) and the rollout phase
  gate (:decide) before anything commits to the SSoT.

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (MemStore today; Datomic/kotoba-server next) - `store` arg
    - the Advisor  (mock | real LLM)                            - :advisor opt
    - the Notifier (mock | real mail/phone)                     - :notifier opt
    - the Phase    (0->3 rollout)                               - :phase in ctx

  One graph run = one trade-coordination operation (intake -> advise
  -> govern -> decide -> commit | hold | approval). No unbounded inner
  loop -- each operation is auditable and checkpointed.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor and hands the
  decision to a human operator (the licensed plumber/HVAC technician).
  The approver resumes with `{:approval {:status :approved}}` (or :rejected).
  `:flag-safety-hazard` and `:request-inspection-review` ALWAYS reach this
  node when the governor is clean -- see `plumbing.phase`. These are the
  safety-critical operations that never auto-commit."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [plumbing.plumbingadvisor :as plumbingadvisor]
            [plumbing.governor :as governor]
            [plumbing.notify :as notify]
            [plumbing.phase :as phase]
            [plumbing.store :as store]))

(defn- commit-fact [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(:subject request)]
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

(defn build
  "Compiles an OperationActor graph bound to `store` (any `plumbing.store/Store`).
  opts:
    :advisor      -- a `plumbing.plumbingadvisor/Advisor` (default: mock-advisor)
    :notifier     -- a `plumbing.notify/Notifier` (default: mock-notifier)
    :checkpointer -- langgraph checkpointer (default: in-mem)"
  [store & [{:keys [advisor notifier checkpointer]
             :or   {advisor      (plumbingadvisor/mock-advisor)
                    notifier     (notify/mock-notifier)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}   ; injected actor-id/role/phase
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}   ; :commit | :hold | :escalate
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      ;; Plumbing Advisor inference (the contained intelligence node) -- proposal only.
      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (plumbingadvisor/-advise advisor store request)]
            {:proposal p :audit [{:t :advised :op (:op request) :summary (:summary p)}]})))

      ;; Plumbing Governor -- independent censor (separate system than the advisor).
      (g/add-node :govern
        (fn [{:keys [request proposal]}]
          {:verdict (governor/check request proposal store)}))

      ;; Decide: governor disposition, then the rollout-phase gate.
      (g/add-node :decide
        (fn [{:keys [request verdict]}]
          (let [base (phase/verdict->disposition verdict)
                {:keys [disposition reason]} (phase/gate phase/default-phase request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [{:t :hold :op (:op request) :reason reason
                        :violations (:violations verdict)}]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :subject (:subject request)
                        :reason (or reason :escalation)
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit
               :record (commit-record request {} {})}))))

      ;; Approval handoff -- paused by interrupt-before; human resumes with :approval.
      (g/add-node :request-approval
        (fn [{:keys [request approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :audit [{:t :approval-granted :op (:op request)
                      :by (:by approval)}]}
            {:disposition :hold
             :audit [{:t :approval-rejected :op (:op request)}]})))

      ;; Commit -- the ONLY node that writes the SSoT + audit ledger.
      (g/add-node :commit
        (fn [{:keys [request proposal]}]
          (let [f (commit-fact request {} proposal)]
            (store/append-ledger! store f)
            {:audit [f]})))

      ;; Hold -- write the rejection to the ledger; no SSoT mutation.
      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when (seq audit)
            (store/append-ledger! store {:t :hold :audit audit}))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
