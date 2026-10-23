---
title: Several servers
sidebar_position: 6
---

# Several servers

Companies can run on several servers of a network, for example behind Velocity or BungeeCord. All servers share one
database, so players see the same orders and companies everywhere.

## What you need

- **MariaDB or PostgreSQL.** All servers connect to the same database. SQLite does not work for this: a secondary
  server with SQLite refuses to start with "Secondary nodes can not be used with SqLite."
- **The same Companies version** on every server.
- **Exactly one primary server.** All other servers are secondary.

Set up the database in `database.yml` on each server. See [database.yml](./configuration/database-yml.md).

## node.yml

Each server has a `node.yml`:

```yaml
nodeType: PRIMARY
nodeUid: 6f1c1b0e-3f5a-4a52-9c1e-1d2f0b9a7c41
```

| Key | Default | Meaning |
|---|---|---|
| `nodeType` | `PRIMARY` | `PRIMARY` or `SECONDARY` |
| `nodeUid` | random | The id of this server. It is created on the first start |

Set `nodeType: SECONDARY` on every server except one. Changes to `node.yml` need a restart.

At start, each server registers itself in the database and checks the setup. It refuses to start when:

- no server is primary: "No primary node is present. Set one node to PRIMARY in node.yml",
- more than one server is primary: "Multiple nodes are set as primary. ...". The message names the uid of this
  server and of every primary, with its version and when it was last seen,
- the primary runs another Companies version: "Primary node is on version ... Please update this node." or "...
  Please update primary first."

:::warning Keep node.yml
Keep `node.yml` when you move or rebuild a server. A new `nodeUid` registers a new server, and the old entry stays in
the database. If the old entry was the primary, the network then has two primaries and refuses to start.
:::

## Old server entries

`/companyadmin node list` shows every server registered in the database: its uid, `PRIMARY` or `SECONDARY`, its
version and when it was last seen. A server is seen when it starts and when you run `/companyadmin syncconfiguration`
on it. Entries from before this was recorded show `-`.

To remove an entry of a server that no longer runs:

1. Run `/companyadmin node list` and find the old uid. The entry of the server you are on is marked as this server.
2. Run `/companyadmin node remove <uid>`. Companies shows the entry and asks you to confirm.
3. Run `/companyadmin node remove confirm` within 30 seconds.

The configuration the server stored in the database is removed with it. The entry of the server you run the command
on can not be removed.

These commands need `companies.admin.nodes`. They work from the console, and also in the safe mode the plugin falls
into after the error about two primaries.

See [node.yml](./configuration/node-yml.md) for the file reference.

## What is shared

**All data** lives in the database: orders, companies, members, treasuries, inboxes, history and alerts. Every server
sees changes at once.

**The configuration** is copied from the primary server:

| File | Shared |
|---|---|
| `config.yml` | Yes, copied from the primary |
| `company.yml` | Yes, copied from the primary |
| `order.yml` | Yes, copied from the primary |
| `user.yml` | Yes, copied from the primary |
| `database.yml` | No, each server has its own |
| `node.yml` | No, each server has its own |
| `boards.yml` | No, each server has its own NPC bindings |

The primary writes its four files to the database. A secondary replaces its own four files with the copies from the
database and saves them. So edit these files on the primary only. Changes you make on a secondary are overwritten
at the next copy.

### When the configuration is copied

The copy happens at two moments only:

- when a server starts,
- when you run `/companyadmin syncconfiguration` on that server.

`/companyadmin reload` does **not** copy anything. It only reads the local files again.

To change the configuration of a running network:

1. Edit the files on the primary server.
2. Run `/companyadmin reload` on the primary, so it reads your changes.
3. Run `/companyadmin syncconfiguration` on the primary. It writes the files to the database.
4. Run `/companyadmin syncconfiguration` on each secondary. They copy the files from the database.

Some settings are only read when the server starts, for example `boards.enabled`. For those, restart the servers
instead. The [configuration pages](./configuration/index.md) say which settings need a restart.

:::tip
Put secrets such as the Discord webhook URL in an environment variable with `env:`. Then only the variable name is
stored in the database. See [Discord webhook](./discord-webhook.md#the-url).
:::

## Jobs that run on the primary only

Some work runs on a timer. To do it once for the whole network, only the primary server runs it:

| Job | How often |
|---|---|
| Release claimed orders past their deadline, remove orders nobody claimed, warn owners before removal | Every `generalSettings.orderCheckInterval` minutes (default `60`), first after 10 minutes |
| Post due copies of repeating orders | Every `repeating.checkMinutes` minutes (default `5`) |
| Send order alert digests | Every 5 minutes |
| Expire old invites and applications | Every 5 minutes |
| Update market prices | Every 60 minutes |
| Delete old inbox entries, history entries and treasury log entries | Every 60 minutes |

If the primary server is down, these jobs wait until it is back. Orders do not expire and repeating orders do not
post in the meantime. Everything else keeps working on the other servers.

## Notifications across servers

A notification for a player who is online on the same server is shown at once. For a player on another server, it is
stored in the inbox. Every server checks the inbox every `notifications.pollSeconds` seconds (default `10`) and shows
new entries to its players. So a message from another server can take a few seconds.

When players join any server, they get a summary of their unread notifications.

## What stays on one server

Some things are kept in the memory of one server and do not move with the player:

- **Order drafts.** A draft started on one server is not there on another.
- **Open menus, buttons and confirmations.** They belong to the server that showed them.
- **Search queries** of the order search.
- **NPC board bindings** in `boards.yml`.

Some things are updated on each server on their own:

- **Placeholder values** are refreshed every `placeholders.refreshSeconds`, and at once only for events on the same
  server.
- **Board sign counts** are refreshed every `boards.refreshSeconds`, and soon after order changes on the same server.
- **The company ranking** (`/company top`) is cached on each server for `top.cacheSeconds`.
- **The Discord webhook limits** count for each server.

## Admin tools on a network

- The [level editor](./levels.md#the-level-editor) only works on the primary server. On a secondary it answers "This
  can be only used on the primary node."
- `/companyadmin calclevel` works on any server, since all servers share the data.
- Each server writes its own [admin log](./admin-commands.md#the-admin-log) to its own console.
