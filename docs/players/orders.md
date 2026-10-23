---
title: Orders
sidebar_position: 1
---

# Orders

An order asks the companies of your server for items. You say what you want and what you pay for it. A company claims
the order, its members deliver the items, and you pick them up.

Every order is anonymous. Companies see the items and the price, but never who placed the order. You never see which
company worked on it.

## The life of an order

| State | What it means |
|---|---|
| Unclaimed | The order is posted and waits for a company. You can still change the price, extend or cancel it. |
| Claimed | A company works on it. It has a fixed time to deliver everything. |
| Delivered | Every item is there. You can receive the items. |
| Received | You took the items. The order is finished and moves to your history. |

`/order` or `/order list` shows your orders that you have not received yet, your order limit and items that wait for
you. Click an order or use `/order info <id>` to see one order with its lines, progress and price.

## Create an order

You build an order as a draft first. Nothing is paid until you finish it.

1. Start a draft with `/order create <name>`. The name can have up to 31 characters.
2. Add lines: one line per item you want.
3. Check the lines, the amounts and the prices.
4. Click `Done` or type `/order draft done`. The order is posted and the money is taken.

If you already have a draft, `/order create` shows it again instead of starting a new one. `/order draft cancel` or
the `Cancel` button throws the draft away. A draft you leave alone for 15 minutes is dropped.

### Dialog or chat

The builder looks different depending on your server and your game version.

- **Dialog.** A window with buttons and input fields. `Add` opens a small form with the material, an amount slider and
  the price per item. `Add item in hand` does the same for the item you hold. Click a line to change its amount or
  price, or to remove it. `Repeat` sets up a [repeating order](./repeating-orders.md).
- **Chat.** A menu in chat. Buttons like `[Add]`, `[Amount]` and `[Price]` put the matching `/order draft` command into
  your chat line, so you only type the numbers. `[Remove]` removes a line at once.

Dialogs need Minecraft 1.21.6 or newer. Players on older game versions always get the chat menu. While a chat menu is
open, other chat messages are held back. You get them once you close the menu with `[x]`.

### Commands of the draft

You can do everything with commands too. Line numbers start at 1.

| Command | What it does |
|---|---|
| `/order draft add <item> <amount> <unit price>` | Add a line |
| `/order draft add hand <amount> <unit price>` | Add the item in your main hand |
| `/order draft amount <line> <amount>` | Change the amount. `0` removes the line. |
| `/order draft price <line> <unit price>` | Change the price for one item |
| `/order draft price <line> market` | Use the market average as the price |
| `/order draft remove <line>` | Remove a line |
| `/order draft name <name>` | Rename the order |
| `/order draft done` | Place the order and pay |
| `/order draft cancel` | Throw the draft away |

All order commands are listed on the [Commands](../reference/commands.md) page.

## Material lines and exact lines

Each line is one of two kinds.

- **Material line.** Any item of that material counts, for example any stone or any iron ingot.
  `/order draft add stone 64 0.5` makes one.
- **Exact line.** The item needs the same data as the item you ordered, such as its enchantments. You get one when
  the item has extra data, for example `diamond_sword[enchantments={sharpness:5}]` or an enchanted sword in your hand.

`/order draft add hand` looks at the item you hold. A plain item becomes a material line. An item with extra data
becomes an exact line.

Some things to know:

- Items that hold other items, like a filled shulker box or bundle, cannot be ordered.
- Names, lore and repair costs are removed from the item you order. Workers do not see them, and they do not count
  when items are delivered.
- Delivered items may carry more than you asked for. A worker may deliver a sword with an extra enchantment.
- Damaged items are not accepted by default.
- You get exactly the items that were delivered, with all their data.

Your server decides which item data counts. See `matching` in [order.yml](../admin/configuration/order-yml.md).

Adding the same item twice merges it into the existing line. An order has a limit of lines and of items in total. The
builder shows both, for example `Lines: 2/5` and `Items: 192/512`. If you ask for more items than fit, the amount is
lowered to what fits.

## Prices

You set a price for one item, the **unit price**. The line costs the unit price times the amount. The order costs the
sum of its lines.

The price is what companies earn. A good price gets your order claimed faster.

### The market average

For material lines the builder shows what others paid. Hover over a line in chat to see the average, the lowest and
the highest price per item. Click `[Market avg]` or type `/order draft price <line> market` to use the average.

The market prices come from the last delivered orders of that material. They are updated about once an hour. A
material nobody ordered yet has no market price. Exact lines never offer one.

### Fees

Your server may charge a fee for each order. The builder shows the price, the fee and the total before you place the
order. Fees go to the server, not to the company.

:::warning
The fee is not refunded when you cancel an order yourself.
:::

## Your order limit

You can only have a certain number of orders at the same time. The default is 7. `/order list` shows your limit, and
`/order create` tells you when you reached it. Only orders that are not fully delivered yet count. Your server may give you a
different limit with a permission, see [Permissions](../reference/permissions.md).

## While nobody claimed your order

### It is removed after a while

An order that no company claims is removed after some time and you get your money back, price and fee. The default is
7 days. `/order info` shows when it will be refunded. You get a warning in your [inbox](./inbox.md) before that, 24 hours before by default.

### Extend it

`/order extend <id>` or the `Extend` button starts the waiting time again. You can extend an order a few times, 2 times
by default. The button shows how many extensions are left.

### Raise the price

If nobody takes your order, offer more money. `/order raise <id> <amount>` adds the amount to the price of the order.
In the dialog you enter the amount in the order view. The increase is spread over the lines, so every unit price goes
up a little.

You confirm the raise first. The confirmation shows what you pay. If your server takes a percentage fee, you also pay
that percentage on the increase. A fixed fee is not charged again.

### Cancel it

`/order cancel <id>` cancels an order that nobody claimed yet. You get the price back. The fee is kept.

Once a company claimed your order, you cannot cancel it, extend it or raise its price.

## While a company works on it

You get a message when a company claims your order. The company then has a fixed time to deliver. The order view
shows when the order is due.

If the company gives the order back or runs out of time, your order is open again and waits for the next company. You
get a message when that happens. Items that were already delivered go back to the workers, not to you.

## Receive your items

When everything is delivered, you get a message with a `[Receive your items]` button. Until you receive the order, you
are reminded each time you join.

Click the button or type `/order receive <id>`. The items go into your inventory. Items that do not fit are kept for
you. Free up some space and get them with `/order collect`. `/order list` shows how many items are waiting.

:::tip
Admins can also give you items through Companies. Those wait for you in the same place, so `/order collect` picks them
up too.
:::

## Your order history

Once an order is over for you, it moves to your history. This happens when you received it, when you cancelled it,
when it was removed because nobody claimed it, or when an admin cancelled it.

- `/order history [page]` lists your ended orders.
- `/order history info <entry>` shows one of them with its items, price, fee and outcome.

Entries are kept for 90 days by default.
