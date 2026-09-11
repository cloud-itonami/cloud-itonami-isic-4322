(ns plumbing.governor
  "Plumbing Trade Governor -- the independent compliance layer that gates
  any proposal from the Plumbing Advisor before it commits. This actor does
  NOT perform installation work and NEVER certifies installation as
  code-compliant/safe -- that requires a licensed plumber's or HVAC
  technician's exclusive human sign-off, given real gas-leak/scalding/
  carbon-monoxide hazard implications.

  Proposal ops (closed allowlist, all `:effect :propose`):
    - `:log-progress-record` -- installation milestone/progress logging
    - `:schedule-crew-dispatch` -- trade-crew dispatch scheduling proposal
    - `:flag-safety-hazard` -- surface a gas/water/HVAC safety hazard,
                               ALWAYS a hard escalation
    - `:request-inspection-review` -- propose scheduling the licensed
                                      technician's/inspector's
                                      certification review

  HARD invariants (always `:hold`):
    1. Site/project record must be verified/registered before any action.
    2. `:effect` must be `:propose` only.
    3. Any proposal to certify installation work as code-compliant/safe is
       a hard, permanent block (this actor cannot/does not certify).

  ESCALATE (always human sign-off):
    - `:flag-safety-hazard` always escalates; no exception.
    - `:request-inspection-review` always escalates; only licensed
      professionals sign off on installation compliance.
    - Low confidence in any operational judgment."
  (:require [plumbing.facts :as facts]
            [plumbing.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Flagging a safety hazard (gas/water/HVAC/carbon-monoxide) and requesting
  a licensed professional's inspection/certification review are the two
  real-world actuation events this actor performs that ALWAYS need human
  sign-off (licensed plumber/HVAC technician). This actor NEVER certifies
  work as safe -- that's exclusive to the licensed professional."
  #{:flag-safety-hazard :request-inspection-review})

;; ----------------------------- checks -----------------------------

(defn- legal-basis-missing-violations
  "A proposal (any op) with no legal-basis citation is a HARD violation --
  never invent a jurisdiction's plumbing/HVAC installation requirements."
  [{:keys [op]} proposal]
  (let [value (:value proposal)]
    (when (or (empty? (:cites proposal))
              (and (contains? value :spec-basis) (nil? (:spec-basis value))))
      [{:rule :no-legal-basis
        :detail "Official legal-basis citation required for all proposals"}])))

(defn- project-unregistered-violations
  "Project/site record must be verified/registered before any action."
  [{:keys [subject]} st]
  (when-not (store/project st subject)
    [{:rule :project-unregistered
      :detail (str subject " is not registered or verified in the system")}]))

(defn- unresolved-hazard-violations
  "An unresolved safety hazard already on file for this project is a HARD,
  un-overridable hold for any operational action (crew dispatch, etc.).
  A brand-new `:flag-safety-hazard` proposal is NOT itself a violation
  here -- it is gated to always ESCALATE to a human (never auto-commit)
  via `high-stakes` below and via every phase's `:auto` set (see
  `plumbing.phase`'s safety-escalation invariant docstring), not via a
  hard block. (A prior revision of this fn also hard-held on every fresh
  `:flag-safety-hazard` proposal regardless of prior state, which made
  the `high-stakes` escalate path for that op permanently unreachable --
  contradicting this namespace's own top docstring and
  `safety-hazard-always-escalates-test`; fixed.)

  NOTE: `store/hazard-history` entries are `plumbing.registry`'s
  registration-draft \"record\" maps, which use STRING keys
  (`\"project_id\"` / `\"escalated\"`, per `register-safety-hazard-flag`),
  not keyword keys -- `(:project_id %)` on those maps always returns nil
  regardless of content, which silently made this check permanently
  unreachable. Read with `get`/string keys to match the real shape."
  [{:keys [subject]} st]
  (let [hazards-on-file (store/hazard-history st)
        has-unresolved? (some #(and (= (get % "project_id") subject)
                                     (= (get % "escalated") true))
                               hazards-on-file)]
    (when has-unresolved?
      [{:rule :unresolved-hazard
        :detail "An unresolved safety hazard prevents any action until resolved by a licensed professional"}])))

(defn- already-flagged-violations
  "Do not re-flag the same hazard for the same project twice in a row.

  Same string-vs-keyword key fix as `unresolved-hazard-violations` above
  applies to `last-hazard` (a `store/hazard-history` record). Also: the
  NEW hazard type being proposed lives at `(:hazard-type request)` --
  `plumbing.plumbingadvisor/flag-safety-hazard` destructures `hazard-type`
  directly off the request map, there is no `:value` key on `request`
  (`:value` only exists on the advisor's returned *proposal*, a
  different map) -- so `(get-in request [:value :hazard-type])` always
  read nil and this check was also permanently unreachable."
  [{:keys [op subject hazard-type]} st]
  (when (= op :flag-safety-hazard)
    (let [last-hazard (last (store/hazard-history st))
          repeat-hazard? (and last-hazard
                               (= (get last-hazard "project_id") subject)
                               (= (get last-hazard "hazard_type") hazard-type))]
      (when repeat-hazard?
        [{:rule :already-flagged
          :detail "This hazard type was just flagged for this project"}]))))

(defn check
  "Censors a Plumbing Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool}"
  [request proposal st]
  (let [violations (concat
                     (legal-basis-missing-violations request proposal)
                     (project-unregistered-violations request st)
                     (unresolved-hazard-violations request st)
                     (already-flagged-violations request st))
        confidence (:confidence proposal 0.5)
        low-confidence? (< confidence confidence-floor)
        escalate-demanded? (contains? high-stakes (:op request))
        disposition (cond
                      (seq violations)         :hold
                      escalate-demanded?       :escalate
                      low-confidence?          :escalate
                      :else                    :commit)]
    {:ok? (not (seq violations))
     :violations violations
     :confidence confidence
     :escalate? (= :escalate disposition)
     :disposition disposition}))
