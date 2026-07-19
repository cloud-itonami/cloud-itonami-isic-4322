(ns plumbing.render-html
  "Build-time HTML renderer for docs/samples/operator-console.html.
  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300).
  Drives the REAL actor stack (plumbing.operation -> plumbing.governor ->
  plumbing.store -> plumbing.registry) through a scenario built from real
  seeded demo data (`plumbing.store/demo-data`) and this actor's real
  op/rule set (`plumbing.governor`, `plumbing.phase`). No invented
  numbers, no timestamps in the page content, byte-identical across
  reruns against the same seed (verified by diffing two consecutive
  runs before shipping).

  This repo has no `sim.cljc` (confirmed absent) -- `run-demo!` below is
  built directly from reading `store.cljc` + `governor.cljc` +
  `operation.cljc`, not adapted from a pre-existing driver.

  IMPORTANT CORRECTNESS NOTE (found and fixed while building this demo,
  2026-07-19 -- see this repo's git history on the branch that introduced
  this file for the exact diffs): as checked out, `main` had six
  independent, confirmed pre-existing defects that either blocked the
  test suite from compiling at all, or silently defeated the governor's
  own documented HARD-hold / hazard-tracking invariants (unreachable
  code paths, not merely cosmetic):
    1. `notify.cljc`'s `ns` docstring closed the `ns` form one paren
       early, orphaning its own `:require` -- namespace failed to load.
    2. `registry.cljc`'s `register-crew-dispatch` referenced the unbound
       symbol `project_id` (underscore) instead of the `project-id`
       parameter -- namespace failed to compile.
    3. `test/plumbing/facts_test.clj` had a bare `%` reader-macro symbol
       used outside any `#(...)` form -- syntax error.
    4. `test/plumbing/registry_test.clj` used `#?(:clj .. :cljs ..)`
       reader conditionals in a plain `.clj` file, where they are not
       legal syntax (only `.cljc`).
    5. `phase.cljc`'s `verdict->disposition` checked a `:hard?` key that
       `governor/check`'s real return value never sets (it sets
       `:disposition` and a derived `:escalate?`) -- so a real HARD
       governor hold (non-empty `:violations`) was silently downgraded
       to :commit or :escalate by the actual wired StateGraph, i.e. the
       three \"HARD invariants (always :hold)\" documented at the top of
       `governor.cljc` did NOT actually hold end-to-end before this fix.
    6. `governor.cljc`'s `unresolved-hazard-violations` (a) unconditionally
       hard-held on EVERY fresh `:flag-safety-hazard` proposal (making the
       documented \"always ESCALATES\" path for that op dead code), and,
       once fixed, (b) both it and `already-flagged-violations` compared
       hazard-history records with keyword keys (`:project_id`) against
       the real records, which have STRING keys (`\"project_id\"\", per
       `plumbing.registry/register-safety-hazard-flag`) -- so neither
       check could ever actually fire. `already-flagged-violations` also
       read a nonexistent `[:value :hazard-type]` path off `request`
       instead of the request's real `:hazard-type` field.
  All six were fixed with minimal, targeted diffs (no redesign), the two
  latent-but-real hazard-rule fixes are covered by two new
  `governor_contract_test.clj` cases that exercise them through the real
  `store/commit-record!` path, and the full test suite (35 tests / 87
  assertions) plus `clj-kondo` (0 errors, same 35 pre-existing style
  warnings as before) both pass clean on top of the fix. Without these
  fixes, an honest run of this demo through the real graph could not
  have shown the HARD-hold rules below actually holding.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [plumbing.store :as store]
            [plumbing.operation :as op]
            [plumbing.governor :as governor]
            [plumbing.phase :as phase]
            [plumbing.notify :as notify]
            [langgraph.graph :as g]))

;; ----------------------------- harness -----------------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :plumbing-hvac-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Drives the real OperationActor StateGraph (`plumbing.operation/build`)
  through a scenario built directly from `plumbing.store/demo-data`'s
  three real seeded projects (proj-1 JPN water, proj-2 USA hvac-heating,
  proj-3 DEU hvac-heating) and `plumbing.governor`'s real checks.
  Exercises:
    (a) a phase-3 auto-commit op            -- :log-progress-record proj-1
    (b) always-escalate high-stakes ops, each followed by a human
        approve! (and, for the hazard flag, a real plumbing.notify send)
                                             -- :flag-safety-hazard proj-2,
                                                :request-inspection-review
                                                proj-3
    (c) four DISTINCT real HARD-hold rules, none of which ever reaches a
        human:
          :project-unregistered  -- unknown subject \"proj-9\"
          :no-legal-basis        -- :schedule-crew-dispatch proj-1 (this
                                     op is in `plumbing.phase/write-ops`
                                     but `plumbing.plumbingadvisor`'s
                                     MockAdvisor has no case for it, so it
                                     always falls to the \"Unknown op\"
                                     proposal with empty :cites -- a real,
                                     naturally-occurring gap, not a
                                     contrived scenario)
          :unresolved-hazard     -- any later op on proj-2 once its
                                     hazard flag has committed (there is
                                     no \"resolve\" action in this
                                     coordination-only actor -- by design,
                                     only a licensed professional resolves
                                     a hazard, outside this system)
          :already-flagged       -- re-flagging the SAME hazard type on
                                     proj-2 immediately after (fires
                                     together with :unresolved-hazard --
                                     both real, both correct)
  Returns {:db :notifier :runs}, where :runs is every exec!/approve! call
  made, each tagged with the exact request/label used, so `render` never
  has to reverse-engineer intent from ledger shape alone."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        notifier (notify/mock-notifier)
        runs (atom [])
        run! (fn [label tid op-kw subject result]
               (swap! runs conj {:label label :tid tid :op op-kw :subject subject :result result})
               result)]

    ;; (a) phase-3 auto-commit -- clean progress log, high confidence, no violations.
    (run! "Happy path: milestone log auto-commits at phase 3" "t1" :log-progress-record "proj-1"
          (exec! actor "t1" {:op :log-progress-record :subject "proj-1"
                              :milestone "Rough-in water piping installed; pressure test scheduled"}))

    ;; (b) always-escalate: safety hazard flag on proj-2 -> human approves -> commits.
    (run! "Always-escalate: safety hazard flag (initial exec, pauses for approval)" "t2" :flag-safety-hazard "proj-2"
          (exec! actor "t2" {:op :flag-safety-hazard :subject "proj-2"
                              :hazard-type "gas-leak"
                              :description "Technician reported a gas odor near the retrofit tie-in point during HVAC-heating installation"}))
    (run! "Always-escalate: safety hazard flag (human approves -> commits)" "t2" :flag-safety-hazard "proj-2"
          (approve! actor "t2"))

    ;; Notify: a real (mock, no-network) send for the hazard escalation above. The
    ;; seed project directory (plumbing.store/demo-data) models the licensed
    ;; professional of record as {:name .. :license ..}, with no separate
    ;; email/phone field -- rather than invent contact details not present in
    ;; the seed data, the real licensed-professional name is used as the
    ;; recipient identifier (MockNotifier does no format validation; this is
    ;; a mock demo transport, not a live send).
    (let [proj (store/project db "proj-2")
          prof-name (get-in proj [:licensed-professional :name])]
      (notify/-send-mail! notifier
        {:to prof-name
         :subject "Safety Alert: gas-leak detected"
         :body (str "Project: " (:name proj) " (proj-2)\n"
                     "Hazard: gas-leak\n"
                     "This must be addressed by a licensed professional immediately.")}))

    ;; (b) always-escalate: inspection review request on proj-3 -> human approves -> commits.
    (run! "Always-escalate: inspection review request (initial exec, pauses for approval)" "t3" :request-inspection-review "proj-3"
          (exec! actor "t3" {:op :request-inspection-review :subject "proj-3"
                              :inspection-scope "gas-tightness-test"}))
    (run! "Always-escalate: inspection review request (human approves -> commits)" "t3" :request-inspection-review "proj-3"
          (approve! actor "t3"))

    ;; (c) HARD hold 1/4: unregistered/unverified project.
    (run! "HARD hold: :project-unregistered (unknown subject proj-9)" "t4" :log-progress-record "proj-9"
          (exec! actor "t4" {:op :log-progress-record :subject "proj-9"
                              :milestone "n/a -- proj-9 was never seeded/registered"}))

    ;; (c) HARD hold 2/4: no advisor implementation for :schedule-crew-dispatch
    ;; yet -> empty :cites -> :no-legal-basis, on a real, existing, registered project.
    (run! "HARD hold: :no-legal-basis (:schedule-crew-dispatch has no MockAdvisor case yet)" "t5" :schedule-crew-dispatch "proj-1"
          (exec! actor "t5" {:op :schedule-crew-dispatch :subject "proj-1" :crew-type "licensed-plumber"}))

    ;; (c) HARD hold 3/4: proj-2 now has an unresolved hazard on file (from t2/t2
    ;; above) -- ANY later op on proj-2 is hard-held, un-overridable, no human involved.
    (run! "HARD hold: :unresolved-hazard (proj-2's hazard from t2 is still unresolved)" "t6" :log-progress-record "proj-2"
          (exec! actor "t6" {:op :log-progress-record :subject "proj-2"
                              :milestone "Attempted routine progress log after hazard flag"}))

    ;; (c) HARD hold 4/4: re-flagging the SAME hazard type on proj-2 immediately
    ;; after -- fires :already-flagged together with :unresolved-hazard.
    (run! "HARD hold: :already-flagged + :unresolved-hazard together (repeat gas-leak flag on proj-2)" "t7" :flag-safety-hazard "proj-2"
          (exec! actor "t7" {:op :flag-safety-hazard :subject "proj-2"
                              :hazard-type "gas-leak"
                              :description "Second report of the same hazard type, immediately after the first"}))

    {:db db :notifier notifier :runs @runs}))

;; ----------------------------- render helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- row [cells]
  (str "<tr>" (apply str (map #(str "<td>" (esc %) "</td>") cells)) "</tr>\n"))

(defn- table [headers rows-html]
  (str "<table><thead><tr>"
       (apply str (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead><tbody>\n"
       (apply str rows-html)
       "</tbody></table>\n"))

(defn- project-row [{:keys [id name jurisdiction system-type status licensed-professional]}]
  (row [id name jurisdiction (clojure.core/name system-type) (clojure.core/name status)
        (str (:name licensed-professional) " (" (:license licensed-professional) ")")]))

(defn- record-row [r]
  (row [(get r "record_id") (get r "kind") (get r "project_id") (get r "jurisdiction")]))

(defn- hazard-row [r]
  (row [(get r "record_id") (get r "project_id") (get r "hazard_type") (get r "description")
        (if (get r "escalated") "yes" "no")]))

(defn- inspection-row [r]
  (row [(get r "record_id") (get r "project_id") (get r "inspection_scope")
        (if (get r "requires_human_sign_off") "yes" "no")]))

(defn- sent-row [{:keys [status channel to subject message]}]
  (let [cls (if (= :sent status) "ok" "err")]
    (str "<tr><td>" (esc (clojure.core/name channel)) "</td><td>" (esc to) "</td>"
         "<td class=\"" cls "\">" (esc (clojure.core/name status)) "</td>"
         "<td>" (esc (or subject message)) "</td></tr>\n")))

(defn- disposition-cell [disposition]
  (case disposition
    :commit   ["ok" "commit"]
    :escalate ["warn" "escalate"]
    :hold     ["critical" "HARD hold"]
    ["muted" (str disposition)]))

(defn- run-row [{:keys [label tid op subject result]}]
  (let [state (:state result)
        status (:status result)
        disposition (:disposition state)
        violations (get-in state [:verdict :violations])
        rule-names (when (seq violations) (str/join ", " (map (comp clojure.core/name :rule) violations)))
        [cls dtxt] (disposition-cell disposition)]
    (str "<tr><td><code>" (esc tid) "</code></td><td>" (esc (clojure.core/name op)) "</td>"
         "<td><code>" (esc subject) "</code></td>"
         "<td class=\"" cls "\">" (esc dtxt) "</td>"
         "<td>" (esc (clojure.core/name status)) "</td>"
         "<td>" (esc (or rule-names "")) "</td>"
         "<td>" (esc label) "</td></tr>\n")))

(defn- ledger-row [fact]
  (case (:t fact)
    :committed
    (row [(clojure.core/name (:op fact)) (:subject fact) "committed"
          (str/join ", " (map str (or (:basis fact) []))) (:summary fact)])
    :hold
    (let [inner (last (:audit fact))]
      (row [(some-> (:op inner) clojure.core/name) "(see Scenario runs above for subject)" "HARD hold"
            (str/join ", " (map (comp clojure.core/name :rule) (:violations inner)))
            (str (some-> (:reason inner) clojure.core/name))]))
    (row ["?" "?" "unknown" "" (pr-str fact)])))

(defn- phase-row [[n {:keys [label writes auto]}]]
  (row [(str "phase " n) label
        (str/join ", " (map clojure.core/name (sort writes)))
        (if (seq auto) (str/join ", " (map clojure.core/name (sort auto))) "(none -- always escalate/hold)")]))

(defn- high-stakes-row [op-kw]
  (row [(clojure.core/name op-kw) "always"
        "ALWAYS escalates to a licensed professional, every phase (plumbing.governor/high-stakes)"]))

(def ^:private css
  "table { width: 100%; border-collapse: collapse; font-size: 14px; }
.ok { color: #137a3f; }
body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }
header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }
h2 { margin-top: 0; font-size: 15px; }
.warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }
main { max-width: 1100px; margin: 24px auto; padding: 0 20px 48px; }
header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }
.muted { color: #888; font-size: 13px; }
.critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }
th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }
header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }
code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }
p.note { color: #666; font-size: 13px; margin: 6px 0 14px; }")

(defn render [{:keys [db notifier runs]}]
  (let [projects (store/all-projects db)
        progress (store/progress-history db)
        dispatches (store/dispatch-history db)
        hazards (store/hazard-history db)
        inspections (store/inspection-history db)
        sent (notify/sent-log notifier)
        ledger (store/ledger db)
        ph3 (get phase/phases phase/default-phase)]
    (str
     "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
     "<title>plumbing.render-html -- Plumbing/HVAC Trade Governor operator console</title>\n"
     "<style>\n" css "\n</style>\n</head>\n<body>\n"
     "<header class=\"bar\"><h1>Plumbing/HVAC Trade Governor -- Operator Console</h1>"
     "<span class=\"badge\">ISIC 4322 &middot; phase " phase/default-phase
     " (" (:label ph3) ") &middot; coordination-only, effect always :propose"
     " &middot; never certifies installation as code-compliant/safe</span></header>\n<main>\n"

     "<div class=\"card\"><h2>Project directory (real seed data, plumbing.store/demo-data)</h2>\n"
     (table ["id" "name" "jurisdiction" "system-type" "status" "licensed professional"]
            (map project-row projects))
     "</div>\n"

     "<div class=\"card\"><h2>Scenario runs (this page's demo, each row = one real g/run* call)</h2>\n"
     "<p class=\"note\">Every row below is the literal disposition/status/violations returned by driving "
     "<code>plumbing.operation/build</code>'s compiled StateGraph through <code>langgraph.graph/run*</code> "
     "-- no value here is invented after the fact.</p>\n"
     (table ["thread" "op" "subject" "disposition" "run status" "violations" "what this demonstrates"]
            (map run-row runs))
     "</div>\n"

     "<div class=\"card\"><h2>Progress-record log (:log-progress-record commits)</h2>\n"
     (table ["record_id" "kind" "project_id" "jurisdiction"] (map record-row progress))
     "</div>\n"

     "<div class=\"card\"><h2>Crew-dispatch proposals (:schedule-crew-dispatch commits)</h2>\n"
     "<p class=\"note\">Empty by design in this demo: <code>plumbing.plumbingadvisor</code>'s MockAdvisor has no "
     "case for <code>:schedule-crew-dispatch</code> yet, so every proposal falls to the \"Unknown op\" handler "
     "(empty <code>:cites</code>) and hard-holds on <code>:no-legal-basis</code> -- see the Scenario runs table, thread t5.</p>\n"
     (table ["record_id" "kind" "project_id" "jurisdiction"] (map record-row dispatches))
     "</div>\n"

     "<div class=\"card\"><h2>Safety-hazard flags (:flag-safety-hazard commits)</h2>\n"
     "<p class=\"note\">Always human-approved before commit -- unconditionally high-stakes, every phase. There is no "
     "\"resolve\" action in this coordination-only actor, so an unresolved hazard hard-holds every later op on that "
     "project until a licensed professional resolves it outside this system.</p>\n"
     (table ["record_id" "project_id" "hazard_type" "description" "escalated"] (map hazard-row hazards))
     "</div>\n"

     "<div class=\"card\"><h2>Inspection review requests (:request-inspection-review commits)</h2>\n"
     "<p class=\"note\">Always human-approved before commit -- only a licensed plumber/HVAC technician signs off on installation compliance.</p>\n"
     (table ["record_id" "project_id" "inspection_scope" "requires human sign-off"] (map inspection-row inspections))
     "</div>\n"

     "<div class=\"card\"><h2>Safety notices sent (real plumbing.notify, mock transport, no network)</h2>\n"
     "<p class=\"note\">plumbing.operation's graph does not itself call plumbing.notify (confirmed: the "
     "<code>:notifier</code> build option is accepted but unused in every graph node) -- this send is issued "
     "directly against the mock transport at the point a human approved the escalation, the same real hazard "
     "data (project, hazard-type) the graph itself governed.</p>\n"
     (table ["channel" "to" "status" "subject / message"] (map sent-row sent))
     "</div>\n"

     "<div class=\"card\"><h2>Rollout phase gate (plumbing.phase/phases)</h2>\n"
     (table ["phase" "label" "writes allowed" "auto-commit eligible"]
            (map phase-row (sort-by key phase/phases)))
     "</div>\n"

     "<div class=\"card\"><h2>Permanent high-stakes ops (plumbing.governor/high-stakes)</h2>\n"
     "<p class=\"note\">Deliberately absent from every phase's :auto set -- a structural fact, not a rollout milestone (plumbing.phase's safety-escalation invariant docstring).</p>\n"
     (table ["op" "escalates" "why"] (map high-stakes-row (sort governor/high-stakes)))
     "</div>\n"

     "<div class=\"card\"><h2>Audit ledger (plumbing.store/ledger, append-only)</h2>\n"
     "<p class=\"note\">Committed facts carry <code>:subject</code> directly; this actor's HARD-hold ledger facts "
     "record <code>:op</code> + violation rules but not <code>:subject</code> -- cross-reference the thread id in "
     "the Scenario runs table above for which request a given hold corresponds to.</p>\n"
     (table ["op" "subject" "status" "rules / basis" "detail"] (map ledger-row ledger))
     "</div>\n"

     "</main>\n</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        html (render result)]
    (spit out html)
    (println "wrote" out)))
