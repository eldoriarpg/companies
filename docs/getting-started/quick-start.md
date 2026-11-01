---
title: Quick start
sidebar_position: 1
---

# Quick start

The most important things to get right when you set up Companies, in the order you meet them. Each step links to the
page with the details.

## 1. Install what Companies needs

- Paper 1.21.11 or newer and Java 25.
- [Vault](https://github.com/MilkBowl/Vault) and an economy plugin that hooks into Vault, such as EssentialsX Economy.
  Without an economy, Companies stays in a safe mode where nothing that moves money works.
- The Companies jar from [Lyna](https://lyna.eldoria.de/products/2), in the `plugins` folder.

Start the server once. Companies creates `plugins/Companies` with its configuration files. See
[Installation](./installation.md).

## 2. Choose the database before players start

Companies has no tool to move data from one database to another, so decide now.

| Your setup | Database |
|---|---|
| One server | SQLite, the default. Nothing to set up. |
| One server with a database you already run | MariaDB or PostgreSQL |
| Several servers sharing companies and orders | **PostgreSQL, highly recommended**, plus an additional **Redis** server, also highly recommended |

Set it in [database.yml](../admin/configuration/database-yml.md) and restart. For a network, also read
[Several servers](../admin/multiserver.md): every server needs the same database, and exactly one is the primary.

## 3. Connect the dashboard

The web dashboard is where players manage orders and companies, and where you finish the setup. It is on from the
first start and needs only its key:

1. Get a dashboard key on the Companies page of [Lyna](https://lyna.eldoria.de), where you got the plugin.
2. Set it as the environment variable `COMPANIES_DASHBOARD_KEY` of the primary server and restart, or enter it once
   with `/companyadmin dashboard key <key>`, or in the dialog of `/companyadmin setup` in game.
3. `/companyadmin dashboard status` shows whether the server is connected.

Until the key is stored, the primary server's console and joining admins who may enter it are reminded. Then run
`/company dashboard`, log in, and open **Admin → Setup**. It walks through the next steps one page at a time and saves
them for every server. See [Web dashboard](../admin/dashboard.md).

## 4. Set the economy

The Setup's **Economy** page has these; look at them before players spend money:

- The founding price: what founding a company costs.
- The order fee: what players pay on top of an order's price.
- The tax: what is taken from the workers' earnings.
- The collector account: where fees and taxes go. When it is empty, that money leaves the economy.

They live in `company.yml`, `order.yml` and `config.yml`. See [Economy](../admin/economy.md).

## 5. Shape the levels

Companies grow through levels, which set how many members and orders a company may have. The defaults are a starting
point; change them in the dashboard's level editor (**Admin → Levels**) or with `/companyadmin level` in game. See
[Levels](../admin/levels.md).

## 6. Pick the language

The Setup's **Language and menus** page sets the language (`en_US`, `de_DE` or `fr_FR`), your time zone, and whether
menus open as dialogs or in chat. See [config.yml](../admin/configuration/config-yml.md).

## 7. Integrations

The Setup's **Integrations** page turns these on or off. Plugin hooks and board signs take effect after a restart of
each server.

- **Order boards:** signs and NPCs that show open orders. See [Boards](../admin/boards.md).
- **Discord:** post new and completed orders and level-ups to a channel. See
  [Discord webhook](../admin/discord-webhook.md).
- **PlaceholderAPI:** `%companies_…%` placeholders. See [Placeholders](../reference/placeholders.md).

## 8. The dashboard itself

The Setup's **Dashboard** page sets the name and icon players see on the dashboard and which of its parts they may
use. See [Web dashboard](../admin/dashboard.md#settings).

## 9. Check the permissions

Players can use everything they need by default, and only operators get the admin commands. With a permission
plugin, the two usual changes are `companies.orders.limit.<n>` (how many open orders a player may have) and
`companies.orders.repeat` (repeating orders). The Setup's **Permissions** page lists them too. See
[Permissions](../reference/permissions.md).

## Things to keep in mind

- Settings saved on the dashboard reach every server at once; the few that need a restart say so when you save. After
  editing a file by hand, run `/companyadmin reload` and check the server log. A file with an error is moved aside as a
  backup and replaced with defaults. See [Configuration](../admin/configuration/index.md).
- `database.yml` and `node.yml` are read only at startup; changing them needs a restart.
- Keep secrets such as the dashboard key or the Redis password in environment variables, not in the files. See
  [Secrets from the environment](../admin/configuration/index.md#secrets-from-the-environment).
- Back up your database regularly, and always before a major update. A major update can change the database for good;
  see [Upgrading from 1.x](../admin/upgrading.md).

Then take the [first steps](./first-steps.md) to see the whole loop of ordering, claiming and delivering once.
