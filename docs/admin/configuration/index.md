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
`/companyadmin reload` works in game and from the console. It needs `companies.admin.reload`.
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

:::danger A broken file
If a file cannot be read, for example because of a YAML error, a wrong type or an unknown value in a fixed list, the
server log says so. Check it after editing a file.

- `config.yml`, `company.yml`, `user.yml` and `order.yml` stay as they are, so you can fix them. Until then Companies
  keeps their last values, or uses their defaults right after a start, does not save them and does not share them
  with the other servers of a network. Run `/companyadmin reload` once you fixed the file; it names the files that
  still cannot be read. While `company.yml` cannot be read, the level editor refuses changes. Running on defaults
  can still affect play, for example companies checked against the default levels, so fix the file soon.
- Any other file is moved aside as `backup_<timestamp>_<file name>` and replaced with a new file with default
  values. Your changes are then in the backup file.
:::

## Secrets from the environment

Three secrets can come from environment variables of the server process instead of the files:

| Variable | Setting |
|---|---|
| `COMPANIES_DISCORD_WEBHOOK` | `discord.url` in [config.yml](./config-yml.md#discord) |
| `COMPANIES_REDIS_PASSWORD` | `redis.password` in [database.yml](./database-yml.md#redis) |
| `COMPANIES_DASHBOARD_KEY` | `dashboard.serverKey` in [node.yml](./node-yml.md) |

A variable that is set wins over the file. Companies never writes its value anywhere: not into the file, not into the
database copy of a shared file, not into a debug report. Leave the setting in the file empty when you use the
variable. Set the variable on every server that needs it; in a network that means each server, since the variable is
not copied.

```bash
export COMPANIES_DISCORD_WEBHOOK='https://discord.com/api/webhooks/...'
```

## In a network

When several servers share one database, one server is the primary and the others are secondaries. See [node.yml](./node-yml.md) and [Several servers](../multiserver.md).

- `config.yml`, `company.yml`, `order.yml` and `user.yml` are shared. Edit them on any server, see below.
- The primary stores each of these four files in the database as a new revision when it changed: when it starts and when you run `/companyadmin syncconfiguration` on it.
- Each secondary takes a new revision on its own, at once with a realtime bus or within 30 seconds without one, and when it starts. It saves it over its own file; a change made on the secondary is kept as a backup first, see [Several servers](../multiserver.md#when-the-configuration-is-copied).
- `/companyadmin reload` does not touch the database. It only reads the files of the server it runs on.
- `database.yml`, `node.yml`, `boards.yml` and `base_configuration.yml` belong to each server and are never shared.

To change a shared setting in a network:

1. Edit the file on any server.
2. Run `/companyadmin reload` there. It names the keys you changed.
3. Run `/companyadmin reload confirm` within 30 seconds. The change becomes the next revision and the other servers take it on their own. If another server changed the same file meanwhile, your version is kept as a `conflict_` file instead, see [Several servers](../multiserver.md#when-the-configuration-is-copied). Settings marked **restart** need a restart of each server that should pick them up.
