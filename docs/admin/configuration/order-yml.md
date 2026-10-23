---
title: order.yml
sidebar_position: 3
---

# order.yml

Order settings: fees, item matching, extensions and repeating orders.

In a network, this file is shared. Edit it on the primary server. See [Configuration](./index.md#in-a-network).

Every key in this file applies on `/companyadmin reload`, except `repeating.checkMinutes`, which needs a restart.

## Default file

```yaml
maxItems: 512
maxMaterials: 5
maxUnclaimedHours: 168
fee:
  fixed: 0.0
  percent: 0.0
  min: 0.0
  max: 0.0
maxExtensions: 2
purgeWarningHours: 24
matching:
  ignoredComponents:
    - minecraft:custom_name
    - minecraft:repair_cost
    - minecraft:lore
  allowDamaged: false
repeating:
  enabled: true
  maxSeriesPerPlayer: 3
  maxCopies: 52
  maxDays: 365
  checkMinutes: 5
```

## General

| Key | Type | Default | Meaning |
|---|---|---|---|
| `maxItems` | number | `512` | Most items in one order, over all lines. |
| `maxMaterials` | number | `5` | Most lines in one order. |
| `maxUnclaimedHours` | number (hours) | `168` | How long an order waits for a company. After that it is removed and the owner gets the price back. |
| `maxExtensions` | number | `2` | How often an owner may restart that time with `/order extend`. `0` turns extending off. |
| `purgeWarningHours` | number (hours) | `24` | The owner is warned this long before an order is removed. |

How many orders a player may have at once is set in [user.yml](./user-yml.md).

## fee

The fee a player pays when placing an order. It goes to the `economy.collectorAccount` in [config.yml](./config-yml.md#economy).

| Key | Type | Default | Meaning |
|---|---|---|---|
| `fee.fixed` | number | `0.0` | Fixed amount per order. |
| `fee.percent` | number | `0.0` | Percent of the order price. `5` means 5 %. |
| `fee.min` | number | `0.0` | Lowest fee. `0` means no lower bound. |
| `fee.max` | number | `0.0` | Highest fee. `0` means no upper bound. |

How the fee is worked out:

1. `fixed` plus `percent` of the price.
2. Raised to `min` and lowered to `max`, when they are above 0.
3. Rounded down to two decimals. It is never below 0.

When an owner raises the price with `/order raise`, they pay `percent` of the increase. `fixed` is not charged again. `min` and `max` apply to the total fee of the order.

## matching

Decides which items count when a company delivers.

An order line is either a **material line**, which any item of that material fills, or an **exact line**, which needs the components of the ordered item. Extra components on a delivered item, like enchantments, are always accepted.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `matching.ignoredComponents` | list of component types | `minecraft:custom_name`, `minecraft:repair_cost`, `minecraft:lore` | Components that never decide a match. They are also removed from items put into an order. |
| `matching.allowDamaged` | true/false | `false` | Accept damaged items. With `false`, only undamaged items can be delivered. |

- Any vanilla component type can be listed in `ignoredComponents`, including `minecraft:custom_data`, which holds the data of items from other plugins.
- Unknown component types are logged and skipped.
- Because the ignored components are removed from ordered items, a name or lore on the ordered item is not shown to workers.

## repeating

Orders that are placed again daily or weekly. See [Repeating orders](../../players/repeating-orders.md) for a guide.

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `repeating.enabled` | true/false | `true` | Turn repeating orders on. With `false`, no new repeating orders can be made, and existing ones post no new copies. | reload |
| `repeating.maxSeriesPerPlayer` | number | `3` | Most active repeating orders of one player. `0` means none. | reload |
| `repeating.maxCopies` | number | `52` | Most copies of one repeating order. At least 1. | reload |
| `repeating.maxDays` | number (days) | `365` | How far ahead the last day of a repeating order may be. At least 1. | reload |
| `repeating.checkMinutes` | number (minutes) | `5` | How often the primary server posts copies that are due. At least 1. | restart |
