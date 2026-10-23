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

MariaDB needs version 10.5 or newer. MySQL does not work, because it lacks features Companies uses.

:::warning
The password is stored as plain text in this file. Give the database user only the rights Companies needs on its own database.
:::

:::info
SQLite works for a single server only. A network of servers needs MariaDB or PostgreSQL. A secondary server refuses to start with SQLite. See [node.yml](./node-yml.md).
:::

See [Installation](../../getting-started/installation.md) for choosing a database, and [Multiple servers](../multiserver.md) for networks.
