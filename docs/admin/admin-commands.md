---
title: Admin commands
sidebar_position: 7
---

# Admin commands

`/companyadmin` holds the tools for server staff. You can look into any order, settle disputes, help players, fix
companies and manage the plugin.

Every subcommand has its own permission, so you can give your staff only what they need. `/companyadmin` shows up
for anyone who has at least one of them. The [Commands](../reference/commands.md) and
[Permissions](../reference/permissions.md) pages list them all.

:::note Run them in game
Most admin commands only work for players. From the console you can run `debug`, `syncconfiguration`, `calclevel`,
the `board` commands and the `node` commands.
:::

## Orders

You need `companies.admin.orders`.

| Command | What it does |
|---|---|
| `/companyadmin order info <id>` | Shows an order with both sides: owner and company, times, money, the deliveries of every worker and its history |
| `/companyadmin order search player <player>` | Lists the orders of a player |
| `/companyadmin order search company <company>` | Lists the orders of a company |
| `/companyadmin order unclaim <id>` | Takes a claimed order away from its company |
| `/companyadmin order cancel <id> <refund\|keep>` | Deletes an open or claimed order |
| `/companyadmin order deliver <id>` | Closes a claimed order as delivered |
| `/companyadmin order extend <id> <hours>` | Gives an order more time, 1 to 8760 hours |

This is the one place where you see who placed and who claimed an order. Players never see that about other
players' orders.

The order view has buttons for every action. `unclaim`, `cancel` and `deliver` ask you to confirm. Click the button,
or type the confirm command:

```bash
/companyadmin order cancel 42 refund
/companyadmin order cancel confirm
```

The same works with `/companyadmin order unclaim confirm` and `/companyadmin order deliver confirm`. A confirmation is
valid for 30 seconds.

### Settling orders

Each action treats everyone fairly. Nobody is paid for work they did not do, and nobody loses items.

**Unclaim** takes a claimed order away from its company:

- The items delivered so far go back to the workers.
- The order is open again for every company.
- The company gets no penalty.

**Cancel with `refund`** deletes the order:

- The owner gets the price and the fee back.
- Delivered items go back to the workers.

**Cancel with `keep`** deletes the order without a refund:

- The owner gets nothing back.
- The price and the fee go to the [collector account](./economy.md#the-collector-account). Without a collector,
  the money leaves the economy.
- Delivered items go back to the workers.

If the order was a copy of a [repeating order](../players/repeating-orders.md), that repeating order stops and the
owner is told.

**Deliver** closes a claimed order as delivered, however much is missing:

- Each worker is paid for what they delivered, with tax and treasury share as usual.
- The owner gets the price of the missing items back.
- The fee goes to the collector.
- The owner can receive the delivered items as usual.

**Extend** gives a claimed order more delivery time, or an open order more time before it is removed. This does not
use up the owner's own extensions.

The players affected get a notification. It never names the admin, and it does not tell the owner which company had
the order or the company who the owner is.

**Orders worked on alone.** A player may work on an order alone, without a company (see
[Working alone](../players/working-alone.md)). `info` and the search lists show that player instead of a company, marked
as alone. Every action works on these orders the same way: the player gets the delivered items back and a
notification, and `deliver` pays them with the solo tax. Nothing counts for or against a company.

## System orders

You need `companies.admin.orders`. These commands also work from the console.

| Command | What it does |
|---|---|
| `/companyadmin systemorders list` | Lists the orders the server placed that are open or in work: rule, price, when placed and state |
| `/companyadmin systemorders info <id>` | Shows why the server placed an order: the rule, how its price was found, the jitter drawn, the bounds and where the money came from |
| `/companyadmin systemorders item add <rule>` | Adds the item you hold to a defined rule: an item with data as an exact entry, a plain one as a material entry. It is saved as the next revision of `system-orders.yml`, like a dashboard save, and needs `companies.admin.reload` |
| `/companyadmin systemorders stats` | Shows per material what guessed orders learned over the last `learning.days`: placed, taken and expired orders, the average minutes until one was taken, and the factor a new order pays. Placed counts every order, so it can be more than taken and expired together |

`info` also works for orders that ended: for completed ones always, for removed ones as long as the
[order history](./configuration/config-yml.md#generalsettings) keeps them. To remove a system order, use `/companyadmin order cancel <id>`. Its money always goes back to the account
that paid it, whether you choose `refund` or `keep`; minted money goes nowhere. See
[system-orders.yml](./configuration/system-orders-yml.md).

## Event offers

You need `companies.admin.offers`. These commands also work from the console, except `goal hand`.

Event offers are offers the server makes for an event or a shared goal. So far there are **goals**: a large amount
the server needs, such as 10,000 oak logs for the harbour, split into ordinary orders (chunks) that every company and
every player working alone can take. Players see them with an `[Event #<id>: <name>]` badge and can filter and sort the
order search by them, see [Event offers](../players/event-offers.md).

| Command | What it does |
|---|---|
| `/companyadmin offers goal material <name> <money> <amount> <price> <chunk> <start> <hours> <material> [budget]` | Creates a goal for any item of a material |
| `/companyadmin offers goal hand <name> <money> <amount> <price> <chunk> <start> <hours> [budget]` | Creates a goal for the item in your main hand: exactly that item when it has data (name, enchantments, ...), otherwise any item of its material |
| `/companyadmin offers goal criteria <name> <money> <amount> <price> <chunk> <start> <hours> [budget <budget>] <criteria>` | Creates a goal for every item that meets the criteria, written as in [criteria lines](../players/orders.md#criteria-lines). The criteria come last, so a budget is given as the word `budget` and the amount before them |
| `/companyadmin offers list` | Lists the 20 newest offers with their state, progress and end |
| `/companyadmin offers info <id>` | Shows who created an offer and when, what it orders, where its money comes from, its budget, what it spent and what is left, its progress, the items in open chunks and the top contributors |
| `/companyadmin offers open <id> <count>` | Changes how many chunks wait to be taken at once, 1 to 100. Only while the offer runs, is scheduled or paused |
| `/companyadmin offers pause <id>` | Pauses a running or scheduled offer |
| `/companyadmin offers resume <id>` | Resumes a paused offer |
| `/companyadmin offers cancel <id>` | Closes an offer for good |

The values of `goal`:

- `<name>`: the name players see, at most 31 characters. It is also the name of every chunk. Put it in quotes when it
  has spaces.
- `<money>`: `minted`, or the name of an economy account that pays the chunks (in quotes when it has spaces). Like
  [system orders](./configuration/system-orders-yml.md), an account pays a chunk when it is placed and gets the money
  back when the chunk is withdrawn or removed. Minted money is created when the workers are paid.
- `<amount>`: the items the goal asks for in all. `<price>`: the price per item, more than 0 and at most 1,000,000.
- `<chunk>`: the items per chunk, for example 256. The last chunk is the rest. Keep it within the order size your
  company levels allow, or small companies cannot take the chunks.
- `<start>`: hours from now until the goal starts, `0` for now. `<hours>`: how long it runs from its start. Both are
  at most 8760 hours (a year).
- `[budget]`: what the whole goal may spend. Without it, the budget is what its chunks cost at its price, each
  rounded to cents like any order. A budget must pay at least one item and may be at most 1,000,000,000,000. A
  smaller budget than the cost makes the goal close as **out of money** once it is spent.

A goal keeps 3 chunks waiting to be taken at once; change it with `open`. Example:

```bash
/companyadmin offers goal material "Harbour logs" minted 10000 0.8 256 0 72 oak_log
/companyadmin offers goal criteria "Any logs" "orders/Collector" 5000 0.5 128 2 48 budget 2000 #logs
```

### How a goal runs

The primary server looks after the goals every minute, and at once after you changed one there:

- At its start, a goal places chunks until the set number waits to be taken. When a chunk is taken, the next one is
  placed, until the whole amount is placed.
- Chunks are ordinary orders. A company or a player working alone takes one, delivers it and is paid as usual;
  extensions, aborts and expiry work as for any order. An aborted chunk waits to be taken again. A chunk nobody took
  in time is removed like any order, and its amount is placed again.
- The **progress** of a goal is the items delivered to its chunks, counted the moment a chunk is completed. It is
  stored with the goal, so it stays when the orders are purged later. `info` shows who delivered most.
- When the whole amount is delivered, the goal is **reached** within a minute, and every player on the primary
  server gets a message. In a network, players on the other servers do not see it yet. Server-wide rewards for a
  reached goal and announcements on every server come with server events.
- When its end passes first, it **ends**: the chunks waiting to be taken are withdrawn, and chunks already taken may
  still be delivered until their own delivery time. They still count for the progress.
- `pause` and `cancel` withdraw the waiting chunks the same way; `resume` places them again.
- When the budget cannot pay one more item while items are missing and no chunk is open, the goal closes early as
  **out of money**. With the default budget this only happens when a plugin raises the price of the chunks while
  they are placed: a chunk whose raised price the budget cannot pay is removed again, and the goal waits for its open
  chunks and then closes.
- An account that cannot pay a chunk places nothing; the goal tries again a minute later. The reason is logged once.

Chunks are system orders of the reserved rule `event-offer`. `/companyadmin systemorders list` lists them too, and
`/companyadmin systemorders info <id>` names the goal a chunk belongs to. They do not count for the caps and budgets
of the [system order rules](./configuration/system-orders-yml.md), nor for the network cap of minted money, and
guessed orders do not treat them as player orders. A chunk is always marked, whether `markSystemOrders` is on or not.

Every change is written to the [admin log](#the-admin-log).

## Players

| Command | Permission | What it does |
|---|---|---|
| `/companyadmin player <player>` | `companies.admin.player` | Shows a player's company and role, orders, items waiting and latest notifications |
| `/companyadmin grant items <player> [amount]` | `companies.admin.grant` | Stores the item in your main hand for the player |
| `/companyadmin grant money <player> <amount>` | `companies.admin.grant` | Pays money to the player |

The player can be offline, as long as the server knows them.

`grant items` stores the stack in your hand, or `amount` of that item (1 to 100000). The player picks it up with
`/order collect`. Use it to give back items that were lost, for example after a crash. `grant money` deposits money
through the economy plugin. The player gets a notification for both.

## Companies

| Command | Permission | What it does |
|---|---|---|
| `/companyadmin delete <company>` | `companies.admin.delete` | Dissolves a company, after you confirm |
| `/companyadmin rename <company> <name>` | `companies.admin.rename` | Renames a company for free |
| `/companyadmin transferowner <company> <player>` | `companies.admin.transferOwner` | Makes a member the owner |
| `/companyadmin treasury <company>` | `companies.admin.treasury` | Shows balance, payout share and latest transactions |
| `/companyadmin treasury give <company> <amount>` | `companies.admin.treasury` | Adds money to the treasury |
| `/companyadmin treasury take <company> <amount>` | `companies.admin.treasury` | Removes money from the treasury, down to 0 at most |

Put company names with spaces in quotes for `rename`, `transferowner` and `treasury`:

```bash
/companyadmin rename "Iron Works" Iron and Steel
/companyadmin treasury give "Iron Works" 500
```

`delete` first shows the company with its members, the orders it works on and its treasury balance, and asks you to
confirm. Confirm with the button or `/companyadmin delete confirm` within 30 seconds. It works like a disband: claimed
orders are released, and the owner gets the treasury balance.

`treasury give` and `take` create and remove money. The money does not come from or go to your own balance. Both are
written to the company's treasury log with you as the actor.

## Levels

`/companyadmin level ...` opens the level editor, and `/companyadmin calclevel` checks the level of every company
again. See [Levels](./levels.md).

## Boards

`/companyadmin board ...` lists presets, rewrites board signs and binds NPCs. See [Boards](./boards.md).

## Web dashboard

`/companyadmin dashboard status` shows the dashboard connection, `/companyadmin dashboard key <key>` stores the
dashboard key, and `/companyadmin dashboard revoke <player>` logs a player out of the dashboard. New in 2.1. See
[Web dashboard](./dashboard.md).

## Plugin

| Command | Permission | What it does |
|---|---|---|
| `/companyadmin reload` | `companies.admin.reload` | Reads the configuration files again and applies them; in a network it names the shared settings changed here |
| `/companyadmin reload confirm` | `companies.admin.reload` | Shares those settings with every server |
| `/companyadmin syncconfiguration` | `companies.admin` | Copies the configuration between the primary server and the database |
| `/companyadmin setup` | `companies.admin` | Shows what this server needs before the dashboard can take over, and lets you name it, switch the dashboard on and, with `companies.admin.dashboard`, enter its key |
| `/companyadmin setup dismiss` | `companies.admin.reload` | Hides the reminder to finish the setup for every admin |
| `/companyadmin settings status` | `companies.admin` | Shows the latest revision of each shared file, the one this server uses, and the servers still on an older one |
| `/companyadmin settings history <file> [page]` | `companies.admin` | Lists who changed a shared file, where and when |
| `/companyadmin settings rollback <file> <revision>` | `companies.admin` and `companies.admin.reload` | Asks to make an earlier revision of a shared file the newest again |
| `/companyadmin settings rollback confirm` | `companies.admin` and `companies.admin.reload` | Does it, within 30 seconds |
| `/companyadmin debug` | `companies.admin.debug` | Creates a debug report for support |
| `/companyadmin node list` | `companies.admin.nodes` | Lists the servers registered in the database and how they send each other messages (Redis, PostgreSQL notify, low latency or none) |
| `/companyadmin node remove <uid>` | `companies.admin.nodes` | Removes the entry of a server that no longer runs, after you confirm |

Some settings are only read at start. `reload` does not apply them. The
[configuration pages](./configuration/index.md) say which ones need a restart.

`syncconfiguration` and `settings` only matter on [several servers](./multiserver.md#when-the-configuration-is-copied). The same goes
for `node`, see [Old server entries](./multiserver.md#old-server-entries).

`debug` creates a report with your settings, the server setup and a check of the database, the economy and, when
used, Redis, PostgreSQL notify or low latency mode. Passwords, the Discord webhook secret and dashboard keys are masked. See [Discord webhook](./discord-webhook.md#secrets-in-logs-and-debug-reports).

## The admin log

Every admin action writes one line to the server log. It names the admin and what was done:

```bash
[admin] Steve order cancel id=42 refund=true owner=Alex company="Iron Works"
```

This covers the order, player, grant, treasury, company, level, board, node and offer commands, as well as `reload`,
`syncconfiguration` and `calclevel`. Looking things up is logged too: `order info`, `order search` and `player`.

Use the log to check what your staff did. Search your `latest.log` for `[admin]`.
