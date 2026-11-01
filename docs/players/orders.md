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

**Your orders** in the [dashboard](./dashboard.md)'s portfolio shows the orders you have not received yet, with their
lines, progress and price. In game, `/order list` shows them with your order limit and the items that wait for you,
and `/order info <id>` shows one order.

## Create an order

On the dashboard, **New order** in the order book or under *Your orders* opens the order builder: name the order, add
lines, give each an amount and a price, and press **Place order**. The builder shows the cost before you pay. See
[Placing an order](./dashboard.md#placing-an-order).

Nothing is paid until you place the order.

### In game

`/order create <name>` starts a draft; the name can have up to 31 characters. Add lines, check them, and click
`Done` to place the order and pay; `Cancel` throws the draft away. If you already have a draft, `/order create` shows
it again. A draft you leave alone
for 15 minutes is dropped.

The builder looks different depending on your server and your game version.

- **Dialog.** A window with buttons and input fields. `Add` opens a small form with the material, an amount slider and
  the price per item. `Add item in hand` does the same for the item you hold. Instead of a material you can type
  [criteria](#criteria-lines), such as `#logs`. Click a line to change its amount or price, or to remove it. `Repeat`
  sets up a [repeating order](./repeating-orders.md).
- **Chat.** A menu in chat. Buttons like `[Add]`, `[Add by criteria]`, `[Amount]` and `[Price]` put the matching
  `/order draft` command into your chat line, so you only type the numbers. `[Remove]` removes a line at once.

Dialogs need Minecraft 1.21.6 or newer. Players on older game versions always get the chat menu. While a chat menu is
open, other chat messages are held back. You get them once you close the menu with `[x]`.

Every `/order draft` command is on the [Commands](../reference/commands.md) page.

## Material lines, exact lines and criteria lines

Each line is one of three kinds.

- **Material line.** Any item of that material counts, for example any stone or any iron ingot. On the dashboard,
  pick a material and leave its enchantments and other details empty.
- **Exact line.** The item needs the same data as the item you ordered, such as its enchantments. On the dashboard you
  set them when you add the item; in game you get one when the item has extra data, for example
  `diamond_sword[enchantments={sharpness:5}]` or an enchanted sword in your hand.
- **Criteria line.** Any item of a set counts, mixed, and it may need enchantments. See [Criteria
  lines](#criteria-lines).

In game, `/order draft add hand` looks at the item you hold. A plain item becomes a material line. An item with extra
data becomes an exact line.

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

### Criteria lines

A criteria line asks for any item of a set instead of one material. The amount may be filled with a mix:
`64 logs` may be 40 oak logs and 24 birch logs. On the dashboard, **Add by criteria** lets you pick materials, item
tags, enchantments, a potion type and durability. In game, write the criteria as entries separated by spaces:

| Entry | Meaning |
|---|---|
| `oak_log` | A material |
| `#logs` | Every item of an item tag, here all logs |
| `enchants:2` | Every item needs at least two enchantments |
| `sharpness:3` | Every item needs Sharpness III or higher |
| `potion:swiftness` | Every item holds a Potion of Swiftness: potions, splash and lingering potions, tipped arrows |
| `durability:80` | Every item has at least 80 % of its durability left |

Some examples:

- `/order draft add criteria 64 0.8 #logs` takes any logs.
- `/order draft add criteria 32 1 oak_planks birch_planks` takes oak and birch planks, mixed.
- `/order draft add criteria 1 300 diamond_sword netherite_sword enchants:3 sharpness:4` takes one diamond or
  netherite sword with at least three enchantments, one of them Sharpness IV or higher.
- `/order draft add criteria 16 20 splash_potion lingering_potion potion:strong_healing` takes splash and lingering
  potions of Healing II, mixed.
- `/order draft add criteria 4 50 #pickaxes durability:50` takes pickaxes with at least half their durability left.

Enchanted books count the enchantments they store. Any other data, such as a name, does not matter. A potion type
is matched exactly: `potion:swiftness` does not take Long Swiftness, which is `potion:long_swiftness`. With a
durability condition, damaged items are taken as long as enough durability is left, even where the server refuses
damaged items otherwise; items without durability always meet it. The order is refused when no single item of the set
could meet the conditions, for example Sharpness on logs or a potion type on swords.

A criteria line counts as one line towards your limit, however many items it accepts. Orders with criteria lines are
found in the order search under every material they accept. They have no market average, so set the price yourself.

## Saved items

Save an item you order often, for example a sword with your favourite enchantments, and order it again without holding
it. Saving copies the item in your main hand, so it happens in game; you keep the item. The dashboard's item picker
finds your saved items by typing.

| Command | What it does |
|---|---|
| `/order items` | List your saved items |
| `/order items save [name]` | Save the item in your main hand. Without a name it is saved under its material's name. |
| `/order items remove <name>` | Forget a saved item |
| `/order draft add saved <name> <amount> <unit price>` | Add a saved item to your draft |

Names are one word of letters, digits, `_` and `-`, and upper and lower case count as the same. You can save up to
27 items, unless your server set another number. Items that cannot be ordered cannot be saved. Like the item in your
hand, a saved item with extra data becomes an exact line, a plain one a material line.

## Prices

You set a price for one item, the **unit price**. The line costs the unit price times the amount. The order costs the
sum of its lines.

The price is what companies earn. A good price gets your order claimed faster.

### The market price

For material lines the builder shows what others paid. On the dashboard the average is filled in as the price and
shown below it. In game, hover over a line in chat to see the average, the lowest and the highest price per item, and
click `[Market price]` to use it.

The market prices come from the last delivered orders of that material. They are updated about once an hour. While a
material has only a few delivered orders, the builder offers what the server's shops charge for it instead; the hover
then says the price comes from the shops. A material neither ordered nor sold in a shop has no market price. Exact
lines never offer one.

### Fees

Your server may charge a fee for each order. The builder shows the price, the fee and the total before you place the
order. Fees go to the server, not to the company.

:::warning
The fee is not refunded when you cancel an order yourself.
:::

## Your order limit

You can only have a certain number of orders at the same time. The default is 7. `/order list` shows your limit, and the
builder tells you when you reached it. Only orders that are not fully delivered yet count. Your server may give you a
different limit with a permission, see [Permissions](../reference/permissions.md).

## While nobody claimed your order

### It is removed after a while

An order that no company claims is removed after some time and you get your money back, price and fee. The default is 7
days. `/order info` shows when it will be refunded. You get a warning in your [inbox](./inbox.md) before that, 24 hours
before by default.

### Extend it

**More time** under *Your orders* on the dashboard, `/order extend <id>` or the `Extend` button starts the waiting time
again. You can extend an order a few times, 2 times by default. The button shows how many extensions are left.


### Raise the price

If nobody takes your order, offer more money. **Raise payout** under *Your orders* on the dashboard adds an amount to
the price of the order; in game, `/order raise <id> <amount>` or the amount field in the order view does the same. The
increase is spread over the lines, so every unit price goes up a little.

You confirm the raise first. The confirmation shows what you pay. If your server takes a percentage fee, you also pay
that percentage on the increase. A fixed fee is not charged again.

### Cancel it

**Cancel** under *Your orders* on the dashboard, or `/order cancel <id>`, cancels an order that nobody claimed yet.
You get the price back. The fee is kept.

Once a company claimed your order, you cannot cancel it, extend it or raise its price.

## While a company works on it

You get a message when a company claims your order. The company then has a fixed time to deliver. The order view
shows when the order is due.

If the company gives the order back or runs out of time, your order is open again and waits for the next company. You
get a message when that happens. Items that were already delivered go back to the workers, not to you.

## Receive your items

When everything is delivered, you get a message. Until you receive the order, you are reminded each time you join.

While you are online on the server, press **Receive into inventory** under *Your orders* on the dashboard, or click
`[Receive your items]` in game. Items that do not fit are kept for you. Free up some space and get them with
**Collect waiting items**. In game, `/order receive <id>` and `/order collect` do the same.

:::tip
Admins can also give you items through Companies. Those wait for you in the same place, so collecting picks them up
too.
:::

## Your order history

Once an order is over for you, it moves to your history, which you read in game. This happens when you received it, when
you cancelled it, when it was removed because nobody claimed it, or when an admin cancelled it.

- `/order history [page]` lists your ended orders.
- `/order history info <entry>` shows one of them with its items, price, fee and outcome.

Entries are kept for 90 days by default.
