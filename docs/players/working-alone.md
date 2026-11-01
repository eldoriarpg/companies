---
title: Working alone
sidebar_position: 5
---

# Working alone

You do not need a company to work on orders. You can take a single order on your own, deliver it alone and keep the
whole price. This is a good start for new players, for players who like to work alone and for small servers.

Working alone is off unless your server turns it on. Your server also decides how many orders you may take at once,
how large they may be and how long you have to deliver them.

Orders stay anonymous. The player who placed the order never learns who works on it, and you never learn who placed
it.

Working alone happens in game. The [dashboard](./dashboard.md)'s order book shows the open orders, so you can look for
one there first; whether you may take one alone is checked in game.

## Find an order

`/order work` opens the order search when you work on no order yet. `/order work search` opens it any time.

The search works like the one of companies: the same filters, the same buttons. It is explained in
[Claiming and delivering](../companies/claiming-and-delivering.md#find-orders). The results only show orders you
could take alone:

- your own orders are hidden,
- orders with more items than your server allows for working alone are hidden.

When you already work on as many orders as you may, the search shows no orders. Finish one first.

**From an order board.** A [board](./order-boards.md) shows a `[Work alone]` button when you may work alone. It opens
the same results for working alone, with the filter of the board.

## Take an order

Click `[Accept]` next to an order, or type `/order work accept <id>`. The order is yours now. Nobody else can take
it, and the clock starts. You have 24 hours by default to deliver everything.

The player who placed the order is told that their order is being worked on, without your name.

You cannot take an order alone when:

- your server has not turned working alone on, or you lack the permission,
- you are a member of a company, unless your server allows that too,
- you already work on as many orders as you may (one by default),
- the order is too large (64 items by default),
- you gave up an order recently and still wait for the cooldown.

## Your orders

| Command | What it does |
|---|---|
| `/order work` | List the orders you work on alone, with their progress. Opens the search when there are none. |
| `/order work info <id>` | Show one order: its lines, what is delivered, the price after tax and the time left |

When an order is close to its deadline, you see a reminder when you join.

## Deliver items

Delivering works exactly like in a company. Put the items into your inventory and open the order.

- **Deliver all.** The `[Deliver all]` button, or `/order work deliver <id> all`.
- **One line.** The buttons next to each line, or `/order work deliver <id> <line> <amount>`.
  `/order work deliver <id> <line> max` delivers what the line still misses.

A shulker box in your main hand counts too. Which items count is explained in
[Claiming and delivering](../companies/claiming-and-delivering.md#which-items-count).

## Getting paid

As soon as every line is complete, the order is done and you are paid at once: the whole price of the order. Your
server may take a tax from it, 0 % by default. You get a message with the amount and the tax.

The player who placed the order is told that it was delivered, just like with an order of a company.

Orders you finish alone count for nobody's company. They do not change any company level or ranking.

## Give an order back

Changed your mind? `/order work abort <id>` gives the order back. Confirm with the button or
`/order work abort confirm`.

- The order is open again for everyone.
- The items you already delivered come back to you.
- You cannot take another order alone for a while, 60 minutes by default. The confirmation tells you how long.

### Running out of time

If the deadline passes before everything is delivered, the order expires. The same happens as with giving it back:
the order is open again, your items come back to you and the cooldown starts. You get a message.

## Returned items

Items that come back to you are kept for you. You get them when you join, or with `/company returns`. You do not need
a company for that command.

## For server owners

Working alone is set up in the `solo` section of [order.yml](../admin/configuration/order-yml.md#solo). The permission
`companies.orders.solo` lets you limit it to some groups, see [Permissions](../reference/permissions.md).
