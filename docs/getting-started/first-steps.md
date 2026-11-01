---
title: First steps
sidebar_position: 3
---

# First steps

This short tour shows the whole loop once. A player orders items, a company claims the order and delivers it, and the
player receives the items. You can try it alone, but it is more fun with a second player.

## How it works

- **Players place orders.** An order lists items and the price paid for them. The money is taken when the order is
  placed.
- **Companies claim orders.** A company is a group of players. It claims an order and its members deliver the items.
- **Workers get paid.** When every item is delivered, each member is paid for the items they delivered.
- **The owner receives the items.** The player who placed the order picks up the items whenever they like.

Orders are anonymous. The company never sees who placed an order, and the player never sees which company worked on it.

## 1. Open the dashboard

Run `/company dashboard` in game. A window shows a link and a code: open the link in any browser, on your phone or a
PC, and enter the code. The rest of the tour happens there. See [Dashboard](../players/dashboard.md).

## 2. Place an order

In the **Order book**, press **New order**. Name the order, press **Add a line**, pick an item, and give it an amount
and a price for one item. The builder shows the cost: the items, the fee if your server has one, and the total.
**Place order** takes the total from your balance.

[Orders](../players/orders.md) explains everything about orders.

## 3. Found a company

Open **Company**. Without a company, you can found one there: name it, see the price, and pay. You are now its owner.
Invite a friend in game with `/company invite <player>`.

[Founding and joining](../companies/founding-and-joining.md) has the details.

## 4. Claim an order

Open the **Order book**, pick an order and press **Accept for my company**. It is now your company's, and it shows under
**Positions** in your **Portfolio**. From now on your company has a fixed time to deliver it.

:::note
Your company cannot claim orders that its own members placed. If you test alone, use a second player for the order.
Server owners can change this with `allowMemberOrders` in [company.yml](../admin/configuration/company-yml.md).
:::

## 5. Deliver the items

Put the items into your inventory while you are online on the server. In **Positions**, open the order and press
**Deliver what I carry**. Companies takes the matching items from your inventory. Once every line is complete, the order
is done and every member who delivered is paid.

[Claiming and delivering](../companies/claiming-and-delivering.md) explains the details.

## 6. Receive the items

The player who placed the order sees it as delivered under **Your orders** in the portfolio. **Receive into
inventory** puts the items into the inventory while that player is online. Items that do not fit wait for
**Collect waiting items**.

## The same in game

Every step also works with commands, for example while you play without a browser at hand:

| Step | Command |
|---|---|
| Place an order | `/order create <name>`, then the builder |
| Found a company | `/company create <name>` |
| Claim an order | `/company order search`, then `[Accept]` |
| Deliver | `/company order`, then `[Deliver all]` |
| Receive | `/order receive <id>`, waiting items with `/order collect` |

## Where to go next

- [Orders](../players/orders.md): prices, fees, extending and cancelling orders
- [Inbox](../players/inbox.md): the messages Companies keeps for you
- [Members and permissions](../companies/members-and-permissions.md): let your members help run the company
- [Dashboard](../players/dashboard.md): every view and what you can do there
- [Commands](../reference/commands.md): every command at a glance
