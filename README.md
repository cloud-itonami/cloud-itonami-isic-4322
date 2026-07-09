# cloud-itonami-4322

Open Business Blueprint for **ISIC Rev.5 4322**: plumbing, heat and
air-conditioning installation (licensed plumbing/HVAC trade work in
buildings and structures).

This repository designs a forkable OSS business for community plumbing
and HVAC installation: licensed-trade scope management, robotics-
assisted installation and inspection, and permit/commissioning records
— run by a qualified operator so a plumbing/HVAC contractor keeps its
own permit and inspection history instead of renting a closed
compliance platform.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (pipe/duct
installation, fitting, inspection) operate under an actor that
proposes actions and an independent **Plumbing Trade Governor** that
gates them. The governor never commissions an installation itself;
`:high`/`:safety-critical` actions (commissioning an installation that
has not passed inspection, any gas-line or pressure-system work
outside a licensed tradesperson's verified scope) require human
sign-off.

## Core Contract

```text
intake + identity + design specification + permit
        |
        v
Trade Advisor -> Plumbing Trade Governor -> permit, installation, inspection record, or human approval
        |
        v
robot actions (gated) + build record + inspection/commissioning record + audit ledger
```

No automated advice can commission an installation the governor
refuses, approve work outside a licensed tradesperson's verified scope,
or publish an inspection record without governor approval and audit
evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `4322`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/cae-solver`](https://github.com/kotoba-lang/cae-solver) — computer-aided engineering simulation contracts (flow/pressure/thermal calculations)

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
