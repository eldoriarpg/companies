---
title: node.yml
sidebar_position: 7
---

# node.yml

The role of this server when several servers share one database.

This file belongs to one server and is never shared. Every change needs a restart. A single server keeps the defaults.

## Default file

```yaml
nodeType: PRIMARY
nodeUid: 5f0c2b9e-8a5d-4c1e-9f3a-2d7b6e4a1c08
name: ''
dashboard:
  serverKey: ''
```

The `nodeUid` in your file is different. It is made at random when the file is created.

## Keys

| Key | Type | Default | Meaning |
|---|---|---|---|
| `nodeType` | `PRIMARY` or `SECONDARY` | `PRIMARY` | The role of this server. |
| `nodeUid` | UUID | random | The id of this server in the database. Do not change it, and do not copy it to another server. |
| `name` | text | `''` | A name for this server of at most 32 characters, such as `survival`. The [web dashboard](../dashboard.md#admin-tools) shows it where an admin picks a server. Empty shows the role and the start of the `nodeUid`. New in 2.1. |
| `dashboard.serverKey` | text | `''` | The key of the [web dashboard](../dashboard.md), or the token of a self-hosted gateway. The environment variable `COMPANIES_DASHBOARD_KEY` wins over it, see [Secrets from the environment](./index.md#secrets-from-the-environment). Only the primary reads it: it moves the key into the database, which all servers share, and stores it again only when it changes, so it never undoes a rotation or a key entered with `/companyadmin dashboard key`. New in 2.1. |

A server with a new `nodeUid` leaves its old entry in the database. Remove old entries with `/companyadmin node`, see [Several servers](../multiserver.md#old-server-entries).

## Settings for this server only

:::info New in 2.1
:::

A few settings of [config.yml](./config-yml.md) depend on what one server has, such as its plugins. A server can set
them for itself under `overrides`. Anything left out uses the shared value from `config.yml`. Overrides are never
shared. The branding applies on `/companyadmin reload`, the others on restart.

| Key | Instead of `config.yml` |
|---|---|
| `overrides.messageBlocking` | `generalSettings.messageBlocking` |
| `overrides.boardsEnabled` | `boards.enabled` |
| `overrides.hooks.placeholderapi` | `generalSettings.hooks.placeholderapi` |
| `overrides.hooks.citizens` | `generalSettings.hooks.citizens` |
| `overrides.hooks.fancynpcs` | `generalSettings.hooks.fancynpcs` |
| `overrides.branding.name` | `dashboard.branding.name` |
| `overrides.branding.icon` | `dashboard.branding.icon` |

A lobby without Citizens and without order board signs:

```yaml
overrides:
  boardsEnabled: false
  hooks:
    citizens: false
  branding:
    name: Lobby
```

A shared change to an overridden setting does not mark the server as waiting for a restart; changing the override
itself and reloading does.

A value of the wrong type, such as `messageBlocking: maybe`, or a key not in the table is named in the server log and
ignored; the shared value is used. It stays in the file so you can fix it.

## Primary and secondary

A network has exactly one primary server. All other servers are secondaries.

The primary server:

- writes the shared files (`config.yml`, `company.yml`, `order.yml`, `user.yml`) into the database when it starts,
- runs the timed jobs: expiring and removing orders, ending old invites and applications, updating market prices, cleaning up history, inbox and treasury log, posting repeating orders and sending alert summaries,
- is the only server where the level editor works.

A secondary server reads the shared files from the database when it starts, and saves them over its own files.

Every server checks at start that there is exactly one primary, and that the primary runs the same Companies version. If not, Companies does not start. Update all servers together.

:::info
A secondary server needs MariaDB or PostgreSQL. With `storageType: SQLITE` in [database.yml](./database-yml.md), a secondary refuses to start.
:::

See [Configuration](./index.md#in-a-network) for how to change shared settings, and [Several servers](../multiserver.md) for a guide.
