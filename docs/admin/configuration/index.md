---
title: Configuration
sidebar_position: 1
---

# Configuration

Every configuration file and what its settings do.

## The files

Companies keeps its files in `plugins/Companies/`. They are created with default values on the first start.

| File | What it holds | Shared in a network | Changes apply |
|---|---|---|---|
| [config.yml](./config-yml.md) | Language, menus, style, economy, notifications, placeholders, rankings, boards and Discord | yes | on reload, some keys on restart |
| [company.yml](./company-yml.md) | Levels, prices, tax, joining, profiles, treasury and order alerts | yes | on reload |
| [order.yml](./order-yml.md) | Order size, fees, item matching, extensions and repeating orders | yes | on reload, one key on restart |
| [user.yml](./user-yml.md) | How many orders a player may have | yes | on reload |
| [database.yml](./database-yml.md) | Where the data is stored | no | on restart |
| [node.yml](./node-yml.md) | The role of this server in a network | no | on restart |
| [boards.yml](./boards-yml.md) | NPCs bound to order boards | no | on reload |
| [base_configuration.yml](./base-configuration-yml.md) | Log level and version bookkeeping | no | see its page |

The messages live in the plugin's language files. Pick the language with `generalSettings.language` in [config.yml](./config-yml.md#generalsettings).

## Reload or restart

`/companyadmin reload` reads every file again. Most settings apply at once. Some are only read when the server starts. Each configuration page says which ones in its tables:

- **reload** means `/companyadmin reload` applies the change.
- **restart** means you have to restart the server.

:::info
`/companyadmin reload` works for players only. The console cannot run it. It needs `companies.admin.reload`.
:::

These need a restart:

- all of `database.yml` and `node.yml`,
- in `config.yml`: `generalSettings.orderCheckInterval`, `generalSettings.messageBlocking`, `generalSettings.hooks`, `placeholders.offlineCacheSize` and `boards.enabled`,
- in `order.yml`: `repeating.checkMinutes`.

## How the files are read

- Keys are written in camelCase, exactly as on these pages, for example `maxOrders`.
- Values of a fixed list, like `INVITE_ONLY` or `DIALOG`, may be written in any case.
- A key that Companies does not know is ignored without a warning. Check the spelling when a setting seems to do nothing.
- A missing key or section uses its default value. Companies does not write it into the file. Add it yourself when you want to change it.

:::danger A broken file is replaced
If a file cannot be read, for example because of a YAML error, a wrong type or an unknown value in a fixed list, Companies moves it aside as `backup_<timestamp>_<file name>` and creates a new file with default values. Your changes are then in the backup file. Check the server log after editing a file.
:::

## In a network

When several servers share one database, one server is the primary and the others are secondaries. See [node.yml](./node-yml.md) and [Several servers](../multiserver.md).

- `config.yml`, `company.yml`, `order.yml` and `user.yml` are shared. Edit them on the **primary** server.
- When the primary starts, it writes these four files into the database.
- When a secondary starts, it reads them from the database and saves them over its own files. Local changes on a secondary are lost then.
- `/companyadmin syncconfiguration` does the same without a restart: on the primary it writes the files to the database, on a secondary it reads them.
- `/companyadmin reload` does not touch the database. It only reads the files of the server it runs on.
- `database.yml`, `node.yml`, `boards.yml` and `base_configuration.yml` belong to each server and are never shared.

To change a shared setting in a network:

1. Edit the file on the primary server.
2. Run `/companyadmin reload` on the primary, then `/companyadmin syncconfiguration` on the primary.
3. Run `/companyadmin syncconfiguration` on every secondary. It saves the new files and applies them at once, like a reload. With [Redis](../multiserver.md#redis), [PostgreSQL notify](../multiserver.md#postgresql-notify) or [low latency mode](../multiserver.md#low-latency-mode), the secondaries do this on their own after step 2.

The reload in step 2 matters: `syncconfiguration` writes what the primary has loaded, not what is in its files. Settings marked **restart** need a restart of each server that should pick them up.
