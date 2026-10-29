---
title: Commands
sidebar_position: 1
---

# Commands

Every command, its arguments and the permission it needs.

## How to read this page

- `<id>` is an argument you must give. `[page]` is an argument you may leave out.
- `a|b` means you type one of the words, for example `open` or `invite`.
- **Permission** is the server permission. You give it with your permissions plugin. See [Permissions](./permissions.md).
- **Company permission** is a permission inside a company. The owner gives it to members with `/company permission`. See [Company permissions](./permissions.md#company-permissions).
  - "member" means you must be in a company, but you need no special company permission.
  - "none" means you do not need to be in a company.
- Commands you may not use are hidden from you. They do not show up in tab completion.

:::info Players only
Every `/order` and `/company` command works for players only. The console cannot use them. For `/companyadmin`, the table says which commands also work from the console.
:::

`/comp` is a short alias of `/company`. There are no other aliases.

Many commands ask before they do something you cannot undo. You answer with the `confirm` command next to them, or with the button in the menu.

## Orders

Commands for players who place orders. Read [Orders](../players/orders.md) and [Repeating orders](../players/repeating-orders.md) for a guide.

| Command | Permission | What it does |
|---|---|---|
| `/order` | none | Same as `/order list`. |
| `/order list` | none | Shows your orders that you have not received yet, your order limit and items waiting for you. |
| `/order info <id>` | none | Shows one of your orders. |
| `/order cancel <id>` | none | Cancels an order nobody claimed yet. You get the price back, but not the fee. Asks you to confirm when the order is a copy of an active repeating order. |
| `/order cancel confirm` | none | Confirms the cancel. |
| `/order receive <id>` | none | Hands out the items of a delivered order. |
| `/order collect` | none | Hands out items that did not fit into your inventory, and items an admin granted you. |
| `/order extend <id>` | none | Restarts the timer of an order nobody claimed yet, so it is not removed. Works up to `maxExtensions` times ([order.yml](../admin/configuration/order-yml.md)). |
| `/order raise <id> <amount>` | none | Raises the price of an order nobody claimed yet by `amount`. You pay the increase plus the percentage fee on it. Asks you to confirm. |
| `/order raise confirm` | none | Confirms the raise. |
| `/order create <name>` | `companies.orders.create` | Starts a new order draft and shows the order builder. If you already have a draft, it shows that one. |
| `/order draft name <name>` | `companies.orders.create` | Renames the draft. |
| `/order draft add <item> <amount> <unitPrice>` | `companies.orders.create` | Adds a line. A plain material makes a material line. An item with components makes an exact line. |
| `/order draft add hand <amount> <unitPrice>` | `companies.orders.create` | Adds the item in your main hand. A plain item makes a material line. An item with components makes an exact line. |
| `/order draft add saved <name> <amount> <unitPrice>` | `companies.orders.create` | Adds a saved item. An item with components makes an exact line, a plain one a material line. |
| `/order draft add criteria <amount> <unitPrice> <criteria>` | `companies.orders.create` | Adds a line any item of a set fills, such as `#logs oak_planks enchants:2 sharpness:3`. See [Criteria lines](../players/orders.md#criteria-lines). |
| `/order draft remove <line>` | `companies.orders.create` | Removes a line. |
| `/order draft amount <line> <amount>` | `companies.orders.create` | Changes the amount of a line. |
| `/order draft price <line> <unitPrice>` | `companies.orders.create` | Changes the price per item of a line. |
| `/order draft price <line> market` | `companies.orders.create` | Uses the market average as the price per item. |
| `/order draft repeat daily\|weekly <count>` | `companies.orders.create` and `companies.orders.repeat` | Repeats the order daily or weekly, `count` times. |
| `/order draft repeat daily\|weekly until <date>` | `companies.orders.create` and `companies.orders.repeat` | Repeats the order until a date, written as `yyyy-MM-dd`. |
| `/order draft repeat off` | `companies.orders.create` | Makes the draft a one-time order again. |
| `/order draft done` | `companies.orders.create` | Places the order and pays for it. |
| `/order draft cancel` | `companies.orders.create` | Throws the draft away. |
| `/order items` | `companies.orders.create` | Lists your saved items. |
| `/order items save [name]` | `companies.orders.create` | Saves the item in your main hand to order it again. You keep the item. |
| `/order items remove <name>` | `companies.orders.create` | Forgets a saved item. |
| `/order history [page]` | none | Shows your orders that have ended. |
| `/order history info <entry>` | none | Shows one ended order. |
| `/order series` | none | Lists your repeating orders. |
| `/order series info <id>` | none | Shows one repeating order. |
| `/order series stop <id>` | none | Stops a repeating order. The current copy stays. Asks you to confirm. |
| `/order series stop confirm` | none | Confirms the stop. |
| `/order series resume <id>` | `companies.orders.repeat` | Starts a stopped repeating order again. |

`/order draft` alone does nothing. Use one of its subcommands, or the buttons of the order builder.

How many orders a player may have is set by `maxOrders` in [user.yml](../admin/configuration/user-yml.md), or by the permission `companies.orders.limit.<n>`.

## Working alone

Commands for players who work on orders alone, without a company. They need `solo.enabled` in
[order.yml](../admin/configuration/order-yml.md#solo). Read [Working alone](../players/working-alone.md) for a guide.

| Command | Permission | What it does |
|---|---|---|
| `/order work` or `/order work list` | none | Lists the orders you work on alone. Opens the search when there are none. |
| `/order work info <id>` | none | Shows an order you work on alone, or an open order you could take. |
| `/order work accept <id>` | `companies.orders.solo` | Takes an order to work on it alone. |
| `/order work deliver <id> <line> <amount>` | none | Delivers up to `amount` items to one line. |
| `/order work deliver <id> <line> max` | none | Delivers as many items as the line still misses. |
| `/order work deliver <id> all` | none | Delivers everything you carry that fits the order. |
| `/order work abort <id>` | none | Gives an order back. Asks you to confirm. Items you delivered come back to you and the cooldown starts. |
| `/order work abort confirm` | none | Confirms giving the order back. |
| `/order work search …` | none | The order search for working alone. It has the same `query …` subcommands as `/company order search`, without alerts. |

## Companies

Commands for company members and owners. Read [Founding and joining](../companies/founding-and-joining.md) and [Members and permissions](../companies/members-and-permissions.md) for a guide.

### Profile

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company` | none | none | Shows the profile of your company. |
| `/company profile` or `/company profile self` | none | member | Shows the profile of your company with its level progress. |
| `/company profile id <id>` | none | none | Shows the profile of a company by its id. |
| `/company profile name <name>` | none | none | Shows the profile of a company by its name. |
| `/company profile player <player>` | none | none | Shows the profile of the company of an online player. |
| `/company profile edit` | none | `edit_profile` | Opens the profile editor. |
| `/company profile edit description [text]` | none | `edit_profile` | Sets the description. Leave out the text to clear it. |
| `/company profile edit tag <tag>` | none | `edit_profile` | Sets the tag. |
| `/company profile edit tag clear` | none | `edit_profile` | Clears the tag. |
| `/company profile edit color <color>` | none | `edit_profile` | Sets the colour. Tab completion suggests the allowed colours. |
| `/company profile edit color clear` | none | `edit_profile` | Clears the colour. |

### Founding and joining

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company create <name>` | `companies.company.create` | none | Founds a company. Asks you to confirm and shows the price. |
| `/company create confirm` | `companies.company.create` | none | Confirms the founding and pays the price. |
| `/company create deny` | `companies.company.create` | none | Does not found the company. |
| `/company invite <player>` | none | `invite` | Invites a player. The player may be offline. An online player needs `companies.company.join` to be invited. |
| `/company invite revoke <player>` | none | `invite` | Takes an invite back. |
| `/company invites` | none | none | Shows invites you got and your applications. With `invite`, it also shows the invites your company sent. |
| `/company invites accept <company>` | `companies.company.join` | none | Accepts an invite. |
| `/company invites deny <company>` | none | none | Turns down an invite. |
| `/company join <company>` | `companies.company.join` | none | Joins a company with the join mode `open`. |
| `/company apply <company> <message>` | `companies.company.join` | none | Applies to a company with the join mode `applications`. Put the company name in quotes if it has spaces. |
| `/company apply withdraw <company>` | `companies.company.join` | none | Takes your application back. |
| `/company applications` | none | `invite` | Shows the open applications to your company. |
| `/company applications accept\|deny <player>` | none | `invite` | Accepts or turns down an application. |
| `/company joinmode` | none | member | Shows the join mode of your company. |
| `/company joinmode open\|applications\|invite` | none | `edit_profile` | Changes the join mode. |
| `/company leave` | none | member, not the owner | Leaves your company. Asks you to confirm. The owner must transfer or disband the company instead. |
| `/company leave confirm` | none | member, not the owner | Confirms leaving. |

### Members

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company member` or `/company member self` | none | member | Lists the members with their permissions and what they contributed. |
| `/company member name\|items\|earned\|orders` | none | member | Lists the members, sorted. |
| `/company member name\|items\|earned\|orders all\|30d` | none | member | Lists the members, sorted, for all time or the last 30 days. |
| `/company member id <id>` | none | none | Lists the members of another company, without statistics. |
| `/company permission <member>` | none | `manage_permissions` | Opens the permission editor for a member. |
| `/company permission <member> give\|remove <permission>` | none | `manage_permissions` | Gives or removes a company permission. You can only change permissions you hold yourself. The owner cannot be changed. |
| `/company kick <member>` | none | `kick` | Kicks a member. Asks you to confirm. You can only kick members without `kick`. The owner can kick anyone and cannot be kicked. |
| `/company kick confirm` | none | `kick` | Confirms the kick. |
| `/company transfer <member>` | none | owner | Hands the company to another member. Asks you to confirm. |
| `/company transfer confirm` | none | owner | Confirms the transfer. |
| `/company disband` | none | owner | Dissolves the company. Asks you to confirm. The treasury is paid out to the owner. |
| `/company disband confirm` | none | owner | Confirms the disband. |
| `/company rename <name>` | none | owner | Renames the company for a price. Asks you to confirm. |
| `/company rename confirm` | none | owner | Confirms and pays the price from your own money. |
| `/company rename confirm treasury` | none | owner | Confirms and pays the price from the treasury. Needs `treasury.payRename` and an enabled treasury ([company.yml](../admin/configuration/company-yml.md)). |

`<permission>` is one of `invite`, `kick`, `manage_orders`, `manage_permissions`, `edit_profile` and `treasury`.

### Treasury

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company bank` | none | member | Opens the treasury menu. Members with `treasury` also see its actions. |
| `/company bank deposit <amount>` | none | member | Pays money into the treasury. |
| `/company bank withdraw <amount>` | none | `treasury` | Takes money out of the treasury. |
| `/company bank share <percent>` | none | `treasury` | Sets the share of each payout that goes to the treasury, from 0 up to `treasury.maxPayoutShare`. |
| `/company bank log [page]` | none | member | Shows the treasury log. |

See [Treasury](../companies/treasury.md) for a guide.

### Order alerts

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company alerts` or `/company alerts list` | none | member | Lists the alerts of your company. |
| `/company alerts add <name>` | none | `manage_orders` | Saves your current order search as an alert. |
| `/company alerts remove <name>` | none | `manage_orders` | Removes an alert. |
| `/company alerts on\|off <name>` | none | `manage_orders` | Turns an alert on or off. |
| `/company alerts show <name>` | none | member | Searches the orders that match an alert. |

See [Order alerts](../companies/order-alerts.md) for a guide.

### Inbox, history and rankings

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company inbox [page]` | none | none | Shows your notifications. |
| `/company inbox read` | none | none | Marks all notifications as read. |
| `/company inbox clear` | none | none | Deletes all read notifications. |
| `/company inbox mute\|unmute <category>` | none | none | Stops or starts showing a category live. The notifications still go to your inbox. |
| `/company inbox settings` | none | none | Shows a toggle for each category you can mute. |
| `/company history [page]` | none | member | Shows the ended orders of your company. |
| `/company history info <entry>` | none | member | Shows one ended order of your company. |
| `/company top [page] [ordering] [range]` | none | none | Shows the ranking of all companies. |
| `/company returns` | none | none | Hands out items you delivered that came back to you, for example from an order your company gave back or that expired. |

- `<category>` is one of `members`, `orders`, `payments`, `level` and `alerts`.
- `[ordering]` is one of `orders`, `earned`, `member`, `items_delivered` and `level`. The default is `orders`.
- `[range]` is one of `all`, `30d` and `7d`. The default is `all`.
- The arguments of `/company top` go in this order. To give a range, you also give a page and an ordering.

See [Inbox](../players/inbox.md) and [Rankings and history](../companies/rankings-and-history.md) for a guide.

### Order boards

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company board [board]` | `companies.boards.use` | none | Opens an order board. `board` is a preset name or a list of materials. Without it, the board shows all open orders. |

See [Order boards](../players/order-boards.md) for a guide.

### Event offers

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company events` | none | none | Lists the event offers, goals and contests, that run, are paused or start within a day: progress, time left and, for a contest, your company's place. |
| `/company events open <id>` | none | none | Opens an offer: the order search with only the chunks of a goal (the search of orders to work on alone when you have no company and may work alone), or the info of a contest. The `[Open]` button of the list runs it. |

### Contests

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company contest` | none | none | Lists the contests that have not closed, with what they take, their progress and time left. |
| `/company contest info <id>` | none | none | Shows what a contest still misses, its price per item, its prizes, your company's part and place, and the ranking. |
| `/company contest join <id>` | none | `manage_orders` | Lets your company take part, once the contest started. It uses one of the company's order slots. |
| `/company contest leave <id>` | none | `manage_orders` | Lets your company stop taking part. It keeps its items and place, and the slot is free again. |
| `/company contest deliver <id> <amount>` | none | member | Delivers up to `amount` matching items; you are paid at once. |
| `/company contest deliver <id> all` | none | member | Delivers every matching item you carry, up to what the contest misses. |

See [Event offers](../players/event-offers.md#contests) for a guide.

### Web dashboard

New in 2.1. Works while the server owner enabled the [web dashboard](../admin/dashboard.md).

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company dashboard` | `companies.dashboard.use` | none | Shows a short link and a six-letter code to log in to the dashboard on any device. |
| `/company dashboard sessions` | `companies.dashboard.use` | none | Lists the browsers you are logged in on. |
| `/company dashboard logout` | `companies.dashboard.use` | none | Logs you out on every browser. |

See [Dashboard](../players/dashboard.md) for a guide.

## Company orders

Commands to find, claim and deliver orders. Read [Claiming and delivering](../companies/claiming-and-delivering.md) for a guide.

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company order` or `/company order list` | none | member | Lists the orders your company works on. |
| `/company order info <id>` | none | member | Shows an order as your company sees it. |
| `/company order accept <id>` | none | `manage_orders` | Claims an order for your company. Your company level limits how many and how large. |
| `/company order abort <id>` | none | `manage_orders` | Gives a claimed order back. Asks you to confirm. Items already delivered go back to the workers. |
| `/company order abort confirm` | none | `manage_orders` | Confirms giving the order back. |
| `/company order deliver <id> <line> <amount>` | none | member | Delivers up to `amount` items to one line. |
| `/company order deliver <id> <line> max` | none | member | Delivers as many items as the line still misses. |
| `/company order deliver <id> all` | none | member | Delivers everything you carry that fits the order. |

### Order search

| Command | Permission | Company permission | What it does |
|---|---|---|---|
| `/company order search` or `/company order search query` | none | none | Shows the search form. |
| `/company order search query clear` | none | none | Resets the search. |
| `/company order search query execute` | none | none | Runs the search and shows the first page of results. |
| `/company order search query fillable true\|false` | none | none | Shows only orders you can deliver to. |
| `/company order search query server all\|only\|hide` | none | none | Shows every order, only the orders the server placed, or only orders of players. Works while the server marks its orders (`markSystemOrders` in [system-orders.yml](../admin/configuration/system-orders-yml.md)). |
| `/company order search query offers all\|only` | none | none | Shows every order, or only the chunks of [event offers](../players/event-offers.md). |
| `/company order search query offer <id>` | none | none | Shows only the chunks of one event offer; `0` shows every order again. |
| `/company order search query material_add <material>` | none | none | Adds a material to the search. |
| `/company order search query material_remove [material]` | none | none | Removes a material. Without a material, it removes all. |
| `/company order search query material_match exact\|part` | none | none | Matches whole material names or parts of them. |
| `/company order search query material_search any\|all` | none | none | Finds orders with any of the materials, or with all of them. |
| `/company order search query name [name]` | none | none | Filters by order name. Without a name, it clears the filter. |
| `/company order search query order asc\|desc` | none | none | Sorts up or down. |
| `/company order search query min_price\|max_price <price>` | none | none | Sets the lowest or highest price. |
| `/company order search query min_size\|max_size <size>` | none | none | Sets the lowest or highest number of items. |
| `/company order search query sorting age\|name\|price\|size\|unit_price\|offer_end` | none | none | Sets what the results are sorted by. `offer_end` puts the chunks of the event offers that end soonest first, the other orders after them. |

You move between result pages with the buttons of the result list. There is no command for it. The results show an `[Accept]` button only to members with `manage_orders`.

## Administration

Commands for server owners and staff. Read [Admin commands](../admin/admin-commands.md) for a guide.

You see `/companyadmin` if you have any of the `companies.admin` permissions. Each subcommand then checks its own permission. `/companyadmin` alone does nothing.

| Command | Permission | Console | What it does |
|---|---|---|---|
| `/companyadmin debug` | `companies.admin.debug` | yes | Creates a debug report. The Discord webhook URL is masked in it. |
| `/companyadmin reload` | `companies.admin.reload` | yes | Reloads the configuration files and applies them, including the update settings and the Discord webhook. See [Configuration](../admin/configuration/index.md) for what needs a restart. In a network it names the shared settings changed on this server. |
| `/companyadmin reload confirm` | `companies.admin.reload` | yes | Shares the settings the last `reload` named with every server, as the next revision. |
| `/companyadmin syncconfiguration` | `companies.admin` | yes | Primary server: stores each changed shared file as a new revision. Secondary server: takes the latest revisions and applies them like a reload. |
| `/companyadmin setup` | `companies.admin` | yes | Shows the setup of this server: database, network, role and name, dashboard, and what to change. A player gets a dialog to name the server and switch the dashboard on, and, with `companies.admin.dashboard`, to enter its key; the console gets the commands. Only what you change in the dialog is applied. |
| `/companyadmin setup name <name>` | `companies.admin` and `companies.admin.reload` | yes | Names this server, at most 32 characters. The dashboard shows the name after it reconnects. |
| `/companyadmin setup dashboard <on\|off>` | `companies.admin` and `companies.admin.reload` | yes | Switches the dashboard on or off for every server, as the next revision of `config.yml`. |
| `/companyadmin setup dismiss` | `companies.admin` and `companies.admin.reload` | yes | Hides the reminder to finish the setup, at start and on join, for every admin. |
| `/companyadmin settings status` | `companies.admin` | yes | Lists each shared file with its latest revision, the revision this server uses, whether it was changed here without being shared, and the servers still on an older revision. Names the changed settings this server applies only after a restart. |
| `/companyadmin settings history <file> [page]` | `companies.admin` | yes | Lists the revisions of a shared file, newest first, with who stored them, on which server and when. 10 per page. |
| `/companyadmin settings rollback <file> <revision>` | `companies.admin` and `companies.admin.reload` | yes | Stores the content of an earlier revision as the next revision, so every server takes it. Asks you to confirm. |
| `/companyadmin settings rollback confirm` | `companies.admin` and `companies.admin.reload` | yes | Confirms the rollback. Refused when the file got a newer revision since you asked. |
| `/companyadmin calclevel` | `companies.admin.calcLevel` | yes | Recalculates the level of every company. |
| `/companyadmin delete <name>` | `companies.admin.delete` | no | Dissolves a company. Shows its members, the orders it works on and its treasury, and asks you to confirm. |
| `/companyadmin delete confirm` | `companies.admin.delete` | no | Confirms the delete. |
| `/companyadmin rename <company> <name>` | `companies.admin.rename` | no | Renames a company. Put the old name in quotes if it has spaces. |
| `/companyadmin transferowner <company> <player>` | `companies.admin.transferOwner` | no | Makes a member the owner of a company. |

### Levels

| Command | Permission | Console | What it does |
|---|---|---|---|
| `/companyadmin level` or `/companyadmin level list` | `companies.admin.level` | no | Lists the levels in the level editor. |
| `/companyadmin level info <level>` | `companies.admin.level` | no | Shows the editor of one level. |
| `/companyadmin level create [index]` | `companies.admin.level` | no | Adds a level at the end, or at a position. |
| `/companyadmin level edit <level> <field> <value>` | `companies.admin.level` | no | Changes one field of a level. |
| `/companyadmin level move <source> <target>` | `companies.admin.level` | no | Moves a level to another position. |
| `/companyadmin level remove <level>` | `companies.admin.level` | no | Removes a level at once. Only the menu asks you to confirm. |

`<field>` is one of `name`, `order_count`, `member_count`, `earned_money`, `delivered_items`, `max_members`, `max_orders`, `max_order_size`, `delivery_hours_bonus`, `tax_percent` and `max_alerts`. `tax_percent` and `max_alerts` take `none` to use the global value again. The level editor works on the primary server only. See [Levels](../admin/levels.md).

### Orders and players

| Command | Permission | Console | What it does |
|---|---|---|---|
| `/companyadmin order info <id>` | `companies.admin.orders` | no | Shows an order from both sides: owner, company, times, money, deliveries and history. |
| `/companyadmin order search player <player>` | `companies.admin.orders` | no | Lists the orders of a player. |
| `/companyadmin order search company <company>` | `companies.admin.orders` | no | Lists the orders of a company. |
| `/companyadmin order unclaim <id>` | `companies.admin.orders` | no | Releases a claimed order. Asks you to confirm. |
| `/companyadmin order unclaim confirm` | `companies.admin.orders` | no | Confirms the release. |
| `/companyadmin order cancel <id> refund\|keep` | `companies.admin.orders` | no | Deletes an order. `refund` pays price and fee back to the owner. `keep` pays nothing back: price and fee go to the collector account. Asks you to confirm. |
| `/companyadmin order cancel confirm` | `companies.admin.orders` | no | Confirms the cancel. |
| `/companyadmin order deliver <id>` | `companies.admin.orders` | no | Closes an order as delivered. Asks you to confirm. |
| `/companyadmin order deliver confirm` | `companies.admin.orders` | no | Confirms the delivery. |
| `/companyadmin order extend <id> <hours>` | `companies.admin.orders` | no | Gives an order more time to be delivered, or more time before it is removed. `hours` is 1 to 8760. |
| `/companyadmin player <player>` | `companies.admin.player` | no | Shows the company, role, orders, items and notifications of a player. |
| `/companyadmin grant items <player> [amount]` | `companies.admin.grant` | no | Stores the item in your main hand for a player. The player gets it with `/order collect`. `amount` is 1 to 100000. |
| `/companyadmin grant money <player> <amount>` | `companies.admin.grant` | no | Gives money to a player. |

The confirm commands take no id. They confirm the last request you made.

### Event offers

| Command | Permission | Console | What it does |
|---|---|---|---|
| `/companyadmin offers goal material <name> <money> <amount> <price> <chunk> <start> <hours> <material> [budget]` | `companies.admin.offers` | yes | Creates a goal for any item of a material. `<money>` is `minted` or an economy account, `<start>` the hours until it starts, `<hours>` how long it runs (each at most 8760), `[budget]` what it may spend, by default what its chunks cost. |
| `/companyadmin offers goal hand <name> <money> <amount> <price> <chunk> <start> <hours> [budget]` | `companies.admin.offers` | no | Creates a goal for the item in your main hand. |
| `/companyadmin offers goal criteria <name> <money> <amount> <price> <chunk> <start> <hours> [budget <budget>] <criteria>` | `companies.admin.offers` | yes | Creates a goal for every item meeting the criteria. |
| `/companyadmin offers contest material <name> <money> <amount> <price> <prizes> <start> <hours> <material> [budget]` | `companies.admin.offers` | yes | Creates a contest for any item of a material. `<price>` is paid per delivered item, `<prizes>` is `none` or the money of the first places like `500+250+100`, `[budget]` by default the cost of the whole amount plus the prizes. |
| `/companyadmin offers contest hand <name> <money> <amount> <price> <prizes> <start> <hours> [budget]` | `companies.admin.offers` | no | Creates a contest for the item in your main hand. |
| `/companyadmin offers contest criteria <name> <money> <amount> <price> <prizes> <start> <hours> [budget <budget>] <criteria>` | `companies.admin.offers` | yes | Creates a contest for every item meeting the criteria. |
| `/companyadmin offers list` | `companies.admin.offers` | yes | Lists the newest offers with state, progress and end. |
| `/companyadmin offers info <id>` | `companies.admin.offers` | yes | Shows who created an offer, its money and budget, its progress and top contributors, or for a contest its ranking, payouts and prizes. |
| `/companyadmin offers open <id> <count>` | `companies.admin.offers` | yes | Changes how many chunks of a goal wait to be taken at once, while the goal is not closed. |
| `/companyadmin offers pause <id>` | `companies.admin.offers` | yes | Pauses an offer and withdraws its waiting chunks. |
| `/companyadmin offers resume <id>` | `companies.admin.offers` | yes | Resumes a paused offer. |
| `/companyadmin offers cancel <id>` | `companies.admin.offers` | yes | Closes an offer and withdraws its waiting chunks; a cancelled contest pays no prizes. |
| `/companyadmin offers prize <id> <place> paid\|failed` | `companies.admin.offers` | yes | Notes the outcome of a contest prize whose payment stopped without a known result. |

See [Event offers](../admin/admin-commands.md#event-offers).

### Boards

| Command | Permission | Console | What it does |
|---|---|---|---|
| `/companyadmin board presets` | `companies.admin.boards` | yes | Lists the board presets with their number of open orders. |
| `/companyadmin board reload` | `companies.admin.boards` | yes | Rewrites all loaded board signs. |
| `/companyadmin board npc link <hook> <npc> [board]` | `companies.admin.boards` | yes | Binds an NPC to a board. `hook` is `Citizens` or `FancyNpcs`. `board` is a preset name or a list of materials. Without it, the NPC shows all open orders. |
| `/companyadmin board npc unlink <hook> <npc>` | `companies.admin.boards` | yes | Removes the board from an NPC. |
| `/companyadmin board npc list` | `companies.admin.boards` | yes | Lists the NPCs bound to boards. |

See [Boards](../admin/boards.md) for a guide.

### Treasury

| Command | Permission | Console | What it does |
|---|---|---|---|
| `/companyadmin treasury <company>` | `companies.admin.treasury` | no | Shows the balance, payout share and recent transactions of a company. |
| `/companyadmin treasury give <company> <amount>` | `companies.admin.treasury` | no | Adds money to a treasury. |
| `/companyadmin treasury take <company> <amount>` | `companies.admin.treasury` | no | Removes money from a treasury, up to its balance. |

Put the company name in quotes if it has spaces.

### Servers

| Command | Permission | Console | What it does |
|---|---|---|---|
| `/companyadmin node list` | `companies.admin.nodes` | yes | Lists the servers registered in the database with their uid, role, version and when they were last seen, marks the servers whose shared settings are behind, and names the changed settings each server applies only after a restart. |
| `/companyadmin node remove <uid>` | `companies.admin.nodes` | yes | Removes the entry of a server that no longer runs. Asks you to confirm. The server you run it on can not be removed. |
| `/companyadmin node remove confirm` | `companies.admin.nodes` | yes | Confirms the remove. |

These commands also work in the safe mode after the error about two primary servers. See [Several servers](../admin/multiserver.md#old-server-entries).

### Web dashboard

New in 2.1.

| Command | Permission | Console | What it does |
|---|---|---|---|
| `/companyadmin dashboard status` | `companies.admin.dashboard` | yes | Shows whether the dashboard is enabled, the connection, the gateway, the kind of key and when it rotates, notices about old versions and the number of logins. Never shows the key. |
| `/companyadmin dashboard key <key>` | `companies.admin.dashboard` | yes | Stores the Lyna dashboard key, or the token of a self-hosted gateway, for every server of the network. The command line is kept out of the server log. |
| `/companyadmin dashboard revoke <player>` | `companies.admin.dashboard` | yes | Logs a player out of the dashboard on every browser. |

See [Web dashboard](../admin/dashboard.md) for a guide.
