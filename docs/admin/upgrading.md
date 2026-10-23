---
title: Upgrading from 1.x
sidebar_position: 8
---

# Upgrading from 1.x

Companies 2.0 is a big update. Your companies, orders and settings come with you, but some things work differently
now. Read this page before you update.

## Before you start

1. Stop the server.
2. Back up your database. For SQLite, that is the file `plugins/Companies/data.db`.
3. Back up the folder `plugins/Companies`.

The database is changed on the first start of 2.0. Version 1.x cannot use it afterwards, so your backup is the only
way back.

## New requirements

| | 1.x | 2.0 |
|---|---|---|
| Server | Spigot or Paper, 1.16 and newer | **Paper only**, 1.21.11 and newer |
| Java | 21 | **25** |
| Vault and an economy plugin | needed | needed |
| ProtocolLib | for chat menus | optional |

Spigot is no longer supported. If your server runs Spigot, move to [Paper](https://papermc.io/) first.

See [Installation](../getting-started/installation.md) for everything Companies 2.0 needs.

## Your data

You do not have to do anything. On the first start, Companies brings the database up to date step by step, from
version 1.0 to 1.7. This happens for SQLite, MariaDB and PostgreSQL alike. The data moves into the new tables:

- Deliveries move into the new delivery records.
- Old company notifications move into the new [inbox](../players/inbox.md).
- Payouts of finished orders are filled in for the new statistics.

Large databases can take a moment. Wait until the console says the plugin is enabled.

### On several servers

All servers must run the same version, and the primary server has to be updated first. A secondary server that
starts before its primary is updated refuses to start with "Please update primary first."

1. Stop all servers.
2. Put Companies 2.0 on every server.
3. Start the primary server and wait until it is up.
4. Start the secondary servers.

See [Several servers](./multiserver.md).

## Configuration

Your configuration files stay as they are. New settings that are missing from your files use their default values.
They are not added to your files on the primary server, so add a section when you want to change it. Secondary
servers get complete files from the primary.

The [configuration pages](./configuration/index.md) show every setting. Here is what you should look at:

### Menus

2.0 can show menus as dialog windows. An old `config.yml` has no `menus` entry and keeps the chat menus. To switch
to dialogs, add this line to `config.yml`:

```yaml
menus: DIALOG
```

Players with a client older than 1.21.6 always get chat menus.

### Update checks

`generalSettings.checkUpdates` is now `generalSettings.updates.notice`. Companies still reads the old key once, but it
does not write it back. Replace it with the new section:

```yaml
generalSettings:
  updates:
    notice: true
    autoUpdate: false
    askForAutoUpdate: true
```

### Levels

New installations come with four starter levels. Your `company.yml` keeps the levels you had. If your list is empty
(`level: []`), levels stay off. Add levels with the [level editor](./levels.md#the-level-editor).

Levels have new settings: `maxOrderSize`, `deliveryHoursBonus`, `taxPercent` and `maxAlerts`. See [Levels](./levels.md).

### New sections

These sections are new. Their defaults work, so add them only to change something:

| File | Section | Page |
|---|---|---|
| `config.yml` | `style`, `economy`, `notifications`, `placeholders`, `top`, `boards`, `discord` | [config.yml](./configuration/config-yml.md) |
| `config.yml` | `generalSettings.historyDays`, `generalSettings.hooks.citizens`, `generalSettings.hooks.fancynpcs` | [config.yml](./configuration/config-yml.md) |
| `company.yml` | `allowMemberOrders`, `inviteHours`, `expiryWarningHours`, `tax`, `joining`, `profile`, `treasury`, `alerts` | [company.yml](./configuration/company-yml.md) |
| `order.yml` | `fee`, `maxExtensions`, `purgeWarningHours`, `matching`, `repeating` | [order.yml](./configuration/order-yml.md) |

## Permissions

The player permissions are now given to everyone by default:

| Permission | 1.x | 2.0 |
|---|---|---|
| `companies.orders.create` | not set up, so ops only | everyone |
| `companies.company.create` | ops only | everyone |
| `companies.company.join` | not set up, so ops only | everyone |
| `companies.orders.repeat` | did not exist | everyone |
| `companies.boards.use` | did not exist | everyone |

If you gave these permissions to a player group, you can remove that now. If some players should not found companies
or place orders, take the permission away from them in your permission plugin.

The wildcard for orders is `companies.orders.*`. The node `companies.order.*` from 1.x does nothing.

There are new admin permissions, one for each admin tool. See [Permissions](../reference/permissions.md).

## Commands

Most commands keep their names. These changed:

| 1.x | 2.0 |
|---|---|
| `/order create name\|add\|remove\|price\|amount\|done\|cancel ...` | `/order create <name>` starts a draft. Edit it with `/order draft name\|add\|remove\|amount\|price\|repeat\|done\|cancel ...` |
| `/company invite accept\|deny` | `/company invites accept\|deny <company>` |
| `/company order search page <page>` | Use the page buttons below the results |
| `/company chatblock` | Removed |

`/company chatblock` was a hidden helper of the old chat menus. Holding back chat while a menu is open now happens on
its own. You can turn it off with `generalSettings.messageBlocking: false`. Without ProtocolLib, only player chat is
held back. With ProtocolLib, system messages are held back too. Dialog menus never hold back chat.

There are many new commands. See [Commands](../reference/commands.md).

## For developers

The API changed a lot. See [Migrating from 1.x](../developers/migrating-from-1x.md).
