---
title: First steps
sidebar_position: 2
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

## 1. Place an order

Start an order with a name:

`/order create Building stone`

The order builder opens. Depending on the server, it is a dialog window or a menu in chat. Add a line with the
item, the amount and the price for one item:

`/order draft add stone 256 0.5`

In the dialog you use the `Add` button instead. The builder shows the lines, the total price and the fee, if your
server has one. Click `Done` or type `/order draft done` to place the order. The price and the fee are taken from your
balance.

[Orders](../players/orders.md) explains everything about the builder.

## 2. Found a company

Founding a company costs money. The price is shown before you pay.

`/company create Stone Works`

Confirm it with the button or with `/company create confirm`. You are now the owner of the company.
`/company` shows its profile. Invite a friend with `/company invite <player>`.

[Founding and joining](../companies/founding-and-joining.md) has the details.

## 3. Find and claim an order

Search the open orders:

`/company order search`

The search shows a form. Run it to see the open orders. Click `[Accept]` next to an order to claim it for your
company. From now on your company has a fixed time to deliver it.

:::note
Your company can not claim orders that its own members placed. If you test alone, use a second player for the order.
Server owners can change this with `allowMemberOrders` in [company.yml](../admin/configuration/company-yml.md).
:::

## 4. Deliver the items

Put the items into your inventory and open the order:

`/company order`

Click the order, then `[Deliver all]`. Companies takes the matching items from your inventory. Once every line is
complete, the order is done and every member who delivered is paid.

[Claiming and delivering](../companies/claiming-and-delivering.md) explains the details.

## 5. Receive the items

The player who placed the order gets a message with a `[Receive your items]` button. A click puts the items into the inventory.
`/order receive <id>` does the same. Items that do not fit wait for you. Pick them up later with `/order collect`.

## Where to go next

- [Orders](../players/orders.md): prices, fees, extending and cancelling orders
- [Inbox](../players/inbox.md): the messages Companies keeps for you
- [Members and permissions](../companies/members-and-permissions.md): let your members help run the company
- [Commands](../reference/commands.md): every command at a glance
