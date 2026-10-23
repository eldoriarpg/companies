---
title: Companies
sidebar_position: 1
---

# Companies

Players place orders for items. Companies of players claim and deliver them.

Companies turns gathering and crafting into a player-run economy. A builder who needs 2000 stone bricks puts up an
order and pays for it up front. A company takes the order, its members deliver the items, and every worker is paid
for what they brought. Companies grow, level up and compete in rankings.

New here? Start with [Installation](./getting-started/installation.md) and the short tour in
[First steps](./getting-started/first-steps.md).

## Features

### Orders

Any player can order items. Build the order in a menu, set a price for each item, and pay. Orders can ask for any
item of a material, or for an exact item with its enchantments and other details. The money is held until the order
is delivered, and the player gets it back if nobody takes the order.

Orders are anonymous. Companies see what is ordered, not who ordered it.

[Placing orders](./players/orders.md) · [Repeating orders](./players/repeating-orders.md)

### Companies

Players found companies, invite members or let them apply, and give each member the permissions they need. A company
has a profile with a description, a tag and a colour.

[Founding and joining](./companies/founding-and-joining.md) · [Profile](./companies/profile.md) ·
[Members and permissions](./companies/members-and-permissions.md)

### Claiming and delivering

Companies search for orders, claim the ones they want and deliver the items together. Each member delivers from their
own inventory and is paid for their part.

[Claiming and delivering](./companies/claiming-and-delivering.md)

### Treasury

Every company has a treasury. Members pay into it, and the company can keep a share of every payout.

[Treasury](./companies/treasury.md)

### Order alerts

Companies save a search as an alert and hear about matching orders as soon as they are placed.

[Order alerts](./companies/order-alerts.md)

### Levels, rankings and history

Companies level up as they finish orders, earn money and grow. Higher levels allow more members and more orders at
once. Rankings compare companies, and the history shows finished orders.

[Rankings and history](./companies/rankings-and-history.md) · [Levels](./admin/levels.md)

### Inbox

Players get notifications about their orders and their company, even when they were offline or on another server.

[Inbox](./players/inbox.md)

### Order boards

Put an order board on a sign or an NPC. Players right-click it and see matching orders.

[Using order boards](./players/order-boards.md) · [Setting up boards](./admin/boards.md)

### Menus

Menus show as dialog windows or as clickable chat messages. You choose in `config.yml`.

### For server owners

- [Economy](./admin/economy.md): prices, fees, tax and where the money goes.
- [Discord webhook](./admin/discord-webhook.md): post new and completed orders to a Discord channel.
- [Several servers](./admin/multiserver.md): run Companies on a network with one shared database.
- [Admin commands](./admin/admin-commands.md): look into orders, settle disputes and help players.
- [Configuration](./admin/configuration/index.md): every setting.
- [Upgrading from 1.x](./admin/upgrading.md): what changed in 2.0.

### For developers

Companies has an API to read companies and orders, place orders and react to events.

[API](./developers/api.md) · [Events](./developers/events.md)

## Requirements

- [Paper](https://papermc.io/) 1.21.11 or newer. Spigot does not work.
- Java 25.
- [Vault](https://github.com/MilkBowl/Vault) and an economy plugin that works with Vault.
- A database. SQLite is built in and needs no setup. For larger servers or a network, use MariaDB 10.5 or newer
  (MySQL does not work) or PostgreSQL. See [Installation](./getting-started/installation.md).

## Optional plugins

| Plugin | What it adds |
|---|---|
| [PlaceholderAPI](https://www.spigotmc.org/resources/6245/) | Placeholders for scoreboards, tab lists and more. See [Placeholders](./reference/placeholders.md) |
| [ProtocolLib](https://www.spigotmc.org/resources/1997/) | Only matters for chat menus: while a chat menu is open, system messages are held back too, not just player chat |
| [Citizens](https://citizensnpcs.co) or [FancyNpcs](https://modrinth.com/plugin/fancynpcs) | NPCs that open order boards. See [Boards](./admin/boards.md) |

Dialog menus need a Minecraft 1.21.6 client or newer. Players on older clients, for example through ViaVersion, get
chat menus.
