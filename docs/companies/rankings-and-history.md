---
title: Rankings and history
sidebar_position: 7
---

# Rankings and history

How does your company compare to the others? What did it do last week, and who helped the most? The ranking, the
history and the member statistics answer that.

## The ranking

`/company top` shows the ranking of all companies, one page at a time. Every company shows its tag in its colour. Hover
over a company to see its numbers, and click it to open its profile. Below the page you see the place of your own
company.

Choose what the ranking is sorted by and the time it covers. In the menu these are buttons. With the command they come
in this order:

`/company top [page] [ordering] [range]`

For example, `/company top 1 earned 30d` shows the first page of the companies that earned the most in the last 30
days. To give an ordering, also give the page. To give a range, also give the ordering.

| Ordering | Ranks by |
|---|---|
| `orders` | Completed orders |
| `earned` | Money earned with completed orders, before tax |
| `member` | Current number of members |
| `items_delivered` | Items delivered to completed orders |
| `level` | Current level. Companies on the same level are ranked by the money they earned. |

| Range | Covers |
|---|---|
| `all` | Everything since the company was founded. This is the default. |
| `30d` | The last 30 days |
| `7d` | The last 7 days |

Some things to know:

- The 30 and 7 day ranges are rolling. They always end now, so `7d` on a Wednesday covers the time since last
  Wednesday.
- Only companies that were active in a range are ranked in it. If your company did nothing in the last 7 days, you
  see a dash instead of a place.
- Members and level are always the current values. The range only decides which companies are ranked.
- In the `all` range, failed orders are subtracted from the completed orders. See
  [Claiming and delivering](./claiming-and-delivering.md#running-out-of-time).
- Pages of the ranking are kept for 30 seconds by default, so a new order may take a moment to show.

:::tip
Your server can show the ranking on scoreboards or holograms with PlaceholderAPI. See
[Placeholders](../reference/placeholders.md).
:::

## Company history

When an order ends for your company, an entry is written to its history. Every member can read it.

- `/company history [page]` lists the ended orders, newest first.
- `/company history info <entry>` shows one entry: its times, its lines with what was delivered, and the payouts with
  what every member contributed.

The entry keeps everything as it was when the order ended, even if members left since. The outcome is one of these:

| Outcome | What happened |
|---|---|
| Delivered | Your company delivered everything |
| Aborted | Your company gave the order back |
| Expired | The time ran out |
| Cancelled by an admin | An admin removed the order |
| Taken back by an admin | An admin took the order away from your company |

The history never shows who placed an order. Entries are kept for 90 days by default.

Players have a history of their own orders too, see [Orders](../players/orders.md#your-order-history).

## Member statistics

Every payout to a member is recorded. `/company member` shows for each member, for all time and for the last 30 days:

| Statistic | What it counts |
|---|---|
| Items | Items the member delivered to completed orders |
| Earned | Money the member received for them, after tax and after the treasury share |
| Orders | Completed orders the member delivered to |

Sort the list with `/company member <sorting> [all|30d]`, for example `/company member earned 30d`. The sortings are
`name`, `items`, `earned` and `orders`. More about the member list is in
[Members and permissions](./members-and-permissions.md#your-members).

Only completed orders count. Items delivered to an order that was given back or expired do not count, because they
went back to the member.
