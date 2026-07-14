# cloud-itonami-isco-4212

Open Occupation Blueprint for **ISCO-08 4212**: Bookmakers, Croupiers and Related Gaming Workers.

This repository designs a forkable OSS business for an independent gaming-table and wagering operations practice: a chip/card handling and table-management robot performs deal and settlement tasks under a governor-gated actor, so the practice keeps its own wager records instead of renting a closed gaming-management SaaS.

**Maturity: `:implemented`.** `src/gaming/` implements the
`GamingOperationsActor` as a `langgraph.graph/state-graph`
(`gaming.actor`) wired to a `Gaming Advisor` (`gaming.advisor`) and an
independent `GamingOperationsGovernor` (`gaming.governor`), following
the itonami actor pattern (ADR-2607011000): `:intake -> :advise ->
:govern -> :decide -+-> :commit (:ok?) +-> :request-approval (:escalate?,
human-in-the-loop interrupt) +-> :hold (:hard?)`. 14 tests / 29 assertions
green (`clojure -M:test`). HARD invariants (always hold, never
overridable): client provenance, no-actuation (`:effect` must be
`:propose`), a registered table basis for any wager-settlement
proposal, the proposed payout amount not exceeding the table's
registered payout ceiling (a payout beyond the table's registered
limit is an unauthorized payout, not a lucky night), and verified
patron age/identity before any wager can be settled (settling a wager
without verification is an underage/fraud risk, not efficient
service). Always-escalate ops (human sign-off regardless of
confidence, mapping this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:approve-over-limit-payout` and `:approve-unverified-wager`.

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
