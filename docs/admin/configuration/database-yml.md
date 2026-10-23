---
title: database.yml
sidebar_position: 5
---

# database.yml

Where Companies stores its data.

This file belongs to one server and is never shared. Every change needs a restart.

## Default file

```yaml
storageType: SQLITE
host: localhost
port: '3306'
user: root
password: pass
database: public
schema: public
redis:
  enabled: false
  host: localhost
  port: 6379
  user: ''
  password: env:COMPANIES_REDIS_PASSWORD
  ssl: false
  database: 0
  channelPrefix: companies
messaging:
  lowLatency: false
```

## Keys

| Key | Type | Default | Meaning |
|---|---|---|---|
| `storageType` | `SQLITE`, `MARIADB` or `POSTGRES` | `SQLITE` | The kind of database. |
| `host` | text | `localhost` | Address of the database server. |
| `port` | text | `3306` | Port of the database server. Use `5432` for a usual PostgreSQL server. |
| `user` | text | `root` | Database user. |
| `password` | text | `pass` | Password of the database user. |
| `database` | text | `public` | Name of the database. |
| `schema` | text | `public` | Schema for the tables. Used by PostgreSQL only. |

With `SQLITE`, all other keys are ignored. The data is stored in `plugins/Companies/data.db`, and there is nothing to set up.

With `MARIADB`, `schema` is ignored. The tables go into `database`.

With `POSTGRES`, the schema must exist already. Companies creates its tables, but not the schema.

MariaDB needs version 10.5 or newer. MySQL does not work, because it lacks features Companies uses. PostgreSQL needs version 13 or newer.

:::warning
The password is stored as plain text in this file. Give the database user only the rights Companies needs on its own database.
:::

:::info
SQLite works for a single server only. A network of servers needs MariaDB or PostgreSQL. A secondary server refuses to start with SQLite. See [node.yml](./node-yml.md).
:::

## redis

An optional Redis (or Valkey, KeyDB) server the servers of a network use to tell each other about changes at once.
A single server does not need it. See [Several servers](../multiserver.md#redis) for what it changes.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `enabled` | true/false | `false` | Use Redis. |
| `host` | text | `localhost` | Address of the Redis server. |
| `port` | number | `6379` | Port of the Redis server. |
| `user` | text | `''` | User for servers with access control lists. Leave it empty for the default user. |
| `password` | text | `env:COMPANIES_REDIS_PASSWORD` | Password of the Redis server. `env:NAME` reads it from the environment variable `NAME`. Leave it empty for a server without password. |
| `ssl` | true/false | `false` | Connect with TLS. |
| `database` | number | `0` | Number of the Redis database. |
| `channelPrefix` | text | `companies` | Start of every channel name. Networks sharing one Redis server need different prefixes. |

All servers of a network need the same `host`, `port`, `database` and `channelPrefix`.

With `env:`, the password never appears in the file or the logs. If the variable is not set, Companies logs a warning
and runs without Redis. The debug report masks the password either way.

## messaging

How the servers of a network send each other messages without Redis. A single server does not need it.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `lowLatency` | true/false | `false` | Turn on [low latency mode](../multiserver.md#low-latency-mode): the servers send each other messages through a table in the database, and every server reads it every second. With Redis set up as well, it is used while Redis can not be reached. |

Use the same value on all servers of a network. Each server reads the table once per second, so this costs one small
query per second for each server.

See [Installation](../../getting-started/installation.md) for choosing a database, and [Several servers](../multiserver.md) for networks.
