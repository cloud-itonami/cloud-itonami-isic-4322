(ns plumbing.plumbingadvisor
  "Plumbing/HVAC Advisor client -- the *contained intelligence node* for
  the trade-coordination actor.

  It logs progress milestones, proposes crew dispatch scheduling, flags
  safety hazards, and proposes inspection review requests. CRITICAL: it is
  an advisor, never a decision-maker. It returns a *proposal* (with
  rationale and sources), never a committed record or a real action. Every
  output is censored downstream by `plumbing.governor` and phase-gated
  before anything touches the SSoT.

  This is a deterministic mock so the actor graph runs offline and the
  governor contract is exercised end-to-end. In production this calls a
  real LLM with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the legal-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; operation type
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [plumbing.facts :as facts]
            [plumbing.store :as store]))

(defn- normalize-intake
  "Directory upsert -- the advisor only normalizes/validates the patch;
  it does not invent the project, its details, or its jurisdiction.
  High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "プロジェクト記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :project/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- log-progress
  "Progress milestone logging -- the advisor drafts a milestone record.
  Low stakes, high confidence."
  [db {:keys [subject milestone]}]
  (let [p (store/project db subject)]
    (if (nil? p)
      {:summary "対象プロジェクト記録が見つかりません"
       :rationale "no project record"
       :cites [] :effect :progress/log :value {:milestone "unknown"}
       :stake nil :confidence 0.0}
      {:summary    (str (:name p) ": " milestone)
       :rationale  "インストール進捗のログ記録。低リスク、高信頼度。"
       :cites      ["progress-log-standard"]
       :effect     :progress/log
       :value      {:milestone milestone}
       :stake      nil
       :confidence 0.95})))

(defn- flag-safety-hazard
  "Safety hazard flag proposal -- ALWAYS escalates. Hazard types are
  gas-leak, water-leak, scalding, carbon-monoxide, backflow-contamination."
  [db {:keys [subject hazard-type description]}]
  (let [p (store/project db subject)]
    (if (nil? p)
      {:summary "対象プロジェクト記録が見つかりません"
       :rationale "no project record"
       :cites [] :effect :hazard/flag :value {:hazard-type "unknown"}
       :stake nil :confidence 0.0}
      (let [sb (facts/spec-basis (:jurisdiction p))
            known-hazards (facts/hazard-types (:jurisdiction p))]
        (if (nil? sb)
          {:summary    (str (:jurisdiction p) " の公式legal-basisが見つかりません")
           :rationale  "plumbing.facts に未登録の法域。要件を推測で作らない。"
           :cites      []
           :effect     :hazard/flag
           :value      {:hazard-type hazard-type :description description}
           :stake      :flag-safety-hazard
           :confidence 0.8}
          {:summary    (str (:name p) ": SAFETY ALERT - " hazard-type)
           :rationale  (str "法的根拠: " (:legal-basis sb) " / "
                           "ハザード種別: " hazard-type " / "
                           "説明: " description)
           :cites      [(:legal-basis sb) (:provenance sb) hazard-type]
           :effect     :hazard/flag
           :value      {:hazard-type hazard-type :description description}
           :stake      :flag-safety-hazard
           :confidence 0.9})))))

(defn- request-inspection-review
  "Inspection review request proposal -- ALWAYS escalates to a licensed
  professional. The advisor drafts the request; a licensed plumber/HVAC
  technician must approve and sign off."
  [db {:keys [subject inspection-scope]}]
  (let [p (store/project db subject)]
    (if (nil? p)
      {:summary "対象プロジェクト記録が見つかりません"
       :rationale "no project record"
       :cites [] :effect :inspection/request :value {:inspection-scope "unknown"}
       :stake nil :confidence 0.0}
      (let [sb (facts/spec-basis (:jurisdiction p))
            checklist (facts/inspection-checklist (:jurisdiction p))]
        (if (nil? sb)
          {:summary    (str (:jurisdiction p) " の公式legal-basisが見つかりません")
           :rationale  "plumbing.facts に未登録の法域。要件を推測で作らない。"
           :cites      []
           :effect     :inspection/request
           :value      {:inspection-scope inspection-scope}
           :stake      :request-inspection-review
           :confidence 0.8}
          {:summary    (str (:name p) ": Request licensed professional inspection review")
           :rationale  (str "法的根拠: " (:legal-basis sb) " / "
                           "検査スコープ: " inspection-scope " / "
                           "チェックリスト: " (str/join ", " checklist))
           :cites      (concat [(:legal-basis sb) (:provenance sb)] checklist)
           :effect     :inspection/request
           :value      {:inspection-scope inspection-scope}
           :stake      :request-inspection-review
           :confidence 0.95})))))

(defprotocol Advisor
  (-advise [a st req]))

(defrecord MockAdvisor []
  Advisor
  (-advise [_a st req]
    (let [{:keys [op]} req]
      (case op
        :project/intake (normalize-intake st req)
        :log-progress-record (log-progress st req)
        :flag-safety-hazard (flag-safety-hazard st req)
        :request-inspection-review (request-inspection-review st req)
        {:summary "Unknown op" :rationale "no handler"
         :cites [] :effect :unknown :value {}
         :stake nil :confidence 0.0}))))

(defn mock-advisor
  "A deterministic advisor -- no network, pure-function proposals. Default
  for dev/tests/demo. In production this calls a real LLM."
  []
  (->MockAdvisor))

(defn -advise
  "Invoke the advisor protocol on a request."
  [advisor st req]
  (-advise advisor st req))
