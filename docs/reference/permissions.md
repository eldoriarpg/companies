---
title: Permissions
sidebar_position: 2
---

# Permissions

Every permission, its default and what it covers.

Companies has two kinds of permissions:

- **Server permissions** like `companies.orders.create`. You give them with your permissions plugin.
- **Company permissions** like `invite`. The owner of a company gives them to members, in game.

Commands you may not use are hidden from you. See [Commands](./commands.md) for the permission of each command.

## Player permissions

Every player has these by default. Take one away to block a feature.

| Permission | Default | What it covers |
|---|---|---|
| `companies.orders.create` | everyone | Create orders: `/order create` and every `/order draft` command. |
| `companies.orders.repeat` | everyone | Make orders repeat daily or weekly: `/order draft repeat`, the repeat button of the order builder and `/order series resume`. |
| `companies.orders.solo` | everyone | Take orders alone, without a company: `/order work accept` and the `[Work alone]` button of the order search. Only works while `solo.enabled` is on in [order.yml](../admin/configuration/order-yml.md#solo). |
| `companies.company.create` | everyone | Found a company with `/company create`. |
| `companies.company.join` | everyone | Join a company: `/company join`, `/company apply`, `/company apply withdraw` and `/company invites accept`. An online player without it cannot be invited. |
| `companies.boards.use` | everyone | Open order boards through signs, NPCs and `/company board`. |
| `companies.dashboard.use` | everyone | Open the [web dashboard](../players/dashboard.md) with `/company dashboard` while the server enabled it. Taking it away ends the player's logins. New in 2.1. |

### Order limit

| Permission | Default | What it covers |
|---|---|---|
| `companies.orders.limit.<n>` | not set | The number of active orders a player may have, for example `companies.orders.limit.15`. |

- Without this permission, a player may have `maxOrders` active orders, as set in [user.yml](../admin/configuration/user-yml.md).
- If a player has more than one, the highest number counts.
- The number can be lower than `maxOrders`, so you can also lower the limit for some players.
- The permission is only checked while the player is online. For offline players, `maxOrders` counts.
- It is not raised by `companies.orders.*` or `companies.*`. Give it on its own.

## Admin permissions

Only operators have these by default.

| Permission | Default | What it covers |
|---|---|---|
| `companies.admin` | op | See `/companyadmin`. Use `/companyadmin syncconfiguration` and `/companyadmin settings`. |
| `companies.admin.boards` | op | Create and break board signs, list presets, rewrite board signs and bind NPCs to boards (`/companyadmin board`). |
| `companies.admin.calcLevel` | op | Recalculate company levels with `/companyadmin calclevel`. |
| `companies.admin.dashboard` | op | Show the dashboard connection, enter the dashboard key and log players out of the dashboard with `/companyadmin dashboard`. New in 2.1. |
| `companies.admin.debug` | op | Create debug reports with `/companyadmin debug`. |
| `companies.admin.delete` | op | Delete companies with `/companyadmin delete`. |
| `companies.admin.grant` | op | Give items and money to players with `/companyadmin grant`. |
| `companies.admin.level` | op | Edit the company levels with `/companyadmin level`. |
| `companies.admin.nodes` | op | List and remove the servers registered in the database with `/companyadmin node`. |
| `companies.admin.orders` | op | Look at, release, cancel, complete and extend any order with `/companyadmin order`, and use the buttons of its menus. |
| `companies.admin.player` | op | Look up the company, orders, items and notifications of a player with `/companyadmin player`. |
| `companies.admin.reload` | op | Reload the configuration with `/companyadmin reload`. |
| `companies.admin.rename` | op | Rename companies with `/companyadmin rename`. |
| `companies.admin.transferOwner` | op | Make another member the owner of a company with `/companyadmin transferowner`. |
| `companies.admin.treasury` | op | Look at company treasuries and add or remove money with `/companyadmin treasury`. |
| `companies.updates` | op | Hear about new versions when joining, and download them. |

Every admin permission also makes `/companyadmin` visible. Each subcommand then checks its own permission. The same
permissions unlock the matching tools of the dashboard's [Admin section](../admin/dashboard.md#admin-tools);
`companies.admin` also syncs the configuration from there.

## Wildcard permissions

These give every permission listed below them. On their own, they do nothing.

| Permission | Default | Gives |
|---|---|---|
| `companies.*` | op | `companies.orders.*`, `companies.company.*`, `companies.boards.use`, `companies.dashboard.use`, `companies.admin.*`, `companies.updates` |
| `companies.orders.*` | op | `companies.orders.create`, `companies.orders.repeat`, `companies.orders.solo` |
| `companies.company.*` | op | `companies.company.create`, `companies.company.join` |
| `companies.admin.*` | op | `companies.admin`, `companies.admin.boards`, `companies.admin.calcLevel`, `companies.admin.dashboard`, `companies.admin.debug`, `companies.admin.delete`, `companies.admin.grant`, `companies.admin.level`, `companies.admin.nodes`, `companies.admin.orders`, `companies.admin.player`, `companies.admin.reload`, `companies.admin.rename`, `companies.admin.transferOwner`, `companies.admin.treasury` |

:::note
The player permissions have the default "everyone". So players have them even without `companies.*`. To block a feature, set the permission to `false` for that player or group.
:::

## Company permissions

Company permissions are given inside a company. They are not server permissions.

- The founder becomes the owner. The owner has every company permission.
- New members start with no company permission.
- A member with `manage_permissions` gives and removes permissions with `/company permission <member> give|remove <permission>`, or in the member menu.

| Permission | What it allows |
|---|---|
| `invite` | Invite players, take invites back, see sent invites and answer applications. Members with it are told about new applications. |
| `kick` | Kick members who do not have `kick` themselves. |
| `manage_orders` | Claim orders, give claimed orders back, and add, remove and switch order alerts. Members with it see the `[Accept]` buttons and get alert notifications. |
| `manage_permissions` | Give and remove the permissions of other members. Only permissions you hold yourself, and never those of the owner. |
| `edit_profile` | Change the description, tag, colour and join mode. |
| `treasury` | Take money out of the treasury and set the payout share. |

### The owner

Being the owner works like a company permission of its own, but nobody can give or remove it with `/company permission`. Only the owner can:

- transfer the company to another member (`/company transfer`),
- disband the company (`/company disband`), and gets the treasury when doing so,
- rename the company (`/company rename`).

The owner cannot be kicked and cannot leave. To leave, the owner first transfers or disbands the company. An admin can also make another member the owner with `/companyadmin transferowner`.

Some commands need no company permission, only membership. Examples are delivering items, looking at the treasury, and listing orders and alerts. See [Commands](./commands.md#companies) for each command.
