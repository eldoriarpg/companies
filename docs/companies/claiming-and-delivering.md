---
title: Claiming and delivering
sidebar_position: 4
---

# Claiming and delivering

This is where your company earns money. You find an order, claim it, deliver the items together, and every member who
delivered gets paid.

Orders are anonymous. You see the items, the price and the time left, but never who placed the order.

## Find orders

`/company order search` opens the order search. Fill in what you are looking for and run the search.

| Filter | What it does |
|---|---|
| Name | Only orders whose name contains this text |
| Materials | Only orders with these materials. Separate several materials with spaces. |
| Material match | `Part` finds every material whose name contains the text, so `log` finds all logs. `Exact` needs the whole material name. |
| Material search | `Any` needs one of the materials, `All` needs all of them |
| Min price, max price | The price range of the whole order |
| Min size, max size | The number of items in the order |
| Sorting | By age, name, price, size or price per item, ascending or descending |
| I can fill this | Only orders you can deliver something to with the items you carry. The orders you can fill most come first. |

**In the dialog**, the search is one form with a `Search` button. Leave prices and sizes empty for no limit.

**In chat**, choices are buttons that change the search at once. Names, materials, prices and sizes are typed as
`/company order search query …` commands, which the buttons put into your chat line. `/company order search query execute` runs the search, and
`/company order search query clear` resets it.

The results show only orders your company could claim. Orders of your own members are hidden, unless your server
allows them. Orders larger than your level allows are hidden too.

:::note
When your company already works on as many orders as its level allows, the search shows no orders. Finish an order
first.
:::

Other ways to find orders:

- [Order boards](../players/order-boards.md) on signs and NPCs open the search with a ready-made filter.
- [Order alerts](./order-alerts.md) tell you when a new order matches a saved search.

## Claim an order

You need the `manage_orders` permission. Click `[Accept]` next to an order in the results, or type
`/company order accept <id>`.

Now the order belongs to your company. Nobody else can claim it, and the clock starts. Your company has 48 hours by
default to deliver everything. Higher levels may give you more time.

The player who placed the order is told that a company accepted it, without your name. Your members get a message too.

## Your orders

| Command | What it does |
|---|---|
| `/company order` | List the orders your company works on |
| `/company order info <id>` | Show one order: its lines, what is delivered, the price after tax and the time left |

Every member can open these and deliver.

:::tip
When an order is close to its deadline, members see a reminder when they join. By default this starts 12 hours before
the deadline.
:::

## Deliver items

Put the items into your inventory and open the order with `/company order info <id>`.

- **Deliver all.** The `[Deliver all]` button delivers every matching item you carry, to every line. The command is
  `/company order deliver <id> all`.
- **One line, in chat.** Every line has buttons to deliver 1, 8, 32 or 64 items, or exactly what the line still misses.
  `[Add]` puts the command into your chat line, so you can type any amount.
- **One line, in the dialog.** Every line has a button that delivers what it still misses.
- **By command.** `/company order deliver <id> <line> <amount>` delivers up to that amount to a line.
  `/company order deliver <id> <line> max` delivers what the line misses.

A line never takes more than it misses. Extra items stay with you.

### Where the items come from

Items are taken from your inventory. If you hold a **shulker box in your main hand**, its content counts too. The box
itself is never taken. This way you can bring a full box of items and deliver it in one click.

When you have several kinds of matching items, plain items are used first, then items with more data, like enchanted
ones. Undamaged items go before damaged ones.

### Which items count

For a material line, any item of that material counts. For an exact line, the item needs the same data as the ordered
item, such as its enchantments. Extra data on your item is fine. Damaged items are not accepted unless your server
allows them. Items that hold other items, like a filled shulker box, can not be delivered.

The player who ordered gets exactly the items you delivered. Names and lore you put on an item are visible to them.

## Getting paid

As soon as every line is complete, the order is done. Every member who delivered is paid at once:

1. Each line pays its price in proportion to the items you delivered to it. If you delivered half of a line, you get
   half of its price.
2. Your server may take a tax from it. The tax depends on your company level.
3. Your company may keep a share in its [treasury](./treasury.md).

You get a message with the amount. If there was a tax or a treasury share, the message shows them too. The tax goes to
the server, not to another player.

A completed order counts towards your company level and the [rankings](./rankings-and-history.md).

## Give an order back

Sometimes an order is too much. A member with `manage_orders` can give it back with `/company order abort <id>`.
Confirm with the button or `/company order abort confirm`.

- The order is open again for every company.
- Items that were already delivered go back to the members who delivered them.
- It counts as a failed order for your company.

### Running out of time

If the deadline passes before everything is delivered, the order expires. The same happens as with giving it back:
the order is open again and delivered items go back to the workers. Your members get a message.

:::warning
Failed orders cost your company. By default, every aborted order takes 1 and every expired order takes 3 from the
number of orders your company completed. That number decides your level and your place in the ranking, so your level
can even drop.
:::

## Returned items

When delivered items go back to you, they are kept for you. You get them when you join, or with `/company returns`.
Items that do not fit into your inventory stay stored. Free up some space and use `/company returns` again.

## Limits of your level

Your company level decides:

- how many orders your company can work on at once,
- how many items an order may have at most,
- how much extra time you get for deliveries,
- how much tax is taken from payouts.

`/company` shows the limits of your level and what the next level needs. Server owners set them up, see
[Levels](../admin/levels.md).
