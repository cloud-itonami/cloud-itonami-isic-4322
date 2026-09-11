(ns plumbing.phase
  "Phase 0->3 staged rollout for plumbing/HVAC trade-coordination actor.
  Mirrors the pattern every sibling actor in this fleet uses.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- project intake allowed, every write needs
                                 human approval.
    Phase 2  assisted-verify  -- adds hazard flagging and inspection requests,
                                 still approval-gated.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:log-progress-record` may auto-commit.

  ## Safety escalation invariant

  `:flag-safety-hazard` and `:request-inspection-review` are deliberately
  ABSENT from every phase's `:auto` set, including phase 3 -- a permanent
  structural fact. Flagging a safety hazard and requesting a licensed
  professional's certification review are real-world safety actions that
  ALWAYS require a human (licensed plumber/HVAC technician). The
  `plumbing.governor`'s high-stakes gate enforces the same invariant
  independently -- two layers, not one, agree.")

(def read-ops  #{})
(def write-ops #{:log-progress-record :schedule-crew-dispatch
                 :flag-safety-hazard :request-inspection-review})

(def phases
  "phase -> {:label .. :writes <ops allowed> :auto <ops auto-ok if governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                        :auto #{}}
   1 {:label "assisted-intake"  :writes #{:log-progress-record}                                    :auto #{}}
   2 {:label "assisted-verify"  :writes #{:log-progress-record :flag-safety-hazard}                :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:log-progress-record}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:flag-safety-hazard` and `:request-inspection-review` are never
    auto-eligible at any phase, so they always escalate (or hold if
    governor doesn't clear them)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Plumbing Governor verdict to a base disposition before the
  phase gate. `plumbing.governor/check` returns `:disposition` (one of
  :hold/:escalate/:commit, :hold whenever `:violations` is non-empty)
  plus a derived `:escalate?` -- read `:disposition` directly rather
  than re-deriving hold-ness from a separate flag, so a governor HARD
  hold can never be silently downgraded to commit/escalate here."
  [verdict]
  (cond (= :hold (:disposition verdict)) :hold
        (:escalate? verdict) :escalate
        :else :commit))
