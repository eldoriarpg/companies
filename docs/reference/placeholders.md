---
title: Placeholders
sidebar_position: 3
---

# Placeholders

Every PlaceholderAPI placeholder Companies offers.

## Setup

The placeholders work when [PlaceholderAPI](https://www.spigotmc.org/resources/6245/) is installed and `generalSettings.hooks.placeholderapi` is `true` in [config.yml](../admin/configuration/config-yml.md). It is `true` by default.

Every placeholder has the form `%companies_<id>%`, for example `%companies_company_name%`.

- Ids ignore case. `%companies_COMPANY_NAME%` works too.
- An unknown id is left as it is. PlaceholderAPI shows the text unchanged.
- The expansion stays registered after `/papi reload`.

## Player placeholders

These show values of the player who looks at the placeholder.

| Placeholder | Value | Example |
|---|---|---|
| `%companies_player_open_orders%` | Own orders that are not delivered yet. | `2` |
| `%companies_player_order_limit%` | The most orders the player may have at once. For online players, this respects `companies.orders.limit.<n>`. For offline players, it is `maxOrders` from [user.yml](../admin/configuration/user-yml.md). | `7` |
| `%companies_player_items_waiting%` | Items waiting for the player: items of delivered own orders and items that came back from orders. | `128` |
| `%companies_player_role%` | `Owner` or `Member` in the server language. Empty without a company. | `Owner` |
| `%companies_player_solo_active%` | Orders the player works on alone right now. See [Working alone](../players/working-alone.md). | `1` |
| `%companies_player_solo_completed%` | Orders the player delivered alone, all time. | `12` |

## Company placeholders

These show values of the company of the player who looks at the placeholder. Without a company, numbers read `0` and texts read empty.

| Placeholder | Value | Example | Without company |
|---|---|---|---|
| `%companies_company_name%` | Name of the company. | `Iron Works` | empty |
| `%companies_company_tag%` | Tag of the company. | `IRON` | empty |
| `%companies_company_tag_colored%` | Tag in the company colour, as legacy colour codes. Without a colour, the plain tag. | `§6IRON` | empty |
| `%companies_company_color%` | Colour of the company as stored. | `gold` or `#4a90d9` | empty |
| `%companies_company_owner%` | Name of the owner. | `Steve` | empty |
| `%companies_company_size%` | Number of members. | `4` | `0` |
| `%companies_company_level%` | Level number. | `2` | `0` |
| `%companies_company_level_name%` | Level name from [company.yml](../admin/configuration/company-yml.md). Empty if that level no longer exists. | `Business` | empty |
| `%companies_active_orders%` | Orders the company works on now. | `3` | `0` |
| `%companies_company_orders%` | Completed orders, minus the penalties for orders that expired or were given back. | `41` | `0` |
| `%companies_company_delivered%` | Items delivered to completed orders, all time. | `15230` | `0` |
| `%companies_company_earned%` | Money earned with completed orders, all time, formatted by your economy plugin. | `$25,400.00` | empty |
| `%companies_company_earned_raw%` | The same as a plain number with two decimals. | `25400.00` | empty |
| `%companies_company_treasury%` | Balance of the treasury, formatted by your economy plugin. | `$1,250.00` | empty |
| `%companies_company_payout_share%` | Percentage of each payout kept in the treasury. Capped at `treasury.maxPayoutShare`. | `10` | `0` |
| `%companies_company_rank_<order>[_<range>]%` | Place of the company in a ranking. Empty when the company is not ranked. | `3` | empty |

Coloured tags use `§` colour codes. A hex colour is written as `§x§r§r§g§g§b§b`.

:::info
The penalties come from `expiredOrderPenalty` and `abortedOrderPenalty` in [company.yml](../admin/configuration/company-yml.md). That is why `company_orders` can be lower than the number of orders the company finished.
:::

## Ranking placeholders

These show any place of a ranking, the same rankings as `/company top`.

| Placeholder | Value | Example |
|---|---|---|
| `%companies_top_<order>[_<range>]_<place>_<field>%` | A field of the company at a place of a ranking. Empty when nobody holds that place. | `%companies_top_earned_30d_1_name%` reads `Iron Works` |

### Arguments

| Argument | Allowed values |
|---|---|
| `<order>` | `orders`, `earned`, `member`, `items_delivered`, `level` |
| `<range>` | `all`, `30d`, `7d`. When you leave it out, `all` is used. |
| `<place>` | A number from 1 up to `placeholders.leaderboardSize` (10 by default). Higher places read empty. |
| `<field>` | `name`, `tag`, `tag_colored`, `value` |

`value` is the number the ranking is sorted by:

| `<order>` | `value` shows | Example |
|---|---|---|
| `orders` | Completed orders | `41` |
| `earned` | Money earned, formatted | `$25,400.00` |
| `member` | Number of members | `12` |
| `items_delivered` | Items delivered | `15230` |
| `level` | Level number | `3` |

More examples:

- `%companies_top_orders_1_name%`: name of the company with the most completed orders.
- `%companies_top_items_delivered_7d_3_value%`: items the third company delivered in the last 7 days.
- `%companies_company_rank_earned_30d%`: place of your company by money earned in the last 30 days.

## Loading and refresh

Placeholders never wait for the database. They read values that Companies keeps in memory.

- The first time a value is needed, it reads `placeholders.loadingText` until it is loaded. This text is empty by default.
- All values are loaded again every `placeholders.refreshSeconds` (300 by default).
- Player and company values are also loaded again at once when something changes on the same server, for example when a player joins a company or a company claims an order.
- Rankings change only with the timer.
- Without a player, for example in a console or a hologram without a viewer, player placeholders read empty. Company placeholders read `0` or empty.

You set all of this in the `placeholders` section of [config.yml](../admin/configuration/config-yml.md#placeholders). The settings apply on `/companyadmin reload`, except `offlineCacheSize`, which needs a restart.
