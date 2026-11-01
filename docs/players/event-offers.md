---
title: Event offers
sidebar_position: 8
---

# Event offers

Sometimes the server itself needs a lot of something, for an event or a project everyone shares: 10,000 oak logs for
the harbour, a mountain of wheat for a harvest festival. Your server's staff sets this up as an **event offer**.

An offer can belong to a [server event](./server-events.md): it then starts and ends with the event, and
`/company events` lists it under the event. A goal can carry a reward: when it is reached, a short reward multiplier
event starts at once and pays extra on the orders completed while it runs.

The **Events** page of the [dashboard](./dashboard.md#events) lists the goals and contests that run, are paused or
start within a day, with their progress and time left, and for a contest your company's place. A goal's
**Show its orders** opens its chunks in the order book, and a contest's page lets members who manage orders join or
leave it. In game, `/company events` lists the same, and `[Open]` shows a goal's chunks in the order search (in the
search of orders to work on alone when you have no company), or a contest.
The server tells everyone when an offer starts, when a goal is reached and how a contest ended. Order boards with
`event` on their sign show the chunks of all goals.

## Goals

A goal is a large amount split into ordinary orders, the chunks. Every company, and every player
[working alone](./working-alone.md), can take a chunk, deliver it and get paid for it like for any order. When a
chunk is taken, the next one appears, until the whole amount is delivered or the goal ends.

- **Find them** in the dashboard's order book: chunks carry an **Event** badge, the **Event offers** filter shows
  nothing but chunks, and sorting by **Offer ends** puts the goals that end soonest first. In the in-game order
  search, chunks carry an `[Event #<id>: <name>]` badge; hovering shows how much of the goal is delivered and when it
  ends, and a click shows only that goal's chunks. The search's **Event offers** filter and **Offer end** sorting work
  there too, also in `/order work search`; the [Commands](../reference/commands.md) page has the query words.
- **Work on them** like on any order. The chunk's info names the goal and its progress. Abort a chunk you cannot
  finish, so somebody else can take it.
- **Progress** counts the items of every completed chunk, together, for the whole server. Your company's part counts
  for its level and rankings as usual.
- **When it ends**, the chunks nobody took disappear. A chunk you already took can still be delivered until its own
  delivery time, and still counts.

When a goal is reached, the server announces it in chat.

## Contests

A contest is one offer several companies take part in at once and race to deliver into. Every delivery is paid at
once, per item, and the companies that delivered most may win prizes when it closes.

- **Find them** on the dashboard's **Events** page or with `/company contest`: what each contest takes, its price per
  item, how much is delivered and how long it runs. A contest's page, or `/company contest info <id>`, shows what is
  still missing, the prizes, your company's part and place, and the ranking.
- **Join** on the contest's page or with `/company contest join <id>` once the contest started. You need the company
  permission `manage_orders`, like for accepting orders. Taking part uses one of your company's order slots until you
  leave, on the contest's page or with `/company contest leave <id>`, or the contest closes. A company that leaves keeps
  its items and its place. Players working alone cannot take part.
- **Deliver** in game, as it takes the items from your inventory: `/company contest deliver <id> <amount>` or
  `/company contest deliver <id> all`. Every member can deliver, from the inventory and a shulker box in the main hand,
  the same items an order line would take. You are paid at once: the price minus the tax of your company's level, and
  your company keeps its treasury share. The contest takes only what it still misses; the rest goes back to you.
- **Close.** The contest closes when it has everything it wanted, when its money runs out, when it ends or when the
  staff closes it. The companies are ranked by their delivered items; among equals, the one that got there first
  ranks higher. The prizes go into the treasuries of the companies on the first places, and the server announces the
  result. A contest the staff cancels pays no prizes; your deliveries were paid already.

Deliveries into contests do not count for your company's level or the rankings; the payouts and prizes show in the
company's treasury log.
