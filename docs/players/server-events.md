---
title: Server events
sidebar_position: 9
---

# Server events

Now and then the server runs an event for a while: less tax, a lower order fee, or extra money on top of every payout.
Your server's staff starts them, or they come back every week, such as every weekend.

## What an event does

- **Tax reduction:** less tax on the payouts of orders completed while it runs.
- **Reward multiplier:** workers get extra on top of their payout, such as half of it extra for `×1.5`. All of the
  extra goes to the worker; your company's treasury share is taken from the normal payout only. Several multipliers
  multiply, up to a cap the server sets. One that pays from a budget can run out; then it ends early.
- **Fee reduction:** a lower fee when you place an order or raise its price.

An event can be limited to some items and to companies of some levels. An order counts when one of its lines accepts
one of the event's items; a multiplier limited to items pays only on what you delivered of them. Orders you work on
alone count only for events without a level limit. What counts is when the order is **completed**.

## Seeing events

- The **Events** page of the [dashboard](./dashboard.md#events) lists the events that run or start within a day:
  what they do, for which items and company levels, the time left and, for a multiplier with a budget, what it can
  still pay. Event offers of an event name it. The running events also lead the ticker at the top.
- In game, `/company events` lists the same, with the budget as a bar and the event's offers under it.
- The order search marks the orders an event boosts, such as `[×1.5 · tax −5]`.
- The view of an order shows what it pays with the events that run now, "if completed before" the end of the soonest
  of them.
- When you build an order, its fee already counts the fee reductions running now.
- When an event starts or ends, the server tells everyone in chat. The first time you join while an event runs, it is
  put into your [inbox](./inbox.md); on later joins you are reminded in chat. Both name the event's offers.

The values shown before you work on an order are a preview: an event may end or run out of money before you finish.
For a line that accepts several items, such as `#logs`, the preview counts a multiplier limited to one of them on the
whole line, so it shows at most what you get; the payout counts only what you deliver of those items.
