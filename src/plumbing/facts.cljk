(ns plumbing.facts
  "Per-jurisdiction plumbing and HVAC installation regulatory catalog --
  the spec-basis table the Plumbing Trade Governor checks every proposal
  against ('did the advisor cite an OFFICIAL public source for this
  jurisdiction's installation/inspection/safety requirements, or did it
  invent one?').

  This actor does NOT perform installation work, and NEVER certifies
  installation as code-compliant/safe. That requires a licensed plumber's
  or HVAC technician's exclusive human sign-off, given real gas-leak/
  scalding/carbon-monoxide hazard implications. This actor proposes
  coordination tasks (scheduling, progress logging, safety hazard flagging)
  and escalates all safety-critical decisions to a licensed professional.

  Coverage is reported HONESTLY: a jurisdiction not in this table has NO
  spec-basis, full stop -- the advisor must not fabricate one, and the
  governor holds if it tries.

  Seed values are drawn from each jurisdiction's official plumbing and
  mechanical licensing authorities (see `:provenance`). This is a STARTING
  catalog (JPN/USA/DEU), not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to `catalog`,
  cite a real source, done -- never invent a jurisdiction's requirements.

  DEU is used as the EU-jurisdiction proxy, the SAME convention
  `aerospace.facts` established (Germany for aircraft certification) --
  the Boiler and Pressure Equipment Directive and Installation
  Regulations are transposed into German law (Technische Regeln Gase –
  TRG / 12. BImSchV for gas heating).")

(def catalog
  "iso3 -> requirement map. `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2-style citation the governor requires before
  any proposal can commit. `:inspection-evidence` backs the mandatory
  inspection checklist a licensed professional must sign off on."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (労働基準監督署) / 経済産業省 ガス保安室 / 各自治体 水道局"
          :legal-basis "給水装置工事主任技術者規定（厚労省）/ ガス事業法 第22条（ガス器具の安全確認）/ 労働基準法 第75条（有害物・危険物周辺の安全義務）"
          :provenance "https://www.mhlw.go.jp/stf/seisakunitsuite/bunya/kenkou_iryou/iryou_kaigo/water/"
          :inspection-evidence ["給水管・給湯管耐圧試験記録 (water/hot-water pipe pressure-test record)"
                                "ガス配管施工検査記録 (gas-pipe installation inspection record)"
                                "一酸化炭素検査記録 (carbon-monoxide inspection record)"
                                "逆流防止装置点検記録 (backflow-prevention device inspection record)"]
          :hazard-types ["gas-leak" "water-leak" "scalding" "carbon-monoxide" "backflow-contamination"]}
   "USA" {:name "United States"
          :owner-authority "International Code Council (ICC) Plumbing/Mechanical Codes (adopted by states/localities); EPA (water safety); OSHA (worker safety during installation)"
          :legal-basis "International Plumbing Code (IPC, adopted by most jurisdictions) / International Mechanical Code (IMC) / EPA Safe Drinking Water Act (SDWA) § 1431-1445 (backflow prevention)"
          :provenance "https://www.icc.org/products-services/standards/i-codes/ipc"
          :inspection-evidence ["Pressure test record (IPC §510.4, water systems ≥ 60 psi)"
                                "Backflow-prevention device installation & inspection (EPA Rule 141.72, state-specific)"
                                "Gas appliance/vent inspection record (IMC § 501-507, per local Authority Having Jurisdiction)"
                                "Carbon-monoxide detector placement verification"]
          :hazard-types ["gas-leak" "water-leak" "scalding" "carbon-monoxide" "backflow-contamination"]}
   "DEU" {:name "Germany (EU jurisdiction proxy, see ns docstring)"
          :owner-authority "Deutsches Institut für Normung (DIN); Bundesamt für Arbeitsschutz und Arbeitsmedizin (BAuA); DVGW (Deutscher Verein des Gas- und Wasserfaches)"
          :legal-basis "DIN EN 806 (Installation of drinking-water systems) / Verordnung über Gasanlagen (GAVO, transposing Boiler & Pressure Equipment Directive 2014/68/EU) / Arbeitsschutzgesetz (ArbSchG, Framework Directive 89/391/EEC, worker safety during installation)"
          :provenance "https://www.dvgw.de/en/our-topics/drinking-water/technical-standards"
          :inspection-evidence ["Druckprobe für Trinkwasserleitungen (DIN EN 806-4, pressure-test record)"
                                "Gas-Dichtheitsprüfung (gas-tightness test per GAVO)"
                                "Rückflussverhinderer-Inspektion (backflow-prevention device, DIN EN 806-7)"]
          :hazard-types ["gas-leak" "water-leak" "scalding" "carbon-monoxide" "backflow-contamination"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4322 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `plumbing.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn inspection-checklist [iso3]
  (:inspection-evidence (spec-basis iso3) []))

(defn hazard-types [iso3]
  (:hazard-types (spec-basis iso3) []))
