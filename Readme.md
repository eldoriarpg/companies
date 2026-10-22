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
| `/company permission <member> [give\|remove <permission>]` | Show or change the permissions of a member          |
| `/company top [page] [ordering]`                          | Show the company ranking                            |
| `/company returns`                                        | Take back items returned from aborted orders        |

Confirmations are given with `confirm` after the command, e.g. `/company leave confirm`.

### Joining a company

Every company has a join mode, shown on its profile:

| Join mode      | How players join                                                      |
|----------------|-----------------------------------------------------------------------|
| `open`         | Anyone with `companies.company.join` joins with `/company join`       |
| `applications` | Players apply with a message, members with `INVITE` accept or deny it |
| `invite`       | Only invited players can join                                         |

Invites work in every mode. Invites and applications stay open for `inviteHours` and the players involved are
notified when they are answered or expire; notifications for offline players are shown on their next join. A company
at its level's member limit can not take new members.

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

### Administration: `/companyadmin`

| Command                                                  | Permission                      |
|----------------------------------------------------------|---------------------------------|
| `/companyadmin level [list\|info\|create\|edit\|move\|remove]` | `companies.admin.level`      |
| `/companyadmin calclevel`                                | `companies.admin.calcLevel`     |
| `/companyadmin delete <company>`                         | `companies.admin.delete`        |
| `/companyadmin rename <company> <name>`                  | `companies.admin.rename`        |
| `/companyadmin transferowner <company> <player>`         | `companies.admin.transferOwner` |
| `/companyadmin reload`                                   | `companies.admin.reload`        |
| `/companyadmin syncconfiguration`                        | `companies.admin`               |
| `/companyadmin debug`                                    | `companies.admin`               |

Company names with spaces are quoted in `rename` and `transferowner`.
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
| `companies.admin.level`          | op      | Manage the company levels                                                      |
| `companies.admin.reload`         | op      | Reload the configuration                                                       |
| `companies.admin.rename`         | op      | Rename companies                                                               |
| `companies.admin.transferOwner`  | op      | Transfer the ownership of a company                                            |

`companies.orders.*`, `companies.company.*`, `companies.admin.*` and `companies.*` grant the permissions below them.
Without a `companies.orders.limit.<n>` permission a player may have `maxOrders` active orders, as set in `user.yml`.
`companies.orders.create` covers `/order create` and `/order draft`. `/companyadmin` is shown to holders of any admin
permission, and each subcommand checks its own. Commands a player may not use are hidden from them.

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

[wakatime]: https://wakatime.com/badge/github/eldoriarpg/companies
[publish]: https://github.com/eldoriarpg/companies/actions/workflows/publish_to_nexus.yml
[verify]: https://github.com/eldoriarpg/companies/actions/workflows/verify.yml
[release]: https://eldonexus.de/#browse/browse:maven-releases:de%2Feldoria%2Fcompanies-api
[snapshot]: https://eldonexus.de/#browse/browse:maven-snapshots:de%2Feldoria%2Fcompanies-api
