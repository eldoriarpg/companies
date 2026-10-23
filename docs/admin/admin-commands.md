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

## Plugin

| Command | Permission | What it does |
|---|---|---|
| `/companyadmin reload` | `companies.admin.reload` | Reads the configuration files again and applies them |
| `/companyadmin syncconfiguration` | `companies.admin` | Copies the configuration between the primary server and the database |
| `/companyadmin debug` | `companies.admin.debug` | Creates a debug report for support |
| `/companyadmin node list` | `companies.admin.nodes` | Lists the servers registered in the database and whether they are connected to Redis |
| `/companyadmin node remove <uid>` | `companies.admin.nodes` | Removes the entry of a server that no longer runs, after you confirm |

Some settings are only read at start. `reload` does not apply them. The
[configuration pages](./configuration/index.md) say which ones need a restart.

`syncconfiguration` only matters on [several servers](./multiserver.md#when-the-configuration-is-copied). The same goes
for `node`, see [Old server entries](./multiserver.md#old-server-entries).

`debug` creates a report with your settings, the server setup and a check of the database, the economy and, when
used, Redis. Passwords and the Discord webhook secret are masked. See [Discord webhook](./discord-webhook.md#secrets-in-logs-and-debug-reports).

## The admin log

Every admin action writes one line to the server log. It names the admin and what was done:

```bash
[admin] Steve order cancel id=42 refund=true owner=Alex company="Iron Works"
```

This covers the order, player, grant, treasury, company, level, board and node commands, as well as `reload`,
`syncconfiguration` and `calclevel`. Looking things up is logged too: `order info`, `order search` and `player`.

Use the log to check what your staff did. Search your `latest.log` for `[admin]`.
