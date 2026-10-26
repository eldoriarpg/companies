---
title: Web dashboard
sidebar_position: 4.5
---

# Web dashboard

:::info New in 2.1
The web dashboard comes with Companies 2.1. It shows orders, the company, the inbox, rankings and market prices.
Players place, accept, deliver and receive orders, manage their own orders, their company and their inbox there;
inviting players still happens in game. Admins get an [Admin section](#admin-tools) with the tools of
`/companyadmin`.
:::

Players open a dashboard in their browser, on a phone or a PC, and see their orders, their company and its orders,
their inbox and the rankings. It updates live while things happen on the server.

You host nothing for it. Each server opens one outgoing connection to a gateway run by Eldoria
(`companies.eldoria.de`). The gateway serves the web page and relays between browsers and your server. Your server
opens no port and needs no domain or certificate. The connection stays open as long as the dashboard is enabled, and
the server connects again by itself when the gateway restarts or the connection drops. You can also
[run the gateway yourself](#self-hosted-gateway).

## Setup

1. Get a dashboard key on the Companies page of [Lyna](https://lyna.eldoria.de), where you got the plugin. Keys start
   with `lyd_`. Creating one shows the command to paste on the server.
2. Give the key to the primary server as the environment variable `COMPANIES_DASHBOARD_KEY`, see
   [the key from the environment](#the-key-from-the-environment). Or enter it on any server of your network, from the
   console or in game:

   ```
   /companyadmin dashboard key lyd_...
   ```

   The key is stored in the database, so every server of the network uses it. The command line with the key is kept
   out of the server log, and debug reports mask keys.

   :::tip Prefer the environment variable
   A key typed into the console stays in the console's history: Paper keeps it in the file `.console_history` of the
   server folder. Typed in game, it may stay in your client's chat history. The environment variable keeps the key out
   of both.
   :::
3. Switch the dashboard on in `config.yml`:

   ```yaml
   dashboard:
     enabled: true
   ```

4. Run `/companyadmin reload`.
5. Check the connection with `/companyadmin dashboard status`.

Players log in with `/company dashboard`, which shows the link and the code in a dialog, or in chat where
[menus](./configuration/config-yml.md#menus) are shown in chat. Geyser shows the dialog to Bedrock players as a form;
no Floodgate hook is needed.

The console says "Connected to the dashboard gateway" once the server is connected.

### The key from the environment

Instead of the command, you can set the environment variable `COMPANIES_DASHBOARD_KEY` for the primary server's
process (or write the key into `dashboard.serverKey` of its `node.yml`; the variable wins). The primary
reads it when it starts and on every reload, and stores the key in the database only when the variable
changed. An unchanged variable never replaces a key that was rotated since, or one you entered
with `/companyadmin dashboard key`. The secondary servers ignore this setting; they read the key from the database.

:::tip Never put the key into config.yml
`config.yml` is copied to every server and often shared when asking for help. Debug reports mask dashboard keys.
:::

### How the key rotates

A dashboard key changes over time. Once a day the primary server asks Lyna for the current key and stores the new
one in the database before it uses it. The other servers read it from the database before they connect. You do
nothing for this.

A key that is not used for 30 days expires. If your servers were off longer, or Lyna or the gateway refuses the key,
the console says so and `/companyadmin dashboard status` shows the reason. The servers then try again only once an
hour. Create a new key in the Lyna account area and enter it again; the servers connect with it at once.

If two servers share the same `nodeUid` in [node.yml](./configuration/node-yml.md), the gateway keeps only one of their
connections and the console says so. Give every server its own `nodeUid`.

## Settings

The `dashboard` section of `config.yml`:

```yaml
dashboard:
  enabled: false
  gateway: wss://companies.eldoria.de/api/v1/servers/connect
  sessionIdleMinutes: 60
  sessionMaxHours: 24
  features:
    orders.browse: true
    orders.accept: true
    orders.create: true
    orders.own: true
    orders.receive: true
    orders.deliver: true
    company.members: true
    company.applications: true
    company.profile: true
    company.treasury: true
    company.create: true
    company.disband: true
    inbox: true
    rankings: true
    market: true
  branding:
    name: ''
    icon: server-icon.png
```

| Setting | What it does |
|---|---|
| `enabled` | Whether the servers connect and players can open the dashboard. Off by default. |
| `gateway` | The gateway to connect to. Change it only for a [self-hosted gateway](#self-hosted-gateway). |
| `sessionIdleMinutes` | A login ends after this many minutes without use. |
| `sessionMaxHours` | A login ends after this many hours in any case. |
| `features` | Switch a part of the dashboard off with `false`. A part missing from the list is on. The dashboard shows `orders.browse`, `orders.accept`, `orders.create`, `orders.own`, `orders.deliver` (delivering, receiving and collecting, only while the player is online on the server), `company.members` (including handing the company over), `company.applications`, `company.profile`, `company.treasury`, `company.create`, `company.disband`, `inbox`, `rankings`, `market` and, for admins, `admin.orders`, `admin.players`, `admin.companies`, `admin.levels`, `admin.boards`, `admin.nodes`, `admin.config` and `admin.settings`. `orders.receive` is listed for older dashboards and has no effect; receiving belongs to `orders.deliver`. |
| `branding.name` | The name of your server on the dashboard. Empty uses your MOTD without colours. |
| `branding.icon` | A PNG of at most 256×256 pixels and 48 KB, in the plugin folder or the server folder. The default is each server's own `server-icon.png`. On a network you can instead upload one icon in the Settings tab, which every server then shows. |

Changes apply on `/companyadmin reload`, which also reconnects.

## Logins

A player runs `/company dashboard`, opens the short link on any device and enters the six-letter code shown in a dialog
or in chat. The code works for five minutes. The chat tells the player when a browser logged in, so a login they did not
do does not go unnoticed. See [Dashboard](../players/dashboard.md) for the player's side.

A player stays logged in until they run `/company dashboard logout`, the login is unused for `sessionIdleMinutes`, it
is older than `sessionMaxHours`, or you end it with `/companyadmin dashboard revoke <player>`.

A browser that keeps the dashboard open counts as using it, even without clicking. Logins survive a restart of the
gateway, and the time it was down does not count as unused. A login that ended while a server was not connected to the
gateway, for example through `/company dashboard logout`, ends in the browser too as soon as the server connects again.

Players need `companies.dashboard.use`, which everyone has by default. Taking it away ends the player's logins the
next time they are online.

### Offline players

Players use the dashboard while they are not on the server. Their server permissions cannot be checked then, so the
plugin remembers the ones that matter to the dashboard when the login starts and refreshes them while the player is
online. Company permissions are always current.

## Admin tools

Players with admin rights get an *Admin* section on the dashboard; other players never see it, and the gateway does not
even list the admin parts to them. Each tool works only with the permission of its `/companyadmin` command, checked by
the plugin, and every change is written to the server log as `[admin] <name>@dashboard …`, like the commands write
it.

| Tab | What it does | Permission | Same as |
|---|---|---|---|
| Orders | Open any order by id or find the orders of a player or company; take a claimed order from its company, mark it delivered, delete it with or without refund, give it more time. Every change but more time asks once more and names the money it moves. | `companies.admin.orders` | `/companyadmin order …` |
| Players | Look a player up: company and role, own orders, waiting items, latest messages | `companies.admin.player` | `/companyadmin player` |
| Players | Grant a player money, or items of a material to collect with `/order collect` | `companies.admin.grant` | `/companyadmin grant money`, `grant items` |
| Companies | Show a company's treasury with its latest movements; give or take money | `companies.admin.treasury` | `/companyadmin treasury`, `give`, `take` |
| Companies | Rename a company for free | `companies.admin.rename` | `/companyadmin rename` |
| Companies | Make a member the owner | `companies.admin.transferOwner` | `/companyadmin transferowner` |
| Companies | Dissolve a company after asking once more | `companies.admin.delete` | `/companyadmin delete` |
| Levels | Show every level; edit one in a form, add, move and remove levels. Changes are made on the primary server and passed on like the in-game editor does. | `companies.admin.level` | `/companyadmin level …` |
| Levels | Recalculate the level of every company | `companies.admin.calcLevel` | `/companyadmin calclevel` |
| Boards | Show the board presets with their open orders; write the board signs again; bind NPCs to boards and unbind them. On a network you pick the server first, since signs and NPCs belong to a server. An NPC is named by its Citizens id or FancyNpcs name. | `companies.admin.boards` | `/companyadmin board …` |
| Servers | Show the servers in the database with role, version, last seen and how they exchange messages; remove an old one after asking once more | `companies.admin.nodes` | `/companyadmin node …` |
| Servers | Mark the servers that use an older revision of the shared settings, and the changed settings each applies only after a restart | `companies.admin.nodes` | `/companyadmin node list` |
| Settings | Show `config.yml`, `company.yml`, `order.yml` and `user.yml` as their latest revision, grouped in sections, marking the settings that need a restart and those a server sets for itself in its `node.yml`; which servers are behind; the history of each file and what each revision changed. Secrets such as the Discord webhook are never shown, only whether they are set. | `companies.admin` | `/companyadmin settings status`, `history` |
| Settings | Change settings in forms and save them together, after asking once more, as the next revision, which every server takes. A wrong value is marked at its field and nothing is saved. When someone saved the same file meanwhile, nothing is saved: the newer revision is loaded, your changes stay, and each field the newer revision changed shows its value; saving again keeps your values. Admins without `companies.admin.reload` see the settings without being able to change them. The Discord webhook can be replaced or cleared but is never shown, and cannot be changed while `COMPANIES_DISCORD_WEBHOOK` sets it. Make an earlier revision the newest again from the history. Upload one PNG of at most 256×256 pixels and 40 KB as the dashboard icon of every server, or go back to each server's own server icon. The levels and the board presets stay in their own editors and files. | `companies.admin` and `companies.admin.reload` | `/companyadmin reload confirm`, `/companyadmin settings rollback` |
| Servers | Sync the configuration from the primary, after asking once more. Only the primary does it; while it is not connected, the button answers that the server is offline. The dashboard reconnects for a moment. | `companies.admin` | `/companyadmin syncconfiguration` |

Switch a tab off with `dashboard.features` like any other part: set `admin.orders`, `admin.players`,
`admin.companies`, `admin.levels`, `admin.boards`, `admin.nodes`, `admin.config` or `admin.settings` to `false`. The server picker shows
the `name` from [node.yml](./configuration/node-yml.md), or the role and the start of the `nodeUid`.

## Several servers

Every server of a network connects to the gateway, because each server only sees what happens on it. Logins are kept
in the database, so a player who logged in on one server is known to all of them. See
[Several servers](./multiserver.md).

## Plugin versions

The gateway serves every Companies version that is still supported. When your version gets old, the gateway says so:

- **Deprecated**: everything works. The console warns once per start, admins with `companies.admin` see a notice when
  they join, and `/companyadmin dashboard status` shows it. Update before the date it names.
- **Discontinued**: the dashboard does not open for your server. `/company dashboard` shows the reason. The server
  asks the gateway again every six hours, so it works again as soon as you update.

## What the gateway sees

The gateway relays what players see on the dashboard: orders, companies, members, balances, inbox messages and
rankings. It keeps logins and their codes, and the data of open dashboards in memory only. It never sees more than
the player sees in game: an order owner never learns which company or worker took the order, and a company never
learns the owner. The server decides every answer; the gateway cannot ask in the name of a player who did not log in
on your server.

The hosted gateway runs behind Cloudflare, which sees the traffic like the gateway does.

## Self-hosted gateway

You can run the gateway yourself with the same images. A self-hosted gateway needs no Lyna key. It prints a server
token starting with `cst_` on its first start; enter that token like a key:

```
/companyadmin dashboard key <token>
```

or set it in `node.yml` as `dashboard.serverKey`, or in the environment variable `COMPANIES_DASHBOARD_KEY`. Then
point `dashboard.gateway` at your gateway, for example `wss://dashboard.example.org/api/v1/servers/connect`, and
reload. A server token never rotates.

:::warning A Lyna key goes to Eldoria's gateway only
A Lyna dashboard key is sent only over `wss://` to `companies.eldoria.de`. With another `dashboard.gateway`, or with
`ws://`, the server keeps the key back, does not connect, and `/companyadmin dashboard status` says why. If a gateway
calls itself self-hosted after it received a Lyna key, the server disconnects and sends the key no more until you
enter a new key or reload. Give a self-hosted gateway its own server token.
:::
