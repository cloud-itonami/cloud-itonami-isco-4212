# cloud-itonami-isco-4212

Open Occupation Blueprint for **ISCO-08 4212**: Bookmakers, Croupiers and Related Gaming Workers.

This repository designs a forkable OSS business for an independent gaming-table and wagering operations practice: a chip/card handling and table-management robot performs deal and settlement tasks under a governor-gated actor, so the practice keeps its own wager records instead of renting a closed gaming-management SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a chip/card handling and table-management robot performs card dealing, chip counting and table settlement tasks under an actor that proposes
actions and an independent **Gaming Operations Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
payout above the table's registered limit) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
patron registration + wager request + table limits
        |
        v
Gaming Advisor -> Gaming Operations Governor -> accept wager/settle, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `4212`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
