---
title: Several servers
sidebar_position: 6
---

# Several servers

Companies can run on several servers of a network, for example behind Velocity or BungeeCord. All servers share one
database, so players see the same orders and companies everywhere.

## What you need

:::tip Recommended setup
For several servers, **PostgreSQL is highly recommended** as the database, and **an additional Redis server is highly
recommended** for the messages between the servers. This is the setup the rest of this page works best with.
:::

- **PostgreSQL (highly recommended) or MariaDB.** All servers connect to the same database. SQLite does not work for
  this: a secondary server with SQLite refuses to start with "Secondary nodes can not be used with SqLite."
- **The same Companies version** on every server.
- **Exactly one primary server.** All other servers are secondary.
- **A Redis server (highly recommended).** With Redis, the servers tell each other about changes at once. See
  [Redis](#redis). Without Redis, [PostgreSQL notify](#postgresql-notify) does the same on PostgreSQL, and
  [low latency mode](#low-latency-mode) nearly the same on any database. See [Which one to choose](#which-one-to-choose).

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

## Time zones

Times are stored in UTC, so servers in different zones agree on them. Set the same `timezone` in
[config.yml](configuration/config-yml.md) on every server, so they show the same times and start a day at the same
moment.

## What is shared

**All data** lives in the database: orders, companies, members, treasuries, inboxes, history and alerts. Every server
sees changes at once. So do the key and the logins of the [web dashboard](./dashboard.md), to which every server
connects on its own.

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

The primary stores each shared file in the database as a numbered revision, together with who changed it and when. It
stores a new revision only when the file changed: when it starts, when you run `/companyadmin syncconfiguration` on
it, and when a level is changed in the [level editor](./levels.md#the-level-editor) or on the dashboard. Earlier
revisions stay in the database.

Each secondary takes a new revision on its own:

- at once with [Redis](#redis), [PostgreSQL notify](#postgresql-notify) or in [low latency mode](#low-latency-mode),
- within 30 seconds without them, since every secondary looks for new revisions that often,
- when it starts, and when you run `/companyadmin syncconfiguration` on it.

A new `config.yml` is applied like a reload. The other files are read where they are used, without a reload.

A secondary only replaces a file whose content differs from the primary's copy, and runs the reload work, such as
reconnecting the dashboard and the Discord webhook, only then. A restart of the primary that changed nothing therefore
changes nothing on the secondaries. When a secondary's file was changed on that server since it last took a copy, for
example because you edited it there, the secondary keeps its version as `backup_<timestamp>_<file name>` before it
takes the copy, and its log says so. To tell, it notes each copy it took in the hidden folder `.synced`; the first sync
after updating Companies has no such notes yet, so it keeps a backup of every file that differs once. When a backup
cannot be written, the file stays as it is and the copy is not taken. The primary never writes a file it cannot read
to the database.

`/companyadmin reload` does **not** copy anything. It only reads the local files again.

To change the configuration of a running network:

1. Edit the files on the primary server.
2. Run `/companyadmin reload` on the primary, so it reads your changes.
3. Run `/companyadmin syncconfiguration` on the primary. It stores each changed file as a new revision, and the
   secondaries take it on their own.

Some settings are only read when the server starts, for example `boards.enabled`. For those, restart the servers
instead. The [configuration pages](./configuration/index.md) say which settings need a restart.

:::tip
Set secrets such as the Discord webhook URL as environment variables on each server, see
[Secrets from the environment](./configuration/index.md#secrets-from-the-environment). Their values are never
stored in the database.
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
| Delete messages older than a minute from the message table ([low latency mode](#low-latency-mode) only) | Every 30 seconds |

If the primary server is down, these jobs wait until it is back. Orders do not expire and repeating orders do not
post in the meantime. Everything else keeps working on the other servers.

## Notifications across servers

A notification for a player who is online on the same server is shown at once. For a player on another server, it is
stored in the inbox. Every server checks the inbox every `notifications.pollSeconds` seconds (default `10`) and shows
new entries to its players. So a message from another server can take a few seconds. With [Redis](#redis) or
[PostgreSQL notify](#postgresql-notify), it arrives at once, and in [low latency mode](#low-latency-mode) within about
a second.

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

With [Redis](#redis), [PostgreSQL notify](#postgresql-notify) or in [low latency mode](#low-latency-mode), the first
three follow changes on every server at once.

## Redis

Without Redis, the servers only meet in the database. They find changes made on another server when they next look:
the inbox every few seconds, cached values when they run out. With a Redis server, every server tells the others about
a change the moment it happens. Valkey and KeyDB work as well.

Redis only carries these signals. All data stays in the database, so Redis needs no backup and no persistence, and a
lost message only costs a short delay.

### What gets faster

| What | Without Redis | With Redis |
|---|---|---|
| Notifications for a player on another server | after up to `notifications.pollSeconds` (default `10`) | at once |
| Placeholder values of companies, players (also `player_solo_*`) and rankings | after up to `placeholders.refreshSeconds` | at once |
| `/company top` | after up to `top.cacheSeconds` | at once |
| Open order counts on board signs | after up to `boards.refreshSeconds` | a few seconds after the change |
| Order details, market prices and tab completions | when their cache runs out | at once |
| Configuration from the primary | within 30 seconds | at once |

The inbox is still checked with Redis, in case a message was missed, but only every 5 minutes (or every
`notifications.pollSeconds`, if that is longer). The other timers keep running as before.

### Setup

1. Run a Redis server that all servers of the network can reach. Protect it with a password, and keep it out of
   reach of the internet.
2. Set the `redis` section in `database.yml` on **every** server, with the same server and `database`, and the same
   `messaging.channelPrefix`. See [database.yml](./configuration/database-yml.md#redis).
3. Put the password into the environment variable `COMPANIES_REDIS_PASSWORD`, so it is not in the file.
4. Restart the servers.

Each server logs `Connected to Redis at <host>:<port>.` when it starts. `/companyadmin node list` shows `messages
Redis` for this server, and the same for every other server it heard from in the last minute and a half. A server it
did not hear from shows `-` there.

Several networks can share one Redis server when each network uses its own `messaging.channelPrefix`. If a server receives a
message from a server that is not in its database, it logs a warning naming that server once: two networks use the
same prefix.

### When Redis is away

Nothing breaks without Redis. A server that cannot reach Redis logs it and works as without Redis: it checks the inbox
every `notifications.pollSeconds` again and lets its caches run out as usual. It keeps trying to connect, first
after a second and then less often, up to every 30 seconds. Once it is back, it drops its cached values once, since it
may have missed changes, and switches back to the fast way. It does not switch to
[PostgreSQL notify](#postgresql-notify) or [low latency mode](#low-latency-mode) meanwhile.

A server with `enabled: true` whose password variable is not set logs that and does not use Redis until it is
restarted with the variable set.

The [debug report](./admin-commands.md) shows the Redis state of the server and its address, never the password.

## Low latency mode

Low latency mode gives a network without Redis nearly the same: the servers send each other the same messages Redis
would carry, through a table in the database. Every server reads new messages from that table every second. Turn it on
with `messaging.lowLatency: true` in `database.yml` on **every** server and restart them. See
[database.yml](./configuration/database-yml.md#messaging).

### What it changes

Everything in the table under [What gets faster](#what-gets-faster) behaves as with Redis, except that changes take up
to a second instead of arriving at once. Notifications for a player on another server, placeholder values,
`/company top`, order details, market prices, tab completions and the configuration from the primary all follow
changes on any server within about a second.

This mode takes over from the interval settings. While it is on, `notifications.pollSeconds`,
`placeholders.refreshSeconds` and `top.cacheSeconds` no longer decide how fast changes show up. They stay as a slow
safety fallback, in case a message was missed, exactly as with Redis. Each server says so in its log when it starts:
`Low latency mode is on: ...`.

The table only carries signals. The primary server deletes messages older than a minute every 30 seconds. All data
stays in its own tables.

### What it costs

Each server runs one small query per second on the table, plus one short insert for each change it announces. For a
network of five servers, that is five queries per second on the database. The query only reads the rows written
since the last second, through the primary key, so it stays cheap even on a busy network.

Low latency mode also works with SQLite, but there is only one server then, which has nothing to tell.

### Checking it

`/companyadmin node list` shows `messages low latency` for this server, and for every other server it heard from in
the last minute and a half. When the table can not be read, for example while the database is down, the server falls back to polling and
logs it once, and switches back by itself once it can read the table again. The [debug report](./admin-commands.md)
shows which way the server sends its messages and whether that works.

## PostgreSQL notify

On PostgreSQL, the servers can send each other the messages through the database itself, with PostgreSQL's
`LISTEN` and `NOTIFY`. Messages arrive at once, as with Redis, without a Redis server and without the query per
second of low latency mode. Turn it on with `messaging.postgresNotify: true` in `database.yml` on **every** server
and restart them. See [database.yml](./configuration/database-yml.md#messaging). It needs `storageType: POSTGRES`;
with MariaDB or SQLite the server logs a warning and ignores it.

### What it changes

Everything under [What gets faster](#what-gets-faster) behaves as with Redis: notifications, placeholder values,
`/company top`, order details, market prices, tab completions and the configuration from the primary follow changes
on any server at once. `notifications.pollSeconds`, `placeholders.refreshSeconds` and `top.cacheSeconds` stay only as
a slow safety fallback, exactly as with Redis. Each server says so in its log when it starts:
`PostgreSQL notify is on: ...`.

### What it costs

Each server keeps one extra connection to the database open, outside its connection pool, and waits on it for
messages. Sending a message is one short statement. There is no polling query: a quiet network puts no load on the
database, apart from a tiny check of the waiting connection every 10 seconds.

The messages travel on one channel per database, named after `messaging.channelPrefix`. Two networks on the same
PostgreSQL database, for example in different schemas, need different prefixes.

### Limits

- **No PgBouncer in transaction mode.** Waiting for messages needs a session of its own. Through PgBouncer or another
  pooler in transaction pooling mode, the server does not hear the messages. Connect the servers to PostgreSQL directly,
  or through a pooler in session mode.
- **Small messages only.** PostgreSQL carries messages of up to about 8000 bytes. Companies only sends short signals,
  far below that. A message longer than 7900 bytes would not be sent; the server logs a warning once and the other
  servers pick up the change by polling.
- **Messages sent while a server is not listening are lost for that server**, as with Redis. The safety fallback
  covers them, see below.

### When the connection is lost

If the waiting connection breaks, for example while the database restarts, the server logs it and works as without
messages: it checks the inbox every `notifications.pollSeconds` again and lets its caches run out as usual. It keeps
trying to listen again, first after a second and then less often, up to every 30 seconds. Once it listens again, it
drops its cached values once, since it may have missed changes, and switches back to the fast way. It does not switch
to [low latency mode](#low-latency-mode) meanwhile. This is the same as [When Redis is away](#when-redis-is-away).

`/companyadmin node list` shows `messages PostgreSQL notify` for this server, and for every other server it heard from
in the last minute and a half. The [debug report](./admin-commands.md) shows the state and the database address,
never the password.

## Which one to choose

| | Redis | PostgreSQL notify | Low latency mode |
|---|---|---|---|
| Delay | at once | at once | up to a second |
| Extra service to run | a Redis server | none | none |
| Load on the database | none | one waiting connection per server | one query per second per server |
| Works with | MariaDB, PostgreSQL | PostgreSQL, not through PgBouncer in transaction mode | MariaDB, PostgreSQL |

- **Choose Redis** for the lowest delay, with either database. Together with PostgreSQL, it is the highly recommended
  setup for every network.
- **Choose PostgreSQL notify** when you cannot run Redis. It is as fast as Redis and adds nothing to run.
- **Choose low latency mode** on MariaDB without Redis, when a second of delay is fine.

A network uses exactly one of them; set the same on every server. Each server picks one at start in this order:
Redis (`redis.enabled`), PostgreSQL notify (`messaging.postgresNotify`), low latency mode (`messaging.lowLatency`).
When more than one is turned on, it uses the first, ignores the others and logs one warning naming them. It never
switches to another one while it runs: when its own way breaks, it polls until that way works again.

## Admin tools on a network

- The [level editor](./levels.md#the-level-editor) only works on the primary server. On a secondary it answers "This
  can be only used on the primary node." A change writes the levels to the database right away, see
  [when the configuration is copied](#when-the-configuration-is-copied).
- `/companyadmin calclevel` works on any server, since all servers share the data.
- Each server writes its own [admin log](./admin-commands.md#the-admin-log) to its own console.
