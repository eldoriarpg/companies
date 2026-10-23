---
title: Levels
sidebar_position: 2
---

# Levels

Every company has a level. A company climbs the levels by finishing orders, earning money, delivering items and
growing. Each level sets what a company may do: how many members it can have, how many orders it can work on at once,
and more.

Levels live in `company.yml` under `level`. You can edit them in the file or with the level editor in game.

## How a company gets its level

Each level has four requirements:

| Requirement | Key | Meaning |
|---|---|---|
| Orders | `orderCount` | Completed orders, minus penalty points |
| Earned money | `earnedMoney` | Money earned with completed orders |
| Delivered items | `deliveredItems` | Items delivered to completed orders |
| Members | `memberCount` | Current members |

A company gets the highest level whose requirements it meets, as long as it also meets every level below it. Level 1
is the start: every company has at least level 1, even if it does not meet its requirements.

Levels go both ways. A company that loses members or collects penalties can drop a level. Members get a notification
when the company goes up or down.

### Penalties

Orders a company fails count against its order count:

- An order the company gives back with `/company order abort` adds `abortedOrderPenalty` points (default `1`).
- An order the company does not deliver in time adds `expiredOrderPenalty` points (default `3`).

Both keys are in `company.yml`. The points are taken off the completed orders when the level is worked out, so a
company with 10 completed orders and 3 penalty points counts as 7.

### When levels are checked

The plugin checks the level of a company when something changes it:

- an order of the company is delivered,
- a member joins, leaves or is kicked,
- the company gives an order back or misses a deadline.

Changing the levels does **not** check every company again. Run `/companyadmin calclevel` after you changed the
requirements, so all companies move to the level that fits them now.

## What a level allows

| Setting | Key | Default | Meaning |
|---|---|---|---|
| Members | `maxMembers` | `20` | Most members the company can have |
| Orders | `maxOrders` | `5` | Most orders the company can work on at the same time |
| Order size | `maxOrderSize` | `0` | Largest order (in items) the company can claim. `0` means no limit |
| Delivery bonus | `deliveryHoursBonus` | `0` | Extra hours to deliver a claimed order, on top of `deliveryHours` |
| Tax | `taxPercent` | not set | Tax on worker payouts for this level. Not set means the global `tax.percent` applies |
| Alerts | `maxAlerts` | not set | Order alerts the company can save. Not set means the global `alerts.maxPerCompany` applies |

The defaults in this table are what a new level gets. The starter levels below set their own values.

See [Economy](./economy.md) for how the tax works, and [Order alerts](../companies/order-alerts.md) for alerts.

## Starter levels

A new `company.yml` comes with four levels:

| Level | Name | Orders | Earned money | Delivered items | Members | Max members | Max orders |
|---|---|---|---|---|---|---|---|
| 1 | Startup | 0 | 0 | 0 | 1 | 5 | 2 |
| 2 | Business | 10 | 20000 | 1000 | 2 | 10 | 4 |
| 3 | Corporation | 50 | 100000 | 10000 | 4 | 20 | 7 |
| 4 | Conglomerate | 200 | 500000 | 50000 | 8 | 40 | 12 |

None of them sets an order size limit, a delivery bonus, a tax or an alert limit.

An empty list (`level: []`) turns levels off. Companies then keep the level they have and nothing changes it.

## The level list in company.yml

This is the first starter level as it looks in the file:

```yaml
level:
  - levelName: Startup
    level: 1
    requirement:
      orderCount: 0
      earnedMoney: 0.0
      deliveredItems: 0
      memberCount: 1
    settings:
      maxMembers: 5
      maxOrders: 2
      maxOrderSize: 0
      deliveryHoursBonus: 0
```

Add `taxPercent: 5.0` or `maxAlerts: 2` under `settings` to override the global values for that level.

The order of the list decides the levels. The first entry is level 1, the second level 2, and so on. When Companies
loads the file, at start and on `/companyadmin reload`, it numbers the entries 1, 2, 3 in list order. If a `level`
number was different, it logs a warning and saves the file with the new numbers. So you can move or remove entries by
hand without fixing the numbers.

After editing the file, run `/companyadmin reload`, then `/companyadmin calclevel`.

## The level editor

`/companyadmin level` opens the list of all levels. Click a level to open it, or use the commands below. You need
`companies.admin.level`.

| Command | What it does |
|---|---|
| `/companyadmin level` or `level list` | Shows all levels |
| `/companyadmin level info <level>` | Opens one level |
| `/companyadmin level create [position]` | Adds a new level at the end, or at the given position |
| `/companyadmin level edit <level> <field> <value>` | Changes one value of a level |
| `/companyadmin level move <level> <position>` | Moves a level to another position |
| `/companyadmin level remove <level>` | Removes a level |

The fields for `edit` are `name`, `order_count`, `member_count`, `earned_money`, `delivered_items`, `max_members`,
`max_orders`, `max_order_size`, `delivery_hours_bonus`, `tax_percent` and `max_alerts`. For `tax_percent`, enter a
number from 0 to 100, or `none` to use the global tax again. For `max_alerts`, enter a whole number, or `none` to use
the global `alerts.maxPerCompany` again. `0` turns alerts off for the level.

```bash
/companyadmin level create 2
/companyadmin level edit 2 name Workshop
/companyadmin level edit 2 order_count 5
/companyadmin level edit 2 tax_percent 2.5
/companyadmin level edit 2 max_alerts 3
```

In the dialog menu, a level opens as one form. Change what you like and press **Save**. If one value is wrong, the
form shows which one and nothing is saved. Removing a level from the menu asks you to confirm first. The
`remove` command removes it at once.

A new level starts with the name `none`, the requirements 1 order, 1000 money, 1000 items and 1 member, and room for
20 members and 5 orders. Change these before you run `/companyadmin calclevel`.

Every change is saved to `company.yml` straight away and written to the [admin log](./admin-commands.md#the-admin-log).

:::info Several servers
The level editor only works on the primary server. Secondary servers get the levels from it. See
[Several servers](./multiserver.md) for how to pass the change on.
:::

For all `company.yml` settings, see [company.yml](./configuration/company-yml.md).
