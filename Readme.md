[![Wakatime_Badge](https://wakatime.com/badge/github/eldoriarpg/companies.svg)][wakatime]

[![Publish](https://img.shields.io/github/actions/workflow/status/eldoriarpg/companies/publish_to_nexus.yml?style=for-the-badge&label=Publish)][publish]
[![Build](https://img.shields.io/github/actions/workflow/status/eldoriarpg/companies/verify.yml?style=for-the-badge&label=Build)][verify]

[![Releases](https://img.shields.io/nexus/maven-releases/de.eldoria/companies-api?label=Release&logo=Release&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)][release]
[![Snapshots](https://img.shields.io/nexus/s/de.eldoria/companies-api?color=orange&label=Snapshot&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)][snapshot]

<!-- [![Text](image_link)][link_anchor] -->
<!-- [anchor]: link> -->


# Dependency
**Gradle**
``` kotlin
repositories {
    maven("https://eldonexus.de/repository/maven-public")
}

dependencies {
    compileOnly("de.eldoria", "companies-api", "2.0.0")
}
```

**Maven**
``` xml
<repository>
    <id>EldoNexus</id>
    <url>https://eldonexus.de/repository/maven-public/</url>
</repository>

<dependency>
    <groupId>de.eldoria</groupId>
    <artifactId>companies-api</artifactId>
    <version>2.0.0</version>
</dependency>
```

# API

## Getting the API
Declare Companies as a dependency in your `plugin.yml`:

```yaml
depend: [Companies]
# or, when your plugin also runs without it:
softdepend: [Companies]
```

Companies registers the API with Bukkit's services manager while it enables, so it is available in your `onEnable`:

```java
CompaniesApi api = CompaniesApi.get();
// or
CompaniesApi api = getServer().getServicesManager().load(CompaniesApi.class);
```

`CompaniesApi.get()` throws an `IllegalStateException` when Companies is not enabled. With `softdepend`, check
`getServer().getPluginManager().isPluginEnabled("Companies")` first. The API is split into `companies()`, `orders()`,
`players()` and `ranking()`.

## Futures and threads
Every method returns a `CompletableFuture` that completes **off the main thread**. Never `join()` or `get()` it on the
main thread. Hop back to the main thread for Bukkit calls:

```java
api.companies().of(player.getUniqueId())
        .thenAcceptAsync(company -> company.ifPresent(c -> player.sendMessage("Member of " + c.name())),
                task -> Bukkit.getScheduler().runTask(plugin, task));
```

Unknown ids and names give `Optional.empty()`. Reads work as soon as the API is registered. Write operations need the
economy, which Companies sets up once the server has started; until then, and while Companies runs without an economy,
their futures complete exceptionally with `ApiNotReadyException`.

All data lives in the shared database, so on a network every server reads the same state. Events fire only on the
server where the action happened; expiry and purges happen on the primary server.

## Writing
| Method                                         | What it does                                                                         |
|------------------------------------------------|--------------------------------------------------------------------------------------|
| `orders().create(OrderRequest)`                | Creates an order. Fires `PreOrderCreateEvent` and `OrderCreatedEvent`                 |
| `companies().addMember(company, player)`       | Adds a player to a company, respecting its member limit. Fires `CompanyJoinEvent`     |
| `players().grantItems(player, stack, amount)`  | Puts items into the player's returned items (`/order collect`). Fires `ItemsReturnedEvent` |

```java
OrderRequest request = OrderRequest.builder(owner, "Castle stone")
        .line(OrderLineRequest.material(Material.STONE_BRICKS, 512, 2.5))
        .line(OrderLineRequest.exact(enchantedPickaxe, 1, 500))
        .charge(false)
        .respectLimits(false)
        .build();

api.orders().create(request).thenAccept(result -> {
    switch (result) {
        case CreateOrderResult.Created created -> getLogger().info("Order " + created.order().id() + " created");
        case CreateOrderResult.Rejected rejected -> getLogger().info("Rejected: " + rejected.reason());
    }
});
```

* **`charge(true)`** (default): the owner pays price and fee like with `/order create`. An owner who can not afford it
  gets `RejectReason.INSUFFICIENT_FUNDS`; a failed save refunds the money; cancelling refunds as usual.
* **`charge(false)`**: the server funds the order. Nothing is withdrawn and the fee is 0, but workers are still paid the
  full price on completion. Cancelling or removing such an order refunds nothing. Meant for rewards and quests.
* **`respectLimits(true)`** (default) applies the owner's order limit and the lines and items limits of `order.yml`.
  With `false` only the name and the lines themselves are checked.

A rejected order carries a `RejectReason` (`LIMIT_REACHED`, `TOO_MANY_LINES`, `TOO_MANY_ITEMS`, `INSUFFICIENT_FUNDS`,
`CANCELLED`, `INVALID`) and a message you can show to a player.

## Events
Listen to them like any Bukkit event. Most are fired off the main thread; their javadoc names the thread.

| Event                       | When                                                                                   |
|-----------------------------|----------------------------------------------------------------------------------------|
| `PreCompanyCreateEvent`     | A player is about to found a company, before the price is charged. Cancellable         |
| `CompanyCreateEvent`        | A company was founded                                                                  |
| `CompanyRenameEvent`        | A company was renamed by its owner or an admin                                         |
| `CompanyOwnerTransferEvent` | The ownership passed to another member                                                 |
| `CompanyProfileEditEvent`   | Description, tag, colour or join mode changed                                          |
| `CompanyJoinEvent`          | A player joined a company                                                              |
| `CompanyLeaveEvent`         | A member left; `CompanyKickEvent` when they were removed                               |
| `CompanyLevelUpEvent`, `CompanyLevelDownEvent` | The level of a company changed                                      |
| `CompanyDisbandEvent`       | A company was disbanded                                                                |
| `PreOrderCreateEvent`       | An order is about to be created, before the owner is charged. Cancellable; the draft can be changed |
| `OrderCreatedEvent`         | An order was saved                                                                     |
| `PreOrderAcceptEvent`       | A member is about to accept an order for their company. Cancellable                    |
| `OrderAcceptEvent`          | A company claimed an order                                                             |
| `OrderDeliveryEvent`        | A worker delivered items to an order                                                   |
| `OrderDoneEvent`            | All items of an order are delivered                                                    |
| `OrderPaymentEvent`         | A worker is about to be paid their share. Cancellable; `share()` is the part kept in the company treasury |
| `OrderReceivedEvent`        | The owner received the items                                                           |
| `OrderCanceledEvent`        | A company aborted a claimed order                                                      |
| `OrderExpiredEvent`         | A company did not deliver in time                                                      |
| `OrderRemovedEvent`         | An order was deleted before delivery; `reason()` is `PURGED`, `CANCELLED` or `ADMIN`   |
| `ItemsReturnedEvent`        | Items were put into a player's returned items; `reason()` tells why                    |
| `TreasuryTransactionEvent`  | Money moved into or out of a company treasury; `kind()` tells why                       |

`companies().treasury(id)` reads the balance and payout share of a company. Copies posted by a repeating order are
normal orders of their owner; `PreOrderCreateEvent` reports them with the source `REPEATING`.

The pre-events implement `CancellableWithReason`. `cancel(Component)` stops the action and shows the reason to the
player; `setCancelled(true)` shows a generic "Another plugin prevented this." Changes to the draft in
`PreOrderCreateEvent` are checked against the limits again, and an order that breaks them is rejected with `INVALID`
without charging anything.

**Anonymity.** Companies keeps orders anonymous: workers never learn who placed an order, owners never learn which
company works on theirs. Events and the API expose both sides. Use them for logic, logging or staff tools, and never
show the owner of an order to the company's members or the company to the owner.

## Migrating from 1.x
The 1.x `CompaniesApi` had no reachable instance, so only event listeners can break.

| 1.x                                                     | 2.0                                                                    |
|---------------------------------------------------------|------------------------------------------------------------------------|
| `CompaniesApi.getInstance()`                            | `CompaniesApi.get()` or `ServicesManager.load(CompaniesApi.class)`     |
| `getPlayerCompanyProfile(OfflinePlayer)`, `getPlayerCompany` | `companies().of(uuid)`                                            |
| `getCompanyStats(ISimpleCompany)`                       | `companies().stats(id)`                                                |
| `getCompanyByName`, `getCompanyById`                    | `companies().byName`, `companies().byId`                               |
| `retrieveOrderById`                                     | `orders().byId`, now an `IFullOrder`                                   |
| `retrieveOrdersByCompany`, `retrieveOrdersByPlayer`     | `orders().byCompany`, `orders().byOwner`, now `IFullOrder`s            |
| `retrieveMaterialPrice(Material)`                       | `orders().materialPrice`                                               |
| Blocking return values                                  | `CompletableFuture`s, completed off the main thread                    |
| Company and player objects as arguments                 | Company ids and player `UUID`s                                         |
| `OrderRemovedEvent` on purge only                       | Also on owner and admin cancel; check `reason()`                       |
| `OrderPaymentEvent`                                     | Also provides `company()`                                              |
| Order events                                            | All provide the order's `owner()`                                      |
| `CompanyPermission`, `TopOrder`                         | New constants `EDIT_PROFILE`, `TREASURY` and `LEVEL`; exhaustive switches need a branch |
| `ICompanyRank`                                          | New `level()`, `tag()`, `color()`                                      |
| `IOrderContent`                                         | Lines with `line()`, `exact()`, `unitPrice()`, `deliveries()`; `IFullOrder.content(Material)` is deprecated |

# Setup

The plugin is plug & play. However, you can do several configurations.

## Requirements
* [Paper](https://papermc.io/) 1.21.11 or newer, running on Java 25
* [Vault](https://github.com/MilkBowl/Vault) and an economy plugin
* One of these databases:
  * SQLite (default, nothing to set up)
  * MariaDB 10.5 or newer. MySQL is not supported.
  * PostgreSQL 13 or newer. The configured schema has to exist.

## Optional Dependencies
* [PAPI](https://www.spigotmc.org/resources/6245/) for Placeholders
* [ProtocolLib](https://www.spigotmc.org/resources/1997/) for the best experience with the text UIs. **This is highly recommended.**
* [Citizens](https://citizensnpcs.co) or [FancyNpcs](https://modrinth.com/plugin/fancynpcs) for NPCs that open order
  boards

## Commands

Every argument has tab completion. Order ids, line numbers and company names are suggested.
Line numbers start at 1.

### Orders: `/order`

| Command                                          | Description                                                   |
|--------------------------------------------------|---------------------------------------------------------------|
| `/order` or `/order list`                        | List your orders                                              |
| `/order info <id>`                               | Show an order                                                 |
| `/order create <name>`                           | Start a new order draft                                       |
| `/order draft name <name>`                       | Rename the draft                                              |
| `/order draft add <item> <amount> <unit price>`  | Add a line, e.g. `stone` or `diamond_sword[enchantments={sharpness:5}]` |
| `/order draft add hand <amount> <unit price>`    | Add the item in your main hand as an exact line               |
| `/order draft amount <line> <amount>`            | Change the amount of a line                                   |
| `/order draft price <line> <unit price>`         | Change the unit price of a line                               |
| `/order draft price <line> market`               | Use the market average as unit price (material lines only)    |
| `/order draft remove <line>`                     | Remove a line                                                 |
| `/order draft repeat daily\|weekly <count>`      | Post the order again every day or week, `count` times in all  |
| `/order draft repeat daily\|weekly until <date>` | Post it again until the given day (`yyyy-MM-dd`)              |
| `/order draft repeat off`                        | Make the draft a one-off order again                          |
| `/order draft done` / `cancel`                   | Place the order and pay for it, or drop the draft             |
| `/order cancel <id>`                             | Cancel an order nobody accepted yet                           |
| `/order extend <id>`                             | Extend an order nobody accepted yet                           |
| `/order raise <id> <amount>`                     | Raise the price of an open order                              |
| `/order receive <id>`                            | Receive the items of a delivered order                        |
| `/order collect`                                 | Collect items that did not fit into your inventory            |
| `/order history [page]`                          | List your ended orders                                        |
| `/order history info <entry>`                    | Show an ended order with its items, price and outcome         |
| `/order series`                                  | List your repeating orders                                    |
| `/order series info <id>`                        | Show a repeating order, its schedule and its current copy     |
| `/order series stop <id>` / `resume <id>`        | Stop a repeating order, or let a stopped one post again       |

Drafts expire after 15 minutes.

### Repeating orders

With `companies.orders.repeat`, an order can repeat daily or weekly, for a number of copies, until a day, or both,
whichever ends first. Set it with `/order draft repeat` or the repeat button of the order builder. The first copy is
posted at once; the others follow on the same time of day, posted by the primary server, and each copy is charged when
it is posted. Missed intervals are not caught up.

* A copy waits while the previous one is still open, and while you are at your order limit; you are told once when
  the limit holds it back.
* A copy you cannot pay for stops the repeating order. A copy that breaks the current order rules, or that another
  plugin prevents, is skipped.
* `/order series stop` asks for a confirmation and keeps the current copy. `resume` checks the permission, the end and
  `maxSeriesPerPlayer` again.
* Cancelling a copy with `/order cancel` stops the whole repeating order; the command asks first, confirmed with
  `/order cancel confirm`. An admin cancelling a copy stops it as well.

Companies see copies as normal orders; nothing shows them that an order repeats. `/order list` marks your copies with
⟳, and `/order info` links them to their repeating order.

### Companies: `/company` (alias `/comp`)

| Command                                                   | Description                                         |
|-----------------------------------------------------------|-----------------------------------------------------|
| `/company` or `/company profile [id <id>\|name <name>\|player <player>]` | Show a company profile          |
| `/company create <name>`                                  | Found a company                                     |
| `/company invite <player>` / `invite revoke <player>`     | Invite a player, or take an invite back             |
| `/company invites [accept\|deny <company>]`               | Show or answer your invites and applications        |
| `/company join <company>`                                 | Join an open company                                |
| `/company apply <company> <message>` / `apply withdraw <company>` | Apply to a company, or withdraw the application |
| `/company applications [accept\|deny <player>]`           | Show or answer the applications to your company     |
| `/company joinmode [open\|applications\|invite]`          | Show or change how players join your company        |
| `/company profile edit`                                   | Edit the profile of your company                    |
| `/company profile edit description\|tag\|color [value]`   | Set a profile field; `tag clear` and `color clear` remove one |
| `/company kick <member>`                                  | Kick a member                                       |
| `/company leave`                                          | Leave your company                                  |
| `/company transfer <member>`                              | Hand the company to another member                  |
| `/company disband`                                        | Disband your company                                |
| `/company rename <name>`                                  | Rename your company                                 |
| `/company bank [log [page]]`                              | Show the treasury of your company, or its log       |
| `/company bank deposit\|withdraw <amount>`               | Pay money into the treasury, or take it out         |
| `/company bank share <percent>`                           | Set the share of each payout kept in the treasury   |
| `/company alerts [list]`                                  | List the order alerts of your company               |
| `/company alerts add <name>`                              | Save your current order search as an alert          |
| `/company alerts remove\|on\|off <name>`                  | Remove an alert, or switch it on or off             |
| `/company alerts show <name>`                             | Search the orders an alert matches                  |
| `/company member [id <id>]`                               | List the members                                    |
| `/company member <sorting> [all\|30d]`                    | List your members sorted by `name`, `items`, `earned` or `orders` |
| `/company permission <member> [give\|remove <permission>]` | Show or change the permissions of a member          |
| `/company top [page] [ordering] [all\|30d\|7d]`            | Show the company ranking                            |
| `/company returns`                                        | Take back items returned from aborted orders        |
| `/company inbox [page]`                                   | Show your notifications                             |
| `/company inbox read\|clear`                              | Mark every notification read, or delete the read ones |
| `/company inbox mute\|unmute <category>`                  | Stop or resume showing a category live              |
| `/company inbox settings`                                 | Show and toggle the muted categories                |
| `/company history [page]`                                 | List the ended orders of your company               |
| `/company history info <entry>`                           | Show an ended order with its items, payouts and outcome |
| `/company board [preset\|materials]`                      | Open an order board: the open orders of a preset or of materials |

Confirmations are given with `confirm` after the command, e.g. `/company leave confirm`.

### Joining a company

Every company has a join mode, shown on its profile:

| Join mode      | How players join                                                      |
|----------------|-----------------------------------------------------------------------|
| `open`         | Anyone with `companies.company.join` joins with `/company join`       |
| `applications` | Players apply with a message, members with `INVITE` accept or deny it |
| `invite`       | Only invited players can join                                         |

Invites work in every mode. Invites and applications stay open for `inviteHours` and the players involved are
notified when they are answered or expire. A company at its level's member limit can not take new members.

### Company profile

The profile shows a description, a tag of a few upper-case letters or digits written as `[TAG]` before the name, and a
colour for tag and name. Members with `EDIT_PROFILE` change them with `/company profile edit`, through the `[Edit]`
button on the profile or the edit menu, and the other members are notified.

### Treasury

Every company has a treasury. Any member can pay into it and read its log; members with `TREASURY` withdraw money and
set the payout share. The share is the percentage of each worker's payout, after tax and rounded down, that goes to the
treasury instead of the worker. It can be at most `treasury.maxPayoutShare`; lowering that cap applies to every company
at once. All members are told when the share changes, and workers see the kept part in their payment notification.

When the treasury covers the price, the owner can pay a rename from it: the confirmation offers
`[Pay from the treasury]`, or type `/company rename confirm treasury`.

When a company is disbanded or deleted by an admin, the remaining balance is paid to the owner, who gets a
notification, and the payout is written to the server log. Some economies can not pay players who are offline; the
amount is then logged as lost.

### Order alerts

An alert is a saved order search. Members with `MANAGE_ORDERS` save the current search with `/company alerts add
<name>` or the `[Save as alert]` button of the search; names have up to 32 letters, digits, `-` and `_`. When a new order
matches, the members with `MANAGE_ORDERS` get a notification with `[View]` and `[Accept]` buttons; matches on other
servers of the network arrive through the inbox.

* A company may save `alerts.maxPerCompany` alerts, or the `maxAlerts` of its level. A level with `0` allows none.
* Each alert notifies at most once every `alerts.minMinutesBetween` minutes. Matches in between are summed up in a
  digest once that time has passed.
* A company is only told about orders it could accept: not while it works on as many orders as its level allows, not
  about orders larger than its level allows, and not about orders of its own members unless member orders are allowed.

### Company permissions

The owner has every permission and gives the others to members with `/company permission <member> give <permission>`.

| Permission           | Allows                                                            |
|----------------------|-------------------------------------------------------------------|
| `invite`             | Invite players, take invites back and answer applications         |
| `kick`               | Kick members                                                      |
| `manage_orders`      | Accept and abort orders, manage order alerts                      |
| `manage_permissions` | Give and remove permissions                                       |
| `edit_profile`       | Change description, tag, colour and join mode                     |
| `treasury`           | Withdraw from the treasury and set the payout share               |

### Company orders: `/company order`

| Command                                      | Description                                                       |
|----------------------------------------------|-------------------------------------------------------------------|
| `/company order` or `/company order list`    | List the orders your company works on                             |
| `/company order info <id>`                   | Show an order with its lines and what was delivered               |
| `/company order search`                      | Show the search; `search query <field> <value>` changes a field, `search query execute` runs it and `search page <n>` pages through the results |
| `/company order accept <id>`                 | Accept an order from the search results                           |
| `/company order deliver <id> <line> <amount>` | Deliver up to `amount` items to a line                           |
| `/company order deliver <id> <line> max`     | Deliver as many items as the line misses                          |
| `/company order deliver <id> all`            | Deliver every matching item to every line                         |
| `/company order abort <id>`                  | Abort an order. Delivered items go back to the workers            |

Delivery takes items from your inventory and from a shulker box held in the main hand. Plain items are used before
items with extra data such as enchantments or names.

### Inbox

Every notification lands in the inbox of its player. Players who are online see it in chat, within
`notifications.pollSeconds` when they play on another server of the network; important ones also show on the action bar
with a sound. Players who were offline get a summary with the number of unread notifications and an `[Open]` button when
they join. Opening the inbox marks the shown page as read.

Some categories can be muted with `/company inbox mute <category>` or in `/company inbox settings`. Muted notifications
still arrive in the inbox but are never shown live and are not counted in the join summary.

| Category   | Contains                                                                  | Mutable |
|------------|---------------------------------------------------------------------------|---------|
| `company`  | Disbanding, being kicked, ownership transfers, profile changes, treasury share changes and payouts | no |
| `members`  | Members joining, leaving or being kicked                                  | yes     |
| `orders`   | Orders of the company being accepted, aborted, delivered or expiring, and admin changes to them | yes |
| `my_orders`| Your own orders being accepted, done, reopened, removed, about to be removed or changed by an admin, and your repeating orders | no |
| `payments` | Payments for delivered orders and money granted by an admin               | yes     |
| `alerts`   | New orders matching an alert of your company, and digests                 | yes     |
| `invites`  | Invites and applications                                                  | no      |
| `level`    | The company level going up or down                                        | yes     |

Notifications are kept for `notifications.retentionDays`, read or not.

### Rankings

`/company top` ranks the companies by `orders`, `earned`, `member`, `items_delivered` or `level`. Orders, money and items
are counted for all time (`all`, the default) or for the last 30 or 7 days (`30d`, `7d`); the ranges are rolling
windows ending now. Members and level are the current values in every range. The ranking marks the place of your own
company, and each company shows its tag in its colour. Pages and places are cached for `top.cacheSeconds`.

### History and member statistics

When an order ends for its owner, or for the company working on it, an entry is written to the history of that owner or
company. The entry keeps the items, price, workers and payouts as they were, together with the outcome:
received, cancelled, removed, delivered, aborted, expired, or cancelled or released by an admin. Entries are deleted
after `generalSettings.historyDays`.

Every payout to a member is recorded. `/company member` shows each member's delivered items, earned money and
contributed orders for all time and the last 30 days, and sorts the list by one of them with `/company member
<sorting> [all|30d]`. Earned money counts the payouts after tax that were actually paid.

### Administration: `/companyadmin`

| Command                                                  | Permission                      |
|----------------------------------------------------------|---------------------------------|
| `/companyadmin level [list\|info\|create\|edit\|move\|remove]` | `companies.admin.level`      |
| `/companyadmin calclevel`                                | `companies.admin.calcLevel`     |
| `/companyadmin delete <company>`                         | `companies.admin.delete`        |
| `/companyadmin rename <company> <name>`                  | `companies.admin.rename`        |
| `/companyadmin transferowner <company> <player>`         | `companies.admin.transferOwner` |
| `/companyadmin reload`                                   | `companies.admin.reload`        |
| `/companyadmin order info <id>`                          | `companies.admin.orders`        |
| `/companyadmin order search player <player>`             | `companies.admin.orders`        |
| `/companyadmin order search company <company>`           | `companies.admin.orders`        |
| `/companyadmin order unclaim <id>`                       | `companies.admin.orders`        |
| `/companyadmin order cancel <id> refund\|keep`           | `companies.admin.orders`        |
| `/companyadmin order deliver <id>`                       | `companies.admin.orders`        |
| `/companyadmin order extend <id> <hours>`                | `companies.admin.orders`        |
| `/companyadmin player <player>`                          | `companies.admin.player`        |
| `/companyadmin grant items <player> [amount]`            | `companies.admin.grant`         |
| `/companyadmin grant money <player> <amount>`            | `companies.admin.grant`         |
| `/companyadmin board presets`                            | `companies.admin.boards`        |
| `/companyadmin board reload`                             | `companies.admin.boards`        |
| `/companyadmin board npc link <hook> <npc> [board]`      | `companies.admin.boards`        |
| `/companyadmin board npc unlink <hook> <npc>`            | `companies.admin.boards`        |
| `/companyadmin board npc list`                           | `companies.admin.boards`        |
| `/companyadmin treasury <company>`                       | `companies.admin.treasury`      |
| `/companyadmin treasury give\|take <company> <amount>`   | `companies.admin.treasury`      |
| `/companyadmin syncconfiguration`                        | `companies.admin`               |
| `/companyadmin debug`                                    | `companies.admin`               |

Company names with spaces are quoted in `rename`, `transferowner` and `treasury`.

`order info` shows both sides of an order, its times, money, the deliveries of every worker and its history.
`unclaim`, `cancel` and `deliver` ask for a confirmation, given with the button or by appending `confirm`:

* `unclaim` takes a claimed order away from its company. Delivered items go back to the workers and the order is open
  again.
* `cancel … refund` deletes the order and refunds price and fee to the owner; `cancel … keep` deletes it and sends
  price and fee to the collector account, or removes them from the economy when none is set. Delivered items go back to
  the workers.
* `deliver` closes the order as delivered. The workers are paid for what they delivered and the owner gets the price of
  the missing items back.
* `extend` gives a claimed order more delivery time, or an open order more time before it is removed.

`player` shows a player's company, orders, waiting items and unread notifications. `grant items` stores the item in your
main hand for the player (the held stack, or `amount` of that item), who collects it with `/order collect`; `grant
money` pays the player through the economy. `treasury` shows a company's balance, payout share and latest
transactions; `give` and `take` change the balance, at most down to 0, and are written to the treasury log. Players affected by an admin action get a notification that does not name
the admin.

Every admin command writes a line with the admin's name and the details to the server log, for example
`[admin] Steve order cancel id=42 refund=true owner=Alex company="Iron Works"`.
Update notifications link to `/companies:eldo-update`, which is available to `companies.updates`.

### Order boards

A board is the order search opened with a filter: a preset from `boards.presets` in `config.yml`, materials separated
by spaces (each matches every material whose name contains it, e.g. `iron` or `oak_log birch_log`), or nothing for
every open order. It shows the first page of results, with `[Change]` leading to the search form. Members of a company
accept orders from it as usual; players without a company see the list read-only. Boards need
`companies.boards.use` (everyone by default) and open at most once per second per player.

`/company board [preset|materials]` opens a board directly. `/companyadmin board presets` lists the presets with their
open order counts, and `/companyadmin board reload` writes the loaded board signs again after the presets were changed
and `/companyadmin reload` was run.

**Sign boards.** A player with `companies.admin.boards` writes a sign with `[Companies]` (`boards.signHeader`, any case)
on the first line and a preset or materials on the second; an empty second line makes a board of every open order. The
board is stored on the sign itself, the sign is waxed and both sides show the header, the label, the number of open
orders and `Right-click`. A right-click opens the board. Players without the permission get the header cleared, and an
unknown preset or material leaves the sign plain with an error.

The counts are refreshed every `boards.refreshSeconds` and a few seconds after an order on that server was created,
accepted, released or removed. Only signs in loaded chunks are counted, and each distinct board is counted once.

Board signs are protected: they can not be edited, only players with `companies.admin.boards` can break them, and
explosions and fire leave them alone. A sign still falls when the block it hangs on is removed, and pistons move it,
so place board signs on blocks protected by a region plugin.

### NPC boards

A right-click on a bound NPC opens its order board, like `/company board [board]` and with the same permission
(`companies.boards.use`) and limit of one board per second. `<board>` is a preset name, materials separated by spaces,
or nothing for every open order.

* **Citizens:** `/companyadmin board npc link Citizens <id|selected> [board]`, `selected` being the NPC selected with
  `/npc select`.
* **FancyNpcs:** `/companyadmin board npc link FancyNpcs <name> [board]`.

The hooks load when the NPC plugin is enabled and `generalSettings.hooks.citizens` or `generalSettings.hooks.fancynpcs`
is `true`. Bindings are stored per server in `boards.yml` and are not copied to other nodes.

**Any other NPC or menu plugin** opens a board by running `company board [board]` as the clicking player, for example
a Citizens command trait `/npc command add -p company board iron` or a FancyNpcs action
`player_command company board iron`. No hook is needed for that.

## Permissions

| Permission                       | Default | Description                                                                    |
|----------------------------------|---------|--------------------------------------------------------------------------------|
| `companies.orders.create`        | all     | Create orders                                                                  |
| `companies.orders.repeat`        | all     | Make orders repeat daily or weekly                                             |
| `companies.orders.limit.<n>`     | none    | Raise or lower the order limit to `n` active orders. The highest number counts |
| `companies.company.create`       | all     | Found a company                                                                |
| `companies.company.join`         | all     | Be invited to and join a company                                               |
| `companies.updates`              | op      | Hear about new versions on join and download them                              |
| `companies.boards.use`           | all     | Open order boards through signs, NPCs and `/company board`                     |
| `companies.admin`                | op      | Use `/companyadmin` and create debug reports                                   |
| `companies.admin.boards`         | op      | Create and break board signs, list presets and bind NPCs to boards             |
| `companies.admin.calcLevel`      | op      | Recalculate company levels                                                     |
| `companies.admin.delete`         | op      | Delete companies                                                               |
| `companies.admin.grant`          | op      | Grant items and money to players                                               |
| `companies.admin.level`          | op      | Manage the company levels                                                      |
| `companies.admin.orders`         | op      | Inspect, release, cancel, complete and extend any order                        |
| `companies.admin.player`         | op      | Look up the company, orders, items and notifications of a player               |
| `companies.admin.reload`         | op      | Reload the configuration                                                       |
| `companies.admin.rename`         | op      | Rename companies                                                               |
| `companies.admin.transferOwner`  | op      | Transfer the ownership of a company                                            |
| `companies.admin.treasury`       | op      | Inspect company treasuries and add or remove money                             |

`companies.orders.*`, `companies.company.*`, `companies.admin.*` and `companies.*` grant the permissions below them.
Without a `companies.orders.limit.<n>` permission a player may have `maxOrders` active orders, as set in `user.yml`.
`companies.orders.create` covers `/order create` and `/order draft`. `/companyadmin` is shown to holders of any admin
permission, and each subcommand checks its own. Commands a player may not use are hidden from them.

## Placeholders

With [PAPI](https://www.spigotmc.org/resources/6245/) installed, the placeholders are `%companies_<id>%`. Company values
are those of the requesting player's company. Without a company, counts read `0` and texts read empty.

| Id                                   | Value                                                                  |
|--------------------------------------|------------------------------------------------------------------------|
| `company_name`                       | Name of the company                                                    |
| `company_tag`                        | Tag of the company                                                     |
| `company_tag_colored`                | Tag in the company colour                                              |
| `company_color`                      | Colour of the company as configured, e.g. `gold` or `#4a90d9`           |
| `company_owner`                      | Name of the owner                                                      |
| `company_size`                       | Number of members                                                      |
| `company_level`                      | Level number                                                           |
| `company_level_name`                 | Level name                                                             |
| `active_orders`                      | Orders the company works on                                            |
| `company_orders`                     | Completed orders, all time                                             |
| `company_delivered`                  | Items delivered to completed orders, all time                          |
| `company_earned`                     | Money earned with completed orders, formatted, all time                |
| `company_earned_raw`                 | The same as a plain number with two decimals                           |
| `company_treasury`                   | Balance of the treasury, formatted                                     |
| `company_payout_share`               | Percentage of each payout kept in the treasury                         |
| `company_rank_<order>[_<range>]`     | Place of the company in a ranking, empty when not ranked               |
| `player_open_orders`                 | Own orders that are not delivered yet                                  |
| `player_order_limit`                 | Most orders the player may have open at once                           |
| `player_items_waiting`               | Items waiting for the player: returned items and items of own orders  |
| `player_role`                        | `Owner` or `Member` in the server language, empty without a company    |
| `top_<order>[_<range>]_<place>_<field>` | A place of a ranking, empty when there is no such place             |

`<order>` is `orders`, `earned`, `member`, `items_delivered` or `level`. `<range>` is `all` (the default when it is
left out), `30d` or `7d`. `<place>` starts at 1 and goes up to `placeholders.leaderboardSize`. `<field>` is `name`,
`tag`, `tag_colored` or `value`, the value the ranking is sorted by. Examples: `%companies_top_earned_30d_1_name%`,
`%companies_company_rank_items_delivered_7d%`.

Coloured tags are legacy text with `§` codes, hex colours as `§x§r§r§g§g§b§b`. Values are cached and refreshed every
`placeholders.refreshSeconds`, so placeholders never wait for the database. A value requested for the first time reads
`placeholders.loadingText` until it is loaded.

## Configuration
A detailed guide on the configuration can be found at the [wiki](https://github.com/eldoriarpg/companies/wiki/Configuration)

### Item matching
An order line is either a **material line** (`/order draft add stone 64 1`), which any item of that material fills,
or an **exact line** (`/order draft add hand …` or an item with components), which requires the components of the
ordered item. Extra components on a delivered item, like enchantments, are always accepted.

The `matching` section in `order.yml` decides what counts:

```yaml
matching:
  ignoredComponents:
    - minecraft:custom_name
    - minecraft:repair_cost
    - minecraft:lore
  allowDamaged: false
```

* `ignoredComponents` are data component types that never decide a match. They are also removed from items put into
  an order, so a name or lore on the ordered item is not shown to workers. Any vanilla component type can be listed,
  including `minecraft:custom_data`, which holds the data of plugin items. Unknown keys are logged and skipped.
* `allowDamaged` is off by default, so only undamaged items can be delivered. With it on, damaged items are accepted
  and the order info shows workers how worn the delivered items are.

The owner receives exactly the items that were delivered, with all their data: enchantments, names, lore and, with
`allowDamaged`, their damage. A worker's custom name or lore on a delivered item is therefore visible to the owner.
Items with content, like filled shulker boxes or bundles, can neither be ordered nor delivered.

### Joining and profiles
`company.yml` holds the settings for invites, applications and profiles:

```yaml
inviteHours: 24
joining:
  defaultMode: INVITE_ONLY
  maxApplicationLength: 120
  maxPendingApplications: 3
profile:
  maxDescriptionLength: 160
  tagLength:
    min: 2
    max: 5
  blockedTags:
    - ADMIN
    - MOD
    - STAFF
  colors:
    - white
    - gold
    - '#4a90d9'
```

* `inviteHours` is how long invites and applications stay open.
* `joining.defaultMode` is the join mode of newly founded companies (`OPEN`, `APPLICATIONS` or `INVITE_ONLY`).
  Existing companies keep theirs.
* `maxApplicationLength` limits the application message, `maxPendingApplications` the open applications of one
  player across all companies.
* `profile.tagLength.max` can not exceed 5. Tags are unique, and `blockedTags` can never be used.
* `colors` are the colours a company can choose from: named colours such as `gold` or `#rrggbb` values.

### Menus
`menus` in `config.yml` sets how menus such as the order builder, the order search, order views and confirmations are
shown:

| Value    | Menus are shown as                                                                    |
|----------|---------------------------------------------------------------------------------------|
| `DIALOG` | Dialog windows with input fields and buttons. Needs a Minecraft 1.21.6 client or newer |
| `CHAT`   | Clickable chat messages                                                               |

New installations use `DIALOG`. A `config.yml` from an older version has no `menus` entry and keeps showing chat menus
until `menus: DIALOG` is added.

Players whose client is older than 1.21.6, for example when joining through ViaVersion, always get chat menus. While a
chat menu is open, other chat messages are held back and delivered once the menu is closed with `[x]`. This needs
ProtocolLib and can be turned off with `generalSettings.messageBlocking: false`. Dialogs never hold back chat.

### Treasury, repeating orders and alerts
`company.yml` holds `treasury` and `alerts`, `order.yml` holds `repeating`. Files from an older version use these
values until the sections are added.

```yaml
# company.yml
treasury:
  enabled: true
  maxPayoutShare: 25
  minTransaction: 1.0
  payRename: true
  logRetentionDays: 365
  logPageSize: 10
alerts:
  enabled: true
  maxPerCompany: 3
  minMinutesBetween: 15
# order.yml
repeating:
  enabled: true
  maxSeriesPerPlayer: 3
  maxCopies: 52
  maxDays: 365
  checkMinutes: 5
```

* `treasury.enabled: false` hides the bank commands and takes no payout share; balances are kept.
  `maxPayoutShare` caps the share (0 to 100), `minTransaction` is the smallest deposit or withdrawal, `payRename` lets
  owners pay renames from the treasury. Log entries are kept for `logRetentionDays` (0 keeps them forever) and shown
  `logPageSize` per page (1 to 50).
* `alerts.maxPerCompany` is the number of alerts per company (0 turns them off), unless a level in `company.yml` sets
  its own `maxAlerts`. `minMinutesBetween` is the shortest time between two notifications of one alert.
* `repeating.enabled: false` stops new and existing repeating orders from posting. `maxSeriesPerPlayer` limits the
  active repeating orders of a player, `maxCopies` the copies of one, `maxDays` how far ahead its last day may lie.
  The primary server posts due copies every `checkMinutes`.

### Boards
`config.yml` holds the `boards` section. A file from an older version uses these values until it is added.

```yaml
boards:
  enabled: true
  refreshSeconds: 60
  signHeader: '[Companies]'
  presets:
    iron:
      label: Iron
      materials: [iron_ingot, iron_block, raw_iron]
      exactMatch: true
```

* `enabled: false` turns sign boards off; `/company board` and NPC boards keep working. Changing it needs a restart.
* `refreshSeconds` is the time between two count refreshes of the signs (at least 10).
* `signHeader` is the first line that turns a sign into a board.
* `presets` are named filters. The name is used on signs, NPCs and in `/company board`, ignoring case. Each preset
  may set `label` (shown on signs, the name without it), `name` (part of the order name), `materials`, `anyMaterial`
  (default `true`; `false` requires all materials), `exactMatch` (whole material names instead of parts),
  `minPrice`, `maxPrice`, `minSize` and `maxSize` (a maximum of `0` means no limit).
* `generalSettings.hooks.citizens` and `generalSettings.hooks.fancynpcs` switch the NPC hooks; NPC bindings are kept
  in `boards.yml`, see [NPC boards](#npc-boards).

### Discord webhook
New orders, completed orders and company level ups can be posted to a Discord channel through a webhook. Create a
webhook in the channel settings (Integrations, Webhooks), copy its URL and add the `discord` section to `config.yml`:

```yaml
discord:
  enabled: true
  url: env:COMPANIES_DISCORD_WEBHOOK
  events: [ORDER_CREATED, ORDER_COMPLETED, LEVEL_UP]
  username: Companies
  avatarUrl: ''
  maxPerMinute: 20
  queueSize: 100
```

* `url` is the webhook URL, or `env:NAME` to read it from the environment variable `NAME` of the server process. The
  URL contains the webhook's secret: in a network every node reads the configuration from the database, so prefer
  `env:` there and set the variable on each server. The URL has to use `https://`; another host than Discord is
  accepted with a notice, for relays.
* `events` picks what is posted: `ORDER_CREATED`, `ORDER_COMPLETED` (fully delivered) and `LEVEL_UP`. Order messages
  show the order name, the items and the price, never the owner, the company, fees or taxes. Every event is posted by
  the one server it happens on, so nothing is posted twice.
* `username` and `avatarUrl` override the name and picture of the webhook; leave them empty to keep the webhook's own.
* `maxPerMinute` (1 to 60) limits the requests of each server, which send up to ten messages each and also follow
  Discord's rate limits. `queueSize` (1 to 1000) is the number of waiting messages; when it is full the oldest is
  dropped.
* Changes to `events` apply on `/companyadmin reload`; `enabled`, `url` and the limits need a restart. A webhook that
  Discord reports as gone (401, 403 or 404) is switched off until then.

The URL is never written to the log, and `/companyadmin debug` masks it and every webhook token in the uploaded
report. An `env:` reference is shown as it is, as it holds no secret.

### Notifications, placeholders and rankings
`config.yml` holds these sections. A `config.yml` from an older version does not get them written, but uses the values
shown here until they are added.

```yaml
generalSettings:
  historyDays: 90
notifications:
  pollSeconds: 10
  retentionDays: 30
  pageSize: 10
  actionBar: true
  sound: minecraft:entity.experience_orb.pickup
placeholders:
  refreshSeconds: 300
  offlineCacheSize: 500
  leaderboardSize: 10
  loadingText: ''
top:
  cacheSeconds: 30
```

* `generalSettings.historyDays` is how long history entries are kept after the order ended (at least 1).
* `notifications.pollSeconds` is how often each server looks for notifications sent to its players from another server
  (at least 1). `retentionDays` is how long notifications stay in the inbox (at least 1), `pageSize` the notifications
  on one inbox page (1 to 50).
* `notifications.actionBar` also shows important notifications on the action bar, `sound` plays with them; leave it
  empty for no sound.
* `placeholders.refreshSeconds` is how often a cached value is reloaded (at least 10), `offlineCacheSize` the number of
  offline players whose values are kept, `leaderboardSize` the places of each ranking that `top_…` placeholders can
  read, and `loadingText` the text shown while a value is loaded for the first time.
* `top.cacheSeconds` is how long a page of `/company top` and the place of a company are kept; `0` turns the cache off.

[wakatime]: https://wakatime.com/badge/github/eldoriarpg/companies
[publish]: https://github.com/eldoriarpg/companies/actions/workflows/publish_to_nexus.yml
[verify]: https://github.com/eldoriarpg/companies/actions/workflows/verify.yml
[release]: https://eldonexus.de/#browse/browse:maven-releases:de%2Feldoria%2Fcompanies-api
[snapshot]: https://eldonexus.de/#browse/browse:maven-snapshots:de%2Feldoria%2Fcompanies-api
