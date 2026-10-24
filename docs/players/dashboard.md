---
title: Dashboard
sidebar_position: 6
---

# Dashboard

:::info New in 2.1
The web dashboard comes with Companies 2.1, on servers that switched it on. You can accept orders, manage your own
orders, your company and your inbox there; placing and delivering orders and inviting players still happen in game.
:::

The dashboard shows your orders, your company and its orders, your inbox, the rankings and market prices in a browser.
Keep it open on your phone next to the game: it updates while things happen on the server.

## Opening it

1. Run `/company dashboard`.
2. The chat shows a short link and a code, for example:

   ```
   Open companies.eldoria.de/AB12CD on any device and enter the code.
   Login code: K P M W Q R, valid for 5 minutes
   ```

   Click the link, or type it into any browser, for example on your phone.
3. Enter the six letters of the code.

The chat says "The dashboard was opened on a browser" when you are logged in. If you see that message without having
logged in, run `/company dashboard logout` right away.

The code works for five minutes. After that, run `/company dashboard` again.

## Staying logged in

You stay logged in on that browser until you log out, or until you have not used the dashboard for a while (an hour by
default) or the server's maximum time has passed (a day by default). You can be logged in on several devices at once.

| Command | What it does |
|---|---|
| `/company dashboard` | Shows a new link and code. |
| `/company dashboard sessions` | Lists the browsers you are logged in on. |
| `/company dashboard logout` | Logs you out on every browser. |

## What you see

You see what you see in game, nothing more. Your own orders show their progress, but not which company or worker
delivers them. Your company's orders show their progress, but not who placed them. Inside your company, you see which
member delivered what.

## Market prices

The market page lists the average, lowest and highest price per item of the delivered orders, the most ordered items
first. **Biggest movers** shows the items whose average price changed in the last week against the week before, the
biggest change first. Opening an item shows its average price per day over a week, a month or three months.

The price history and its changes come from orders companies delivered; orders worked on alone and lines that ask for
an exact item are not part of them.

An item or a day with fewer delivered orders than the server's minimum shows no price, and such days do not count
towards a change, so no single order can be traced back to the player who placed it.

## What you can do

Everything is checked by the server as if you typed the command, so the dashboard allows exactly what you may do in
game. When the server refuses, the dashboard shows its reason.

| Where | Action | Same as |
|---|---|---|
| Order book | Accept an order for your company (needs the *manage orders* permission) | `/company order accept <id>` |
| Positions | Deliver to an order your company works on: everything you carry for it, or one line (only while you are online) | `/company order deliver <id> all`, `<line> max` |
| Positions | Give an order your company works on back to the order book (needs the *manage orders* permission) | `/company order abort <id>` |
| Order book, Your orders | Place a new order, see below | `/order create` |
| Your orders | Give an open order more time | `/order extend <id>` |
| Your orders | Raise what an open order pays; the dashboard shows the fee and the total before you pay | `/order raise <id> <amount>` |
| Your orders | Cancel an open order; it asks again first and tells you when a repeating order stops with it | `/order cancel <id>` |
| Your orders | Receive a delivered order into your inventory; what does not fit waits to be collected (only while you are online) | `/order receive <id>` |
| Your orders | Collect the items waiting for you (only while you are online) | `/order collect` |
| Inbox | Mark everything read, delete the read entries, mute or unmute a category | `/company inbox read`, `clear`, `mute`, `unmute` |
| Company | Change a member's permissions, only those you hold yourself (needs *manage permissions*) | `/company permission <name>` |
| Company | Remove a member (needs *kick*; nobody removes the owner) | `/company kick <name>` |
| Company | Leave the company; the owner hands it over first | `/company leave` |
| Company | Hand the company to a member; you stay a member without owner rights (owner only) | `/company transfer <name>` |
| Company | Dissolve the company after typing its name: its orders go back to the order book, delivered items to the members who delivered them, the treasury is paid out to you (owner only) | `/company disband` |
| Company | Found a company when you have none; the dashboard shows the price first and takes it from your balance, also while you are not online | `/company create <name>` |
| Company | Accept or deny an application, take back an invite (needs *invite*) | `/company applications accept`, `deny`, `/company invite revoke` |
| Company | Edit description, tag and colour, change how players join (needs *edit profile*) | `/company profile edit`, `/company joinmode` |
| Company | Rename the company, paid by you or from the treasury; the dashboard shows the price first (owner only) | `/company rename <name>` |
| Company | Pay into the treasury, take money out, set the share of payouts it keeps (needs *treasury*) | `/company bank deposit`, `withdraw`, `share` |

A server owner can switch single parts of the dashboard off; their buttons then do not appear.

## Moving items

Delivering, receiving and collecting take items out of your inventory or put them in, so they work only while you are
online on the server, on whichever of its servers you are. While you are not, positions and delivered orders say so
instead of showing their buttons, and the button to collect waiting items is hidden. Delivering picks the items exactly
as the command does.

## Placing an order

**New order** in the order book or under *Your orders* opens the order builder:

1. Name the order.
2. Add lines. The item picker finds the server's materials, your [saved items](orders.md#saved-items) and the exact
   items of your earlier orders by typing.
3. For a material that can carry something, set what survival can give it: enchantments up to their highest
   survival level (never two that exclude each other), a potion type, a leather dye colour or an armour trim. Leave
   it empty to order the plain item.
4. Give each line an amount and a price per item. Where the market knows the material, its average is filled in and
   shown beside the price.
5. Switch on *Repeat this order* for a [repeating order](repeating-orders.md): every day or week, for a number of
   copies or until a day.

While you build, the server says what the order costs: the items, the fee and the total, or why it would refuse the
order. **Place order** takes the total from your balance, even while you are not online, and opens your portfolio,
where the new order shows up.

Some servers run add-ons that can stop an order from being accepted, for example because of where you stand. Those
checks only run while you are online on that server. While you are not, the dashboard accepts without them.
