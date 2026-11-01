---
title: Admin commands
sidebar_position: 7
---

# Admin commands

`/companyadmin` holds the tools for server staff. You can look into any order, settle disputes, help players, fix
companies and manage the plugin.

Most of these tools are also in the *Admin* section of the [web dashboard](./dashboard.md#admin-tools), where they need
the same permissions; each section below with a tab names it.

Every subcommand has its own permission, so you can give your staff only what they need. `/companyadmin` shows up
for anyone who has at least one of them. The [Commands](../reference/commands.md) and
[Permissions](../reference/permissions.md) pages list them all.

:::note Run them in game
Most admin commands only work for players. From the console you can run `debug`, `syncconfiguration`, `calclevel`,
the `board` commands and the `node` commands.
:::

## Orders

You need `companies.admin.orders`. The same tools are in the *Orders* tab of the
[dashboard](./dashboard.md#admin-tools).

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

You need `companies.admin.orders`. These commands also work from the console. The same tools, except adding the item you
hold, are in the *System orders* tab of the [dashboard](./dashboard.md#admin-tools).

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

You need `companies.admin.offers`. These commands also work from the console, except `goal hand` and
`contest hand`.

Event offers are offers the server makes for an event or a shared goal. There are two kinds:

- **Goals**: a large amount the server needs, such as 10,000 oak logs for the harbour, split into ordinary orders
  (chunks) that every company and every player working alone can take. Players see them with an
  `[Event #<id>: <name>]` badge and can filter and sort the order search by them, and `event` boards show them.
- **Contests**: one offer several companies join at once and race to deliver into, each delivery paid at once per
  item, with optional prizes for the first places.

See [Event offers](../players/event-offers.md) for what players see. The same tools are in the *Event offers* tab of the
[dashboard](./dashboard.md#admin-tools).

| Command | What it does |
|---|---|
| `/companyadmin offers goal material <name> <money> <amount> <price> <chunk> <start> <hours> <material> [budget]` | Creates a goal for any item of a material |
| `/companyadmin offers goal hand <name> <money> <amount> <price> <chunk> <start> <hours> [budget]` | Creates a goal for the item in your main hand: exactly that item when it has data (name, enchantments, ...), otherwise any item of its material |
| `/companyadmin offers goal criteria <name> <money> <amount> <price> <chunk> <start> <hours> [budget <budget>] <criteria>` | Creates a goal for every item that meets the criteria, written as in [criteria lines](../players/orders.md#criteria-lines). The criteria come last, so a budget is given as the word `budget` and the amount before them |
| `/companyadmin offers contest material <name> <money> <amount> <price> <prizes> <start> <hours> <material> [budget]` | Creates a contest for any item of a material |
| `/companyadmin offers contest hand <name> <money> <amount> <price> <prizes> <start> <hours> [budget]` | Creates a contest for the item in your main hand |
| `/companyadmin offers contest criteria <name> <money> <amount> <price> <prizes> <start> <hours> [budget <budget>] <criteria>` | Creates a contest for every item that meets the criteria |
| `/companyadmin offers list` | Lists the 20 newest offers with their state, progress and end |
| `/companyadmin offers info <id>` | Shows who created an offer and when, what it orders, where its money comes from, its budget, what it spent and what is left and its progress. For a goal also the items in open chunks and the top contributors; for a contest the money its account paid in, the prizes, the ranking with what each company was paid, and after the close who got which prize |
| `/companyadmin offers open <id> <count>` | Changes how many chunks of a goal wait to be taken at once, 1 to 100. Only while the goal runs, is scheduled or paused |
| `/companyadmin offers pause <id>` | Pauses a running or scheduled offer |
| `/companyadmin offers resume <id>` | Resumes a paused offer |
| `/companyadmin offers cancel <id>` | Closes an offer for good |
| `/companyadmin offers event <id> <event>` | Puts an offer on a [server event](#server-events). Its start and end become the event's and follow it, also when the event is extended; it ends when the event ends, also early or when the event runs out of money, and is cancelled with the event, a contest without prizes. An offer and an event that closed already are refused, and so is an offer that started already on an event that has not |
| `/companyadmin offers standalone <id>` | Takes an offer off its server event; it keeps the times it has and runs on its own |
| `/companyadmin offers reward <id> <factor> <duration> [money] [budget]` | Gives a goal a reward: when it is reached, a reward multiplier server event of `<factor>` starts at once for `<duration>` (such as `2h`, at most 30 days) and applies to every order. `[money]` is `minted` or an economy account, `[budget]` what it may pay in all; left out, `goalRewardFunding`, `goalRewardAccount` and `goalRewardBudget` of [events.yml](./configuration/events-yml.md) apply. A reward whose event would be refused is refused here already. The reward is announced like any event. Its event ends `<duration>` after the goal was reached; if it could not be created in that time, or its event is refused, it fails, which the server log names once and `offers info` shows |
| `/companyadmin offers reward clear <id>` | Takes the reward off a goal |
| `/companyadmin offers prize <id> <place> paid\|failed` | Notes the outcome of a contest prize whose payment stopped without a known result, after you checked the company's treasury log. `failed` gives the prize's money back to the contest, or straight to its account when the contest gave back its rest already |

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

### Contests

The values of `contest` are those of `goal`, with `<prizes>` instead of `<chunk>`:

- `<price>`: what a delivery is paid per item.
- `<prizes>`: `none`, or the money of the first places, first place first, separated by `+` (or by commas in quotes),
  at most 10 places: `500+250+100`.
- `[budget]`: what the whole contest may spend, deliveries and prizes. Without it, it is what the whole amount costs
  at its price plus the prizes. The prizes are always kept back from the budget, so deliveries never use their money
  and they can always be paid. A budget must hold the prizes and one item.

```bash
/companyadmin offers contest material "Harvest race" "orders/Collector" 20000 0.4 500+250+100 0 48 wheat
```

How a contest runs:

- **Money.** With an account, the primary server takes the whole budget from the account when the contest starts.
  When the account cannot pay it, the contest waits and is tried again a minute later; the reason is logged once.
  After the close, what was not spent goes back to the account. Minted money is created when it is paid out.
- **Joining.** A member with the company permission `manage_orders` joins with `/company contest join <id>`, once the
  contest started. Taking part uses one of the company's order slots, like an order it works on, until the company
  leaves or the contest closes. Only companies take part, not players working alone. A company that leaves keeps its
  items and its place.
- **Delivering.** Any member of a company that takes part delivers with `/company contest deliver <id> <amount|all>`,
  from the inventory and a held shulker box, matched like a delivery to an order line (material, exact item or
  criteria). The items are removed. The worker is paid at once, per item at the contest's price, like a payout of an
  order: the tax of the company's level goes to the collector, the company's treasury share into its treasury. Each
  delivery is paid the rounded price of all items delivered so far less that of the items before, so all deliveries
  together cost exactly the price of the whole amount. A delivery never takes more than the contest misses or its
  money, less the prizes, can pay; when two companies deliver the last items at once, the one that comes second gets
  what is left and the rest of its items back.
- **Closing.** A contest closes when it misses nothing (**reached**, at once with the last delivery), when its money
  cannot pay one more item (**out of money**), when its end passes (**ended**) or when you cancel it. The primary
  server then ranks the companies that delivered and still exist by their delivered items (the earlier one first
  among equals; a deleted company gets no place), pays each prize into the treasury of the company on that place (to
  its owner while treasuries are switched off), gives back to the account what was not spent, and announces the first
  places to the players of every server, like a reached goal. A prize no company reached, or that could not be paid,
  stays with the contest and goes back to the account. Each prize is paid at most once, also when the server stops halfway: the
  places and prizes are written in one step before any money moves, the next check finishes what is left, and a prize
  whose payment stopped without a known result is not paid again (`info` shows it, and the log names it); check the
  company's treasury log and note the outcome with `/companyadmin offers prize <id> <place> paid|failed`; `failed`
  works once the contest gave back its rest, and then gives the prize back to the account too. The rest is given back
  only once no prize waits to be paid.
- **Cancel** closes a contest **without prizes**: every delivery was paid already, the money not spent on deliveries
  goes back to the account, and players are told it was cancelled. A contest cancelled before it started announces
  nothing.
- `pause` stops deliveries; joined companies stay. A contest paused before it started takes its money only when it
  starts after `resume`.
- Deliveries into contests do not count for company levels, rankings or member statistics, which are built from
  orders. Their payouts and prizes show in the company treasury log.
- Goals and contests have budgets of their own: their minted money does not count for `mintedDailyCap` of
  [system-orders.yml](./configuration/system-orders-yml.md).

### How a goal runs

The primary server looks after the goals every minute, and at once after you changed one there:

- At its start, a goal places chunks until the set number waits to be taken. When a chunk is taken, the next one is
  placed, until the whole amount is placed.
- Chunks are ordinary orders. A company or a player working alone takes one, delivers it and is paid as usual;
  extensions, aborts and expiry work as for any order. An aborted chunk waits to be taken again. A chunk nobody took
  in time is removed like any order, and its amount is placed again.
- The **progress** of a goal is the items delivered to its chunks, counted the moment a chunk is completed. It is
  stored with the goal, so it stays when the orders are purged later. `info` shows who delivered most.
- When the whole amount is delivered, the goal is **reached** within a minute, and every player gets a message. In
  a network the message reaches every server while the servers exchange messages (Redis, PostgreSQL notify or low
  latency mode); without that only the players on the primary server see it. Server-wide rewards for a reached goal
  come with server events.
- When a goal or contest **starts**, players are told the same way, and `/company events` lists it.
- When its end passes first, it **ends**: the chunks waiting to be taken are withdrawn, and chunks already taken may
  still be delivered until their own delivery time. They still count for the progress.
- `pause` and `cancel` withdraw the waiting chunks the same way; `resume` places them again. A goal paused before it
  started waits for the next check after `resume`, which starts and announces it.
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

## Server events

You need `companies.admin.events`. [Server events](./configuration/events-yml.md) lower the tax, lower the order fee or
pay extra on top of payouts for a time; see [Economy](./economy.md#server-events) for what they change. Events can also
come from the templates of [events.yml](./configuration/events-yml.md#templates). The same tools are in the *Server
events* tab of the [dashboard](./dashboard.md#admin-tools).

| Command | What it does |
|---|---|
| `/companyadmin event create <name>` | Opens the form of a new event: a dialog where your client shows them, otherwise in chat. Only players can use it |
| `/companyadmin event set <field> [value]` | Changes a field of your form from chat, or empties it without a value |
| `/companyadmin event save` | Creates the event of your form |
| `/companyadmin event discard` | Throws your form away |
| `/companyadmin event list` | Lists the newest 20 events with their times and state |
| `/companyadmin event info <id>` | Shows an event: effect, scope, times, funding, budget, what it paid, who created and closed it |
| `/companyadmin event end <id>` | Ends a running event now. One that has not started is refused; cancel it instead |
| `/companyadmin event cancel <id>` | Cancels an event that waits or runs. Nothing is paid back |
| `/companyadmin event extend <id> <duration>` | Moves the end later, such as by `12h` or `1d12h`. The new end may lie at most a year ahead |
| `/companyadmin event budget <id> <amount\|none>` | Sets the budget of a reward multiplier, never below what it paid; `none` only for one paid from an account |

The form has these fields:

| Field | What to enter |
|---|---|
| `kind` | `TAX_REDUCTION`, `REWARD_MULTIPLIER` or `FEE_REDUCTION` |
| `value` | Percentage points off the tax, the factor on the payout such as `1.5`, or percent off the fee |
| `materials` | Items the event is limited to, separated by commas, such as `wheat, carrot`. Empty for every item |
| `levels` | Company levels the event is limited to, such as `2, 3`. Empty for every company. Not for fee reductions |
| `start` | `now` (the default), a time from now such as `2h`, or a date such as `2026-12-24 18:00` in the server's time zone |
| `end` | A duration from the start such as `48h` (the default is `24h`), or a date |
| `funding` | For a reward multiplier: `ACCOUNT` (the default) or `MINTED` for new money |
| `account` | The economy account paying a multiplier. Empty for the collector account, `economy.collectorAccount` in [config.yml](./configuration/config-yml.md#economy). While no collector account is set, name an account or use `MINTED` |
| `budget` | What a multiplier may pay in all. Needed for `MINTED`, empty for no cap on an account |

An invalid value creates nothing and shows the form again with the error. The form stays on the server you opened it
on until you save or discard it.

Only the primary server starts and ends events, at their times; ending, cancelling and changing works on every
server. Every change is written to the [admin log](#the-admin-log).

## Job disputes

You need `companies.admin.jobs`. The owner of a [job](../players/jobs.md#disputes) or the company working on it may open
a dispute. The job is frozen until you settle it: the company's access ends, its time stops and nobody may build in its
area. The side that opened it may withdraw it, and the owner may still approve the job, so a dispute may vanish from the
list before you get to it. The same tools are in the *Disputes* tab of the [dashboard](./dashboard.md#admin-tools).

| Command | What it does |
|---|---|
| `/companyadmin job disputes` | Lists the disputed jobs, the oldest first: price, owner, company, who opened the dispute and why, the company's due time and whether it had passed, the description, and for an area job its place, the blocks counted as work and, on the server of the area, how far it is cleared |
| `/companyadmin job settle <id> <percent>` | Settles a dispute: the company gets `percent` of the price, split among its members by their work and taxed like a finished job; the owner gets the rest back. The fee is kept. When the company is gone, only `0` is accepted |

Settling is written to the server log. Players with `companies.admin.jobs.bypass` are not held by the access rules of
job areas, which helps when you look at a disputed area.

## Players

The same tools are in the *Players* tab of the [dashboard](./dashboard.md#admin-tools).

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

The same tools are in the *Companies* tab of the [dashboard](./dashboard.md#admin-tools).

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
again. The *Levels* tab of the [dashboard](./dashboard.md#admin-tools) has the same editor. See
[Levels](./levels.md).

## Boards

`/companyadmin board ...` lists presets, rewrites board signs and binds NPCs, as the *Boards* tab of the
[dashboard](./dashboard.md#admin-tools) does. See [Boards](./boards.md).

## Web dashboard

`/companyadmin dashboard status` shows the dashboard connection, `/companyadmin dashboard key <key>` stores the
dashboard key, and `/companyadmin dashboard revoke <player>` logs a player out of the dashboard. New in 2.1. See
[Web dashboard](./dashboard.md).

## Plugin

The *Settings* tab of the [dashboard](./dashboard.md#admin-tools) shows and saves the shared settings with their
history and rollback, the *Setup* tab runs the guided setup, and the *Servers* tab lists and removes servers and syncs
the configuration.

| Command | Permission | What it does |
|---|---|---|
| `/companyadmin reload` | `companies.admin.reload` | Reads the configuration files again and applies them; in a network it names the shared settings changed here |
| `/companyadmin reload confirm` | `companies.admin.reload` | Shares those settings with every server |
| `/companyadmin syncconfiguration` | `companies.admin.reload` | Copies the configuration between the primary server and the database |
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
