---
title: events.yml
sidebar_position: 6
---

# events.yml

How the effects of server events combine. Server events are time-limited events that lower the tax, lower the order
fee, or pay extra on top of payouts. The events themselves are stored in the database, so every server of a network
sees the same events; only the primary server starts and ends them at their times.

In a network, this file is shared. Edit it on the primary server, or in the *Settings* tab of the
[dashboard](../dashboard.md#admin-tools). See [Configuration](./index.md#in-a-network).

## Default file

```yaml
maxMultiplier: 3.0
```

## Keys

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `maxMultiplier` | number | `3.0` | The highest factor the reward multipliers of all running events together put on a payout. Multipliers multiply, so two events of `2` would pay four times the payout without this cap; with the default they pay three times, the event that started first paying its full part and the second the rest up to the cap. At least `1`. | reload |

## How effects combine

When several events run at once and apply to the same order:

- tax reductions add up, and the tax never drops below 0 %,
- fee reductions add up, and the fee never drops below 0,
- reward multipliers multiply, up to `maxMultiplier`.

See [Economy](../economy.md#server-events) for how events change payouts and fees. Tax and fee reductions come from
the events each server keeps for up to 30 seconds, so a server without a realtime message bus may apply a started or
ended event up to 30 seconds late. Reward multipliers pay from what the database holds at that moment, so one that
ended or ran out pays nothing at once, while a started one may start paying up to 30 seconds late.
