---
title: events.yml
sidebar_position: 6
---

# events.yml

Server events: how their effects combine, how they are announced, and templates the server creates events from.
Server events are time-limited events that lower the tax, lower the order fee, or pay extra on top of payouts. Admins
create them with [`/companyadmin event`](../admin-commands.md#server-events). The events themselves are stored in the
database, so every server of a network sees the same events; only the primary server starts and ends them at their
times and creates the events of the templates.

In a network, this file is shared. Edit it on the primary server, or in the *Settings* tab of the
[dashboard](../dashboard.md#admin-tools). See [Configuration](./index.md#in-a-network).

## Default file

```yaml
maxMultiplier: 3.0
broadcast: true
joinMessage: true
createAheadHours: 24
goalRewardFunding: MINTED
goalRewardAccount: ''
goalRewardBudget: 1000.0
templates: []
```

## Keys

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `broadcast` | true/false | `true` | Whether every server tells its players in chat when an event starts, ends, runs out of money or is cancelled after it started. | reload |
| `joinMessage` | true/false | `true` | Whether a player who joins while events run is told about them in chat, see [Announcements](#announcements). | reload |
| `createAheadHours` | number (hours) | `24` | How long before its start the primary creates the next event of a template, so it can be seen coming. | reload |
| `goalRewardFunding` | `ACCOUNT` or `MINTED` | `MINTED` | Where the money of a [goal reward](../admin-commands.md#event-offers) comes from when `/companyadmin offers reward` names none. | reload |
| `goalRewardAccount` | text | empty | The account paying goal rewards funded from an account that name none. Empty for `economy.collectorAccount`. | reload |
| `goalRewardBudget` | number | `1000.0` | What a goal reward may pay when it names no budget. `0` means none, which `MINTED` does not allow. | reload |
| `templates` | list | empty | Events the server creates by itself, see [Templates](#templates). Edited in the file only. | reload |
| `maxMultiplier` | number | `3.0` | The highest factor the reward multipliers of all running events together put on a payout. Multipliers multiply, so two events of `2` would pay four times the payout without this cap; with the default they pay three times, the event that started first paying its full part and the second the rest up to the cap. At least `1`. | reload |

## Templates

A template creates an event every week, at fixed dates, or both:

```yaml
templates:
  - id: weekend
    name: "Weekend boost"
    kind: REWARD_MULTIPLIER
    value: 1.5
    materials: []
    levels: []
    every: "SATURDAY 18:00"
    dates: ["2026-12-24 18:00"]
    duration: 48h
    funding: MINTED
    account: ""
    budget: 5000.0
```

| Key | Type | Default | Meaning |
|---|---|---|---|
| `id` | text | none | Names the template; its events remember it. Use each id once, at most 64 characters. |
| `name` | text | the id | The name of its events. |
| `enabled` | true/false | `true` | Whether the template creates events. |
| `kind` | `TAX_REDUCTION`, `REWARD_MULTIPLIER` or `FEE_REDUCTION` | `TAX_REDUCTION` | What its events change. |
| `value` | number | `0.0` | Percentage points off the tax, the factor on the payout, or percent off the fee. |
| `materials` | list | empty | Items its events are limited to, such as `[WHEAT, CARROT]`. Empty for every item. |
| `levels` | list | empty | Company levels its events are limited to. Empty for every company. Not for fee reductions. |
| `every` | text | none | A weekly start: a day of the week and a time, such as `SATURDAY 18:00`. |
| `dates` | list | empty | Fixed starts, such as `"2026-12-24 18:00"`. |
| `duration` | text | `24h` | How long each event runs, such as `48h`, `2d` or `1d12h`. |
| `funding` | `ACCOUNT` or `MINTED` | none | Where the money of a reward multiplier comes from. Needed for multipliers. |
| `account` | text | empty | The account paying a multiplier funded from an account. Empty for `economy.collectorAccount` of [config.yml](./config-yml.md#economy); while that is not set either, the template creates nothing, so name an account or use `MINTED`. |
| `budget` | number | `0.0` | What each event of a multiplier may pay. `0` means no budget, which `MINTED` does not allow. |

Weekly starts and dates are times of the server's time zone, `generalSettings.timezone` of
[config.yml](./config-yml.md#generalsettings): `SATURDAY 18:00` is 18:00 on the server's clock in summer and in
winter. A start the clocks skip when they are put forward moves by the gap. The duration is real time from the start.

A template has one event at a time. Its next event is the first start at or after the planned end of the event it
created last, whatever came of that one; it is created `createAheadHours` before its start and then shows up in
`/companyadmin event list`. So a template never creates the same start twice, and never two events whose times
overlap: not after a restart, not after an admin cancelled or ended its event, and not when you change its start or
the server's time zone. If the primary was down during a whole event, that start is skipped; if it comes up while an
event of the template should run, the event is created with its planned times and starts at once.

The `id` is what makes a template the same template:

- A change of `every`, `dates`, `duration` or the time zone applies from the next start after its last event.
- A template with a new `id` counts as a new template. It may create an event at once, even while one of the old id
  runs.
- A change of `kind`, `value`, the scopes, the funding or the budget does not reach an event that was already
  created. Change its budget with `/companyadmin event budget`, or cancel it with `/companyadmin event cancel <id>`
  and create a replacement with `/companyadmin event create`. A start you cancelled stays skipped; the template
  creates its next start.
- When you disable or remove a template, its event that waits for its start is cancelled by the primary, without an
  announcement; `/companyadmin event info` names its template as the reason. Enable the template again before that
  event would have ended, and the primary creates the start again. An event of it that runs already keeps running
  until its end.
- A `duration` longer than the time to the next start skips the starts its event still covers: weekly with
  `duration: 8d` runs every second week.
- A start that lies within an event of the template, from a date or from `every`, is skipped; a date within the event
  of the date before it is also named once in the server log.
- While `events.yml` cannot be read, templates create nothing and nothing is cancelled.

Templates create events only; they carry no goals or contests. Put an offer on an event of a template with
`/companyadmin offers event <id> <event>` once the event shows up in `/companyadmin event list`.

A template with a problem, such as a weekly start that cannot be read or a value that does not fit its kind, creates
nothing. The server log names the problem once.

## Announcements

When an event starts, ends, runs out of money or is cancelled after it started, every server tells its players in
chat, while `broadcast` is on. An event cancelled before its start, and one whose whole time passed while the primary
was down, are not announced. In a network without a realtime [message bus](../multiserver.md), only the server
that started or ended the event tells its players. The [Discord webhook](../discord-webhook.md#events) posts starts and ends with
`EVENT_STARTED` and `EVENT_ENDED`.

The first time a player joins while an event runs, the event is put into the player's
[inbox](../../players/inbox.md), which shows it at once; this happens once per event and player on the whole network.
On every later join while it runs, the player is told in chat, while `joinMessage` is on.

## How effects combine

When several events run at once and apply to the same order:

- tax reductions add up, and the tax never drops below 0 %,
- fee reductions add up, and the fee never drops below 0,
- reward multipliers multiply, up to `maxMultiplier`.

See [Economy](../economy.md#server-events) for how events change payouts and fees. Tax and fee reductions come from
the events each server keeps for up to 30 seconds, so a server without a realtime message bus may apply a started or
ended event up to 30 seconds late. Reward multipliers pay from what the database holds at that moment, so one that
ended or ran out pays nothing at once, while a started one may start paying up to 30 seconds late.
