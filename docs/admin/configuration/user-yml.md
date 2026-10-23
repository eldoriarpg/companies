---
title: user.yml
sidebar_position: 4
---

# user.yml

Player settings: how many orders a player may have.

In a network, this file is shared. Edit it on the primary server. See [Configuration](./index.md#in-a-network).

## Default file

```yaml
maxOrders: 7
```

## Keys

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `maxOrders` | number | `7` | Most active orders a player may have at once. | reload |

To give some players a different limit, use the permission `companies.orders.limit.<n>`, for example `companies.orders.limit.15`. The highest number a player has counts, and it can also be lower than `maxOrders`. See [Order limit](../../reference/permissions.md#order-limit).

The permission is only checked while the player is online. For offline players, for example in placeholders, `maxOrders` counts.
