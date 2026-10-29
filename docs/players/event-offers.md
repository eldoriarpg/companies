---
title: Event offers
sidebar_position: 8
---

# Event offers

Sometimes the server itself needs a lot of something, for an event or a project everyone shares: 10,000 oak logs for
the harbour, a mountain of wheat for a harvest festival. Your server's staff sets this up as an **event offer**.

## Goals

A goal is a large amount split into ordinary orders, the chunks. Every company, and every player
[working alone](./working-alone.md), can take a chunk, deliver it and get paid for it like for any order. When a
chunk is taken, the next one appears, until the whole amount is delivered or the goal ends.

- **Find them** with the order search: chunks carry an `[Event #<id>: <name>]` badge with the number of the goal,
  and hovering shows how much of the goal is delivered and when it ends. In chat, click the badge to see only that
  goal's chunks. Set the search's **Event offers** filter to `Only these` to see nothing but chunks, or sort by
  **Offer end** to see the goals that end soonest first. With the commands:
  `/company order search query offers only`, `/company order search query offer <id>` (the number from the badge;
  `0` shows every order again) and `/company order search query sorting offer_end`. The same works in
  `/order work search`.
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

- **Find them** with `/company contest`: what each contest takes, its price per item, how much is delivered and how
  long it runs. `/company contest info <id>` shows what is still missing, the prizes, your company's part and place,
  and the ranking.
- **Join** with `/company contest join <id>` once the contest started. You need the company permission
  `manage_orders`, like for accepting orders. Taking part uses one of your company's order slots until you leave with
  `/company contest leave <id>` or the contest closes. A company that leaves keeps its items and its place. Players working alone cannot take part.
- **Deliver** with `/company contest deliver <id> <amount>` or `/company contest deliver <id> all`. Every member can
  deliver, from the inventory and a shulker box in the main hand, the same items an order line would take. You are
  paid at once: the price minus the tax of your company's level, and your company keeps its treasury share. The
  contest takes only what it still misses; the rest goes back to you.
- **Close.** The contest closes when it has everything it wanted, when its money runs out, when it ends or when the
  staff closes it. The companies are ranked by their delivered items; among equals, the one that got there first
  ranks higher. The prizes go into the treasuries of the companies on the first places, and the server announces the
  result. A contest the staff cancels pays no prizes; your deliveries were paid already.

Deliveries into contests do not count for your company's level or the rankings; the payouts and prizes show in the
company's treasury log.
