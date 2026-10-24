---
title: order.yml
sidebar_position: 3
---

# order.yml

Order settings: fees, item matching, extensions, repeating orders and working alone.

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
solo:
  enabled: false
  maxActive: 1
  maxOrderSize: 64
  deliveryHours: 24
  taxPercent: 0.0
  forCompanyMembers: false
  abortCooldownMinutes: 60
market:
  minOrders: 3
  historyDays: 365
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

## solo

Players who take an order alone, without a company. See [Working alone](../../players/working-alone.md) for a guide.
Working alone also needs the permission `companies.orders.solo`, which every player has by default.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `solo.enabled` | true/false | `false` | Turn working alone on. With `false`, nobody can take an order alone. Orders already taken can still be delivered or given back. |
| `solo.maxActive` | number | `1` | Most orders one player works on alone at the same time. At least 1. |
| `solo.maxOrderSize` | number | `64` | Most items, over all lines, of an order a player may take alone. `0` means no limit. |
| `solo.deliveryHours` | number (hours) | `24` | How long a player has to deliver an order taken alone. At least 1. Admins can add time with `/companyadmin order extend`. |
| `solo.taxPercent` | number | `0.0` | Tax on the payout of an order delivered alone. `5` means 5 %. It goes to the `economy.collectorAccount` in [config.yml](./config-yml.md#economy), like the tax of companies. |
| `solo.forCompanyMembers` | true/false | `false` | Let members of a company take orders alone as well. |
| `solo.abortCooldownMinutes` | number (minutes) | `60` | After a player gives an order back or lets it expire, they cannot take another order alone for this long. `0` turns the cooldown off. |

- The whole price of the order goes to the player, minus the tax. No company keeps a treasury share.
- Orders delivered alone count for nobody's company. They do not change any level or ranking.
- On a network, the claims and cooldowns are stored in the database, so they hold on every server. Expired orders are
  released by the primary server, like those of companies.

## market

:::info New in 2.1
:::

The market prices on the [web dashboard](../dashboard.md).

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `market.minOrders` | number | `3` | How many delivered orders a material needs before the dashboard shows its prices. Below that, only the amount ordered is shown, so a single order cannot be traced back. The same applies to each day of the price history; only such days count towards a price change. At least 1. | reload |
| `market.historyDays` | number | `365` | How many days of price history are kept. The primary server records it every half hour from the orders companies delivered, as the order history holds them. Days already recorded stay when the order history is purged, so this may be longer than `historyDays` in `config.yml`; on the first start the history only reaches back as far as the order history does. Days end at midnight in the zone of `timezone` in `config.yml`. At least 14. | reload |
