# Business Model: Community Plumbing and HVAC Installation

## Classification
- Repository: `cloud-itonami-4322`
- ISIC Rev.5: `4322` — plumbing, heat and air-conditioning installation
- Social impact: worker safety, water safety, consumer protection

## Customer
- independent plumbing/HVAC contractors needing an auditable
  permit/inspection platform
- property owners and general contractors needing verifiable
  plumbing/HVAC-work records
- inspection authorities needing verifiable commissioning records
- programs that cannot accept closed, unauditable permit platforms

## Offer
- licensed-tradesperson scope and permit management
- robotics-assisted installation and inspection
- build and design-specification records
- permit and inspection/commissioning records
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per contract/site
- support retainer with SLA
- installation/inspection robot integration and maintenance

## Trust Controls
- a robot action the governor refuses is never dispatched
- safety-critical actions (commissioning an installation that has not
  passed inspection, gas-line or pressure-system work outside a
  licensed tradesperson's verified scope) require human sign-off
- an installation cannot be commissioned outside its verified
  inspection scope
- inspection and commissioning records require source verification
  evidence
- sensitive customer and site data stays outside Git
