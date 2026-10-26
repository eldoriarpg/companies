---
title: system-orders.yml
sidebar_position: 5
---

# system-orders.yml

Orders the server places itself. Each rule orders one item again and again, at a price and with money from a source
you choose. Companies deliver them like any order. The items are removed when the order is complete.

In a network, this file is shared. Edit it on the primary server. See [Configuration](./index.md#in-a-network). Only
the primary server places and removes system orders.

System orders are off by default.

## Default file

```yaml
enabled: false
checkMinutes: 5
mintedDailyCap: 10000.0
rules: []
```

## Keys

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `enabled` | true/false | `false` | Whether the server places system orders. When off, open system orders still run out at the end of their lifetime. | reload |
| `checkMinutes` | number (minutes) | `5` | How often the primary server places due orders and removes the ones past their lifetime. At least 1. | restart |
| `mintedDailyCap` | number | `10000.0` | How much new money all rules together may put into orders per day. `0` allows no new money. | reload |
| `rules` | list | empty | The rules, see below. | reload |

A day is a day of the server's calendar, in the time zone of `generalSettings.timezone` in
[config.yml](./config-yml.md#generalsettings).

## Rules

```yaml
rules:
  - id: iron-weekly
    name: "Royal smithy"
    material: IRON_INGOT
    amount: 256
    price:
      mode: AVERAGE
      factor: 1.1
      jitter: 0.05
      min: 2.0
      max: 6.0
    everyHours: 24
    lifetimeHours: 12
    maxOpen: 1
    source:
      type: ACCOUNT
      account: "Collector"
      dailyBudget: 5000
```

| Key | Type | Default | Meaning |
|---|---|---|---|
| `id` | text | none | Names the rule. Each order remembers it. Use each id once, at most 64 characters. A rule without an id, or with the id of an earlier rule, places nothing. |
| `name` | text | the id | The name of the orders. |
| `enabled` | true/false | `true` | Whether the rule places orders. |
| `material` | material | none | The item ordered, for example `IRON_INGOT`. |
| `amount` | number | `64` | How many items one order asks for. |
| `price.mode` | `FIXED` or `AVERAGE` | `AVERAGE` | `FIXED` pays `price.value` per item. `AVERAGE` pays the market average per item times `price.factor`. |
| `price.value` | number | `1.0` | Price per item for `FIXED`. |
| `price.factor` | number | `1.0` | Factor on the market average for `AVERAGE`. |
| `price.jitter` | number | `0.0` | How far the price moves at random either way. `0.05` is up to 5 % less or more. At most `0.9`. |
| `price.min` | number | `0.0` | Lowest price per item. |
| `price.max` | number | `0.0` | Highest price per item. `0` means no highest price. |
| `everyHours` | number (hours) | `24` | How long after one order the next one is placed. |
| `lifetimeHours` | number (hours) | `12` | How long an order waits for a company, counted from when it was placed, last extended with `/companyadmin order extend`, or last given up by a company. After that it is removed and its money goes back to the source. `maxUnclaimedHours` of [order.yml](./order-yml.md) still applies and removes it earlier if it is shorter. |
| `maxOpen` | number | `1` | How many orders of the rule may be open or in work at once. |
| `source.type` | `ACCOUNT` or `MINTED` | `MINTED` | Where the money comes from, see below. |
| `source.account` | text | none | The economy account for `ACCOUNT`: a player's UUID, or an account name your economy plugin knows. |
| `source.dailyBudget` | number | `0.0` | How much the rule may put into orders per day. `0` means no budget of its own. |

A rule places its next order when `everyHours` passed since its last one and it has fewer than `maxOpen` orders open
or in work. The order is placed only when its price fits the budget of the day, and:

- with `ACCOUNT`, the account pays the price at once. If a plugin listening to new orders changes the price, the account
  pays the difference or gets it back. A raise the account cannot pay, or that goes beyond the rule's budget,
  removes the order again. When the order is removed, cancelled by an admin or delivered
  with items missing, that money goes back to the same account. An account that cannot pay places nothing.
- with `MINTED`, nothing is paid when the order is placed. The workers are paid with new money. All minted rules
  together stay within `mintedDailyCap`. A price raised by a plugin beyond the budget or the cap removes the
  order again. Minted money that is not spent goes nowhere.

With `AVERAGE`, a material that nobody delivered lately has no market average, and the rule waits until it has one.

No fee is charged on system orders. Players see them like any order, with the rule's name as the order name.
Deliveries count for company levels and rankings like any order. They are left out of the market prices, the market
list of the dashboard and its price history, so the server's own orders never move the averages they are priced by.

When a rule places nothing, the server log says why, once until the reason changes. A rule without a usable id is
named by its position in the list, for example `#3`.

## See why an order exists

Each system order keeps the numbers it was placed on: the rule, the price and how it was found, the jitter drawn, the
bounds and where the money came from. See [System orders](../admin-commands.md#system-orders) in the admin commands.
