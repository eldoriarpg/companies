---
title: company.yml
sidebar_position: 2
---

# company.yml

Company settings: levels, prices, tax, joining, profiles, treasury and alerts.

In a network, this file is shared. Edit it on the primary server. See [Configuration](./index.md#in-a-network).

Every key in this file applies on `/companyadmin reload`.

## Default file

The `level` list is shortened here to the first level. A new file has four levels, see [Starter levels](#starter-levels).

```yaml
level:
  - requirement:
      orderCount: 0
      earnedMoney: 0.0
      deliveredItems: 0
      memberCount: 1
    settings:
      maxMembers: 5
      maxOrders: 2
      maxOrderSize: 0
      deliveryHoursBonus: 0
    level: 1
    levelName: Startup
deliveryHours: 48
foundingPrice: 20000.0
renamePrice: 10000.0
expiredOrderPenalty: 3
abortedOrderPenalty: 1
allowMemberOrders: false
inviteHours: 24
expiryWarningHours: 12
tax:
  percent: 0.0
joining:
  defaultMode: INVITE_ONLY
  maxApplicationLength: 120
  maxPendingApplications: 3
profile:
  maxDescriptionLength: 160
  tagLength:
    min: 2
    max: 5
  blockedTags: [ADMIN, MOD, STAFF]
  colors: [white, gray, dark_gray, red, dark_red, gold, yellow, green, dark_green, aqua, dark_aqua, blue, dark_blue, light_purple, dark_purple]
treasury:
  enabled: true
  maxPayoutShare: 25
  minTransaction: 1.0
  payRename: true
  logRetentionDays: 365
  logPageSize: 10
alerts:
  enabled: true
  maxPerCompany: 3
  minMinutesBetween: 15
```

## General

| Key | Type | Default | Meaning |
|---|---|---|---|
| `deliveryHours` | number (hours) | `48` | Time a company has to deliver an order after claiming it. A level can add more with `deliveryHoursBonus`. |
| `foundingPrice` | number | `20000.0` | Price to found a company. |
| `renamePrice` | number | `10000.0` | Price to rename a company. `0` or less makes it free. |
| `expiredOrderPenalty` | number | `3` | Added to the failed orders of a company when a claimed order runs out of time. |
| `abortedOrderPenalty` | number | `1` | Added to the failed orders of a company when it gives a claimed order back. |
| `allowMemberOrders` | true/false | `false` | Allow a company to claim orders of its own members. |
| `inviteHours` | number (hours) | `24` | How long invites and applications stay open. |
| `expiryWarningHours` | number (hours) | `12` | When a member joins the server, claimed orders that end within this time are shown to them. |

Failed orders are subtracted from the completed orders of a company. This counts for the level requirement `orderCount`, the `orders` ranking over all time and the `company_orders` [placeholder](../../reference/placeholders.md#company-placeholders). See [Levels](../levels.md#penalties).

## level

The list of company levels. The first entry is level 1, the second level 2, and so on. A company has the highest level whose requirements it meets, counting up from level 1 without gaps. An empty list turns levels off.

You can edit levels in game with `/companyadmin level`. See [Levels](../levels.md) for a guide.

| Key | Type | Default for a new level | Meaning |
|---|---|---|---|
| `levelName` | text | `none` | Name of the level. |
| `level` | number | set by Companies | Number of the level. Companies sets it when the file is created and in the level editor. |
| `requirement.orderCount` | number | `1` | Completed orders needed. |
| `requirement.earnedMoney` | number | `1000.0` | Money earned needed. |
| `requirement.deliveredItems` | number | `1000` | Delivered items needed. |
| `requirement.memberCount` | number | `1` | Members needed. |
| `settings.maxMembers` | number | `20` | Most members the company may have. |
| `settings.maxOrders` | number | `5` | Most orders the company may work on at once. |
| `settings.maxOrderSize` | number | `0` | Most items an order may have for the company to claim it. `0` or less means no limit. |
| `settings.deliveryHoursBonus` | number (hours) | `0` | Hours added to `deliveryHours`. |
| `settings.taxPercent` | number | not set | Tax for this level. Replaces `tax.percent`. Leave it out to use `tax.percent`. |
| `settings.maxAlerts` | number | not set | Order alerts for this level. Replaces `alerts.maxPerCompany`. `0` means none. Leave it out to use `alerts.maxPerCompany`. |

The defaults in this table are those of a level added with `/companyadmin level create`.

:::warning
The `level` numbers have to run 1, 2, 3 in the order of the list. Companies does not renumber them after loading the file, and numbers out of order give companies wrong levels. The level editor keeps them in order. If you edit the file by hand, renumber the entries after you move or remove one.
:::

### Starter levels

A new `company.yml` comes with these four levels. They have no `maxOrderSize` limit, no delivery bonus and no own tax or alert limit.

| Level | Name | Orders | Money earned | Items delivered | Members | Max members | Max orders |
|---|---|---|---|---|---|---|---|
| 1 | Startup | 0 | 0 | 0 | 1 | 5 | 2 |
| 2 | Business | 10 | 20000 | 1000 | 2 | 10 | 4 |
| 3 | Corporation | 50 | 100000 | 10000 | 4 | 20 | 7 |
| 4 | Conglomerate | 200 | 500000 | 50000 | 8 | 40 | 12 |

## tax

| Key | Type | Default | Meaning |
|---|---|---|---|
| `tax.percent` | number | `0.0` | Tax on the payouts to workers, in percent. `5` means 5 %. A level can set its own with `taxPercent`. |

Taxes go to the `economy.collectorAccount` in [config.yml](./config-yml.md#economy).

## joining

| Key | Type | Default | Meaning |
|---|---|---|---|
| `joining.defaultMode` | `OPEN`, `APPLICATIONS` or `INVITE_ONLY` | `INVITE_ONLY` | Join mode of newly founded companies. Existing companies keep theirs. |
| `joining.maxApplicationLength` | number | `120` | Most characters in an application message. |
| `joining.maxPendingApplications` | number | `3` | Most open applications of one player, across all companies. |

## profile

| Key | Type | Default | Meaning |
|---|---|---|---|
| `profile.maxDescriptionLength` | number | `160` | Most characters in a company description. |
| `profile.tagLength.min` | number | `2` | Fewest characters in a tag. At least 1, at most `max`. |
| `profile.tagLength.max` | number | `5` | Most characters in a tag. From 1 to 5. |
| `profile.blockedTags` | list of text | `[ADMIN, MOD, STAFF]` | Tags nobody may use. Case does not matter. |
| `profile.colors` | list of colours | 15 named colours | Colours a company may choose for its tag and name. Named colours such as `gold`, or hex values such as `'#4a90d9'`. |

Tags are unique. Two companies cannot have the same tag.

## treasury

| Key | Type | Default | Meaning |
|---|---|---|---|
| `treasury.enabled` | true/false | `true` | Turn the treasury on. With `false`, the bank commands are hidden and no payout share is kept. Balances stay. |
| `treasury.maxPayoutShare` | number (percent) | `25` | Highest payout share a company may set. From 0 to 100. Lowering it applies to companies with a higher share at once. |
| `treasury.minTransaction` | number | `1.0` | Smallest deposit or withdrawal. At least 0.01. |
| `treasury.payRename` | true/false | `true` | Let owners pay a rename from the treasury. |
| `treasury.logRetentionDays` | number (days) | `365` | How long treasury log entries are kept. `0` keeps them forever. Cleaned up by the primary server. |
| `treasury.logPageSize` | number | `10` | Log entries per page. From 1 to 50. |

See [Treasury](../../companies/treasury.md) for a guide.

## alerts

| Key | Type | Default | Meaning |
|---|---|---|---|
| `alerts.enabled` | true/false | `true` | Turn order alerts on. With `false`, no company can have alerts. |
| `alerts.maxPerCompany` | number | `3` | Alerts per company. `0` means none. A level can set its own with `maxAlerts`. |
| `alerts.minMinutesBetween` | number (minutes) | `15` | Shortest time between two notifications of one alert. Matches in between are collected into one summary. |

See [Order alerts](../../companies/order-alerts.md) for a guide.
