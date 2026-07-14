(ns plumbing.registry
  "Pure-function progress-record / crew-dispatch / safety-hazard-flag /
  inspection-review-request construction -- an append-only plumbing/HVAC
  trade-coordination book-of-record draft.

  This actor does NOT perform installation work, and NEVER certifies
  installation as code-compliant/safe. That requires a licensed plumber's
  or HVAC technician's exclusive human sign-off. This namespace builds
  coordination RECORDS an operator would keep -- progress logs, crew
  dispatch proposals, safety hazard flags (always escalate), and inspection
  review requests (always escalate to a licensed professional for sign-off).

  Every operator/jurisdiction assigns its own reference format. This
  namespace does NOT invent one; it builds a jurisdiction-scoped sequence
  number and validates the record's required fields, the same honest,
  non-fabricating discipline `plumbing.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real licensing/permitting authority, no mail/phone send.
  It builds the RECORD an operator would keep (that is `plumbing.operation`'s
  `:effect` ops + `plumbing.notify`, always human-gated for safety concerns)."
  (:require [clojure.string :as str]
            [plumbing.facts :as facts]))

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-progress-record
  "Validate + construct the PROGRESS-RECORD registration DRAFT -- the
  operator's own milestone/progress log (e.g., 'rough-in pipes installed',
  'pressure test scheduled', 'inspection passed'). Pure function."
  [project-id jurisdiction sequence milestone]
  (when-not (and project-id (not= project-id ""))
    (throw (ex-info "progress-record: project_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "progress-record: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "progress-record: sequence must be >= 0" {})))
  (when-not (and milestone (not= milestone ""))
    (throw (ex-info "progress-record: milestone required" {})))
  (let [record-number (str (str/upper-case jurisdiction) "-PRG-" (zero-pad sequence 6))]
    {"record" {"record_id" record-number "kind" "progress-record-draft"
               "project_id" project-id "jurisdiction" jurisdiction
               "milestone" milestone "immutable" true}
     "record_number" record-number}))

(defn register-crew-dispatch
  "Validate + construct the CREW-DISPATCH-PROPOSAL registration DRAFT --
  a proposal to schedule trade-crew dispatch for installation/inspection
  work. Pure function -- `plumbing.governor` independently verifies site
  record exists before this is allowed to commit."
  [project-id jurisdiction sequence crew-type]
  (when-not (and project-id (not= project-id ""))
    (throw (ex-info "crew-dispatch: project_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "crew-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "crew-dispatch: sequence must be >= 0" {})))
  (when-not (and crew-type (not= crew-type ""))
    (throw (ex-info "crew-dispatch: crew_type required" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-DSP-" (zero-pad sequence 6))]
    {"record" {"record_id" dispatch-number "kind" "crew-dispatch-proposal-draft"
               "project_id" project_id "jurisdiction" jurisdiction
               "crew_type" crew-type "immutable" true}
     "dispatch_number" dispatch-number}))

(defn register-safety-hazard-flag
  "Validate + construct the SAFETY-HAZARD-FLAG registration DRAFT --
  a critical escalation flag (gas leak, water leak, carbon monoxide,
  backflow contamination, scalding risk, etc.). ALWAYS escalates to a
  licensed professional. Pure function."
  [project-id jurisdiction sequence hazard-type description]
  (when-not (and project-id (not= project-id ""))
    (throw (ex-info "safety-hazard-flag: project_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "safety-hazard-flag: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "safety-hazard-flag: sequence must be >= 0" {})))
  (when-not (and hazard-type (not= hazard-type ""))
    (throw (ex-info "safety-hazard-flag: hazard_type required" {})))
  (when-not (and description (not= description ""))
    (throw (ex-info "safety-hazard-flag: description required" {})))
  (let [flag-number (str (str/upper-case jurisdiction) "-HAZ-" (zero-pad sequence 6))]
    {"record" {"record_id" flag-number "kind" "safety-hazard-flag-draft"
               "project_id" project-id "jurisdiction" jurisdiction
               "hazard_type" hazard-type "description" description
               "escalated" true "immutable" true}
     "flag_number" flag-number}))

(defn register-inspection-review-request
  "Validate + construct the INSPECTION-REVIEW-REQUEST registration DRAFT --
  a request to schedule licensed plumber/HVAC technician's certification
  review (always human sign-off required). Pure function."
  [project-id jurisdiction sequence inspection-scope]
  (when-not (and project-id (not= project-id ""))
    (throw (ex-info "inspection-review-request: project_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "inspection-review-request: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "inspection-review-request: sequence must be >= 0" {})))
  (when-not (and inspection-scope (not= inspection-scope ""))
    (throw (ex-info "inspection-review-request: inspection_scope required" {})))
  (let [request-number (str (str/upper-case jurisdiction) "-INS-" (zero-pad sequence 6))]
    {"record" {"record_id" request-number "kind" "inspection-review-request-draft"
               "project_id" project-id "jurisdiction" jurisdiction
               "inspection_scope" inspection-scope "requires_human_sign_off" true
               "immutable" true}
     "request_number" request-number}))

(defn render-hazard-alert
  "Human-readable SAFETY-HAZARD-ALERT document text, citing the jurisdiction's
  legal basis for remediation requirements inline."
  [{:keys [id name jurisdiction]} hazard-type description flag-number]
  (let [{:keys [owner-authority hazard-types provenance]} (facts/spec-basis jurisdiction)
        is-known-hazard? (some #(= hazard-type %) (facts/hazard-types jurisdiction))]
    (str "# URGENT: Safety Hazard Alert\n\n"
         "Flag number: " flag-number "\n"
         "Project: " name " (" id ")\n"
         "Jurisdiction: " jurisdiction "\n"
         "Hazard type: " hazard-type "\n"
         "Description: " description "\n\n"
         "## Action required\n"
         "This hazard MUST be addressed by a licensed plumber or HVAC technician "
         "BEFORE work resumes. Do NOT work around this flagged condition.\n\n"
         "## Regulatory authority\n"
         "Licensing authority: " owner-authority "\n"
         "Jurisdiction spec-basis: " provenance "\n\n"
         "## Status\nEscalated -- requires immediate licensed professional review.\n")))

(defn append [history result]
  (conj (vec history) (get result "record")))
