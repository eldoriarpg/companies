---
title: Installation
sidebar_position: 1
---

# Installation

This page gets Companies running on your server. You install the plugin, start the server once and pick a database.

## Requirements

| What | Version |
|---|---|
| Server | [Paper](https://papermc.io/) 1.21.11 or newer |
| Java | 25 or newer |
| [Vault](https://github.com/MilkBowl/Vault) | any current version |
| Economy plugin | any economy that registers with Vault |

Companies pays for everything through Vault: orders, fees, founding, the treasury and the workers' payouts. Vault alone
is not enough. You also need an economy plugin that hooks into Vault, such as EssentialsX Economy.

:::warning
Without an economy, Companies starts but stays in a safe mode. It logs
`No Vault economy provider is registered. Install an economy plugin that supports Vault.` and nothing that moves money
works. Install an economy plugin and restart the server.
:::

## Optional plugins

These plugins are not needed, but Companies uses them when they are there.

| Plugin | What it adds |
|---|---|
| [PlaceholderAPI](https://www.spigotmc.org/resources/6245/) | `%companies_…%` placeholders for scoreboards, tab lists and chat. See [Placeholders](../reference/placeholders.md). |
| [ProtocolLib](https://www.spigotmc.org/resources/1997/) | While a chat menu is open, Companies holds back other chat messages. Player chat is held without ProtocolLib. With ProtocolLib, system messages from the server and other plugins are held as well. |
| [Citizens](https://citizensnpcs.co) | NPCs that open an order board when clicked. See [Boards](../admin/boards.md). |
| [FancyNpcs](https://modrinth.com/plugin/fancynpcs) | The same, for FancyNpcs NPCs. |

Companies checks for these plugins once, right after the server started. A plugin you add later is used after the next
restart.

## Install the plugin

Download Companies from [Lyna](https://lyna.eldoria.de/products/2). That is the only place to get the plugin jar.

1. Stop the server.
2. Put the Companies jar into the `plugins` folder.
3. Make sure Vault and an economy plugin are in the `plugins` folder too.
4. Start the server.

On the first start, Paper downloads the database drivers Companies needs. The server needs internet access for this
once.

## The first start

On the first start Companies creates its folder `plugins/Companies` with the configuration files:

| File | What it holds |
|---|---|
| `config.yml` | General settings, menus, notifications, boards, the Discord webhook |
| `company.yml` | Company levels, founding price, joining, profiles, treasury, order alerts |
| `order.yml` | Order limits, fees, item matching, repeating orders |
| `user.yml` | How many orders a player may have open |
| `database.yml` | Which database is used |
| `node.yml` | Whether this server is the primary server of a network |

With the default settings, Companies works right away. It stores its data in a SQLite file, `data.db`, in the same
folder. Every player may place orders, found a company and join one. Only operators get the admin commands.

The defaults of every file are on the [Configuration](../admin/configuration/index.md) pages.

## Choosing a database

Companies supports three databases. Set the one you want in [database.yml](../admin/configuration/database-yml.md).

| Database | Good for | Notes |
|---|---|---|
| SQLite | One server | The default. Nothing to set up. |
| MariaDB | One server or a network | MariaDB 10.5 or newer. MySQL does not work. |
| PostgreSQL | One server or a network | Use PostgreSQL 13 or newer. The schema you configure has to exist already. |

Pick SQLite if you run a single server and do not want to manage a database. Pick MariaDB or PostgreSQL if you already
run one, or if several servers should share the same companies and orders. A network needs one of these two, because
SQLite can only be used by one server. For a network, PostgreSQL is highly recommended, together with an additional
Redis server. [Several servers](../admin/multiserver.md) explains how to set up a network.

:::tip
Choose the database before players start using the plugin. Companies has no tool to move data from one database to
another.
:::

To switch to MariaDB or PostgreSQL:

1. Stop the server.
2. Open `plugins/Companies/database.yml`.
3. Set `storageType` to `MARIADB` or `POSTGRES`, and fill in host, port, user, password, database and schema.
4. Start the server.

Companies creates its tables on the first connection and updates them on its own after a plugin update. Database
settings are read only at startup, so a change needs a restart.

## Permissions

Players can use everything they need by default. Only the admin permissions are for operators. If you use a
permission plugin, look at [Permissions](../reference/permissions.md) for the full list. Two things are often changed:

- `companies.orders.limit.<n>` sets how many open orders a player may have, for example `companies.orders.limit.15`.
- `companies.orders.repeat` lets players make orders repeat. Remove it if you do not want repeating orders.

## Next steps

- Take the [first steps](./first-steps.md) to see how orders and companies work.
- Read [Economy](../admin/economy.md) to set prices, fees and taxes.
- Read [Levels](../admin/levels.md) to shape how companies grow.
