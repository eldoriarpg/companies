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
    compileOnly("de.eldoria", "companies-api", "version")
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
    <version>version</version>
</dependency>
```

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
| `/order draft done` / `cancel`                   | Place the order and pay for it, or drop the draft             |
| `/order cancel <id>`                             | Cancel an order nobody accepted yet                           |
| `/order extend <id>`                             | Extend an order nobody accepted yet                           |
| `/order raise <id> <amount>`                     | Raise the price of an open order                              |
| `/order receive <id>`                            | Receive the items of a delivered order                        |
| `/order collect`                                 | Collect items that did not fit into your inventory            |
| `/order history [page]`                          | List your ended orders                                        |
| `/order history info <entry>`                    | Show an ended order with its items, price and outcome         |

Drafts expire after 15 minutes.

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

### Company permissions

The owner has every permission and gives the others to members with `/company permission <member> give <permission>`.

| Permission           | Allows                                                            |
|----------------------|-------------------------------------------------------------------|
| `invite`             | Invite players, take invites back and answer applications         |
| `kick`               | Kick members                                                      |
| `manage_orders`      | Accept and abort orders                                           |
| `manage_permissions` | Give and remove permissions                                       |
| `edit_profile`       | Change description, tag, colour and join mode                     |

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
| `company`  | Disbanding, being kicked, ownership transfers and profile changes         | no      |
| `members`  | Members joining, leaving or being kicked                                  | yes     |
| `orders`   | Orders of the company being accepted, aborted, delivered or expiring, and admin changes to them | yes |
| `my_orders`| Your own orders being accepted, done, reopened, removed, about to be removed or changed by an admin | no |
| `payments` | Payments for delivered orders and money granted by an admin               | yes     |
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
| `/companyadmin syncconfiguration`                        | `companies.admin`               |
| `/companyadmin debug`                                    | `companies.admin`               |

Company names with spaces are quoted in `rename` and `transferowner`.

`order info` shows both sides of an order, its times, money, the deliveries of every worker and its history.
`unclaim`, `cancel` and `deliver` ask for a confirmation, given with the button or by appending `confirm`:

* `unclaim` takes a claimed order away from its company. Delivered items go back to the workers and the order is open
  again.
* `cancel … refund` deletes the order and refunds price and fee to the owner; `cancel … keep` deletes it and sends the
  fee to the collector. Delivered items go back to the workers.
* `deliver` closes the order as delivered. The workers are paid for what they delivered and the owner gets the price of
  the missing items back.
* `extend` gives a claimed order more delivery time, or an open order more time before it is removed.

`player` shows a player's company, orders, waiting items and unread notifications. `grant items` stores the item in your
main hand for the player (the held stack, or `amount` of that item), who collects it with `/order collect`; `grant
money` pays the player through the economy. Players affected by an admin action get a notification that does not name
the admin.

Every admin command writes a line with the admin's name and the details to the server log, for example
`[admin] Steve order cancel id=42 refund=true owner=Alex company="Iron Works"`.
Update notifications link to `/companies:eldo-update`, which is available to `companies.updates`.

## Permissions

| Permission                       | Default | Description                                                                    |
|----------------------------------|---------|--------------------------------------------------------------------------------|
| `companies.orders.create`        | all     | Create orders                                                                  |
| `companies.orders.limit.<n>`     | none    | Raise or lower the order limit to `n` active orders. The highest number counts |
| `companies.company.create`       | all     | Found a company                                                                |
| `companies.company.join`         | all     | Be invited to and join a company                                               |
| `companies.updates`              | op      | Hear about new versions on join and download them                              |
| `companies.admin`                | op      | Use `/companyadmin` and create debug reports                                   |
| `companies.admin.calcLevel`      | op      | Recalculate company levels                                                     |
| `companies.admin.delete`         | op      | Delete companies                                                               |
| `companies.admin.grant`          | op      | Grant items and money to players                                               |
| `companies.admin.level`          | op      | Manage the company levels                                                      |
| `companies.admin.orders`         | op      | Inspect, release, cancel, complete and extend any order                        |
| `companies.admin.player`         | op      | Look up the company, orders, items and notifications of a player               |
| `companies.admin.reload`         | op      | Reload the configuration                                                       |
| `companies.admin.rename`         | op      | Rename companies                                                               |
| `companies.admin.transferOwner`  | op      | Transfer the ownership of a company                                            |

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
