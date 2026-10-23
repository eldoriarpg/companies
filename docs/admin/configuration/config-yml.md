---
title: config.yml
sidebar_position: 1
---

# config.yml

General settings: language, menus, style, notifications, placeholders, boards and Discord.

In a network, this file is shared. Edit it on the primary server. See [Configuration](./index.md#in-a-network).

The **Applies** column says whether `/companyadmin reload` is enough, or whether you need a restart.

## Default file

```yaml
generalSettings:
  language: en_US
  orderCheckInterval: 60
  updates:
    notice: true
    autoUpdate: false
    askForAutoUpdate: true
  messageBlocking: true
  hooks:
    placeholderapi: true
    citizens: true
    fancynpcs: true
  historyDays: 90
style:
  prefix: <#00a6ff>[C]
  message: dark_aqua
  error: '#f05316'
  heading: gold
  name: aqua
  value: dark_green
  remove: red
  add: green
  modify: yellow
  show: green
  inactive: dark_gray
  active: green
  neutral: dark_aqua
menus: DIALOG
economy:
  collectorAccount: ''
notifications:
  pollSeconds: 10
  retentionDays: 30
  pageSize: 10
  actionBar: true
  sound: minecraft:entity.experience_orb.pickup
placeholders:
  refreshSeconds: 300
  offlineCacheSize: 500
  leaderboardSize: 10
  loadingText: ''
top:
  cacheSeconds: 30
boards:
  enabled: true
  refreshSeconds: 60
  signHeader: '[Companies]'
  presets:
    iron:
      label: Iron
      name: ''
      materials: [iron_ingot, iron_block, raw_iron]
      anyMaterial: true
      exactMatch: true
      minPrice: 0.0
      maxPrice: 0.0
      minSize: 0
      maxSize: 0
discord:
  enabled: false
  url: ''
  events: [ORDER_CREATED, ORDER_COMPLETED, LEVEL_UP]
  username: Companies
  avatarUrl: ''
  maxPerMinute: 20
  queueSize: 100
```

## generalSettings

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `language` | text | `en_US` | Language of the messages. Included: `en_US`, `de_DE`, `fr_FR`. | reload |
| `orderCheckInterval` | number (minutes) | `60` | How often the primary server looks for expired orders and orders to remove. The first check runs 10 minutes after the start. | restart |
| `updates.notice` | true/false | `true` | Tell the console and admins about new versions. | reload |
| `updates.autoUpdate` | true/false | `false` | Download a new stable version within the same major version. It is used from the next start. | reload |
| `updates.askForAutoUpdate` | true/false | `true` | Ask admins in chat to turn on `autoUpdate`. | reload |
| `messageBlocking` | true/false | `true` | Hold back other chat messages while a chat menu is open. With `false`, nothing is held back. | restart |
| `hooks.placeholderapi` | true/false | `true` | Register the [placeholders](../../reference/placeholders.md) when PlaceholderAPI is installed. | restart |
| `hooks.citizens` | true/false | `true` | Allow binding Citizens NPCs to order boards. | restart |
| `hooks.fancynpcs` | true/false | `true` | Allow binding FancyNpcs NPCs to order boards. | restart |
| `historyDays` | number (days) | `90` | How long an ended order stays in the order history. At least 1. Cleaned up by the primary server. | reload |

`updates.notice` and `updates.askForAutoUpdate` go to holders of `companies.updates`.

:::note Old files
Files from Companies 1.x have `checkUpdates` instead of `updates`. Companies still reads it as the value of `updates.notice`. It never writes it back.
:::

## style

The colours and prefix of the chat messages.

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `prefix` | text | `<#00a6ff>[C]` | Put in front of every message. Written in [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format. | reload |
| `message` | colour | `dark_aqua` | Colour of normal messages. | reload |
| `error` | colour | `#f05316` | Colour of error messages. | reload |
| `heading` | colour | `gold` | Colour of headings. | reload |
| `name` | colour | `aqua` | Colour of names. | reload |
| `value` | colour | `dark_green` | Colour of values. | reload |
| `remove` | colour | `red` | Colour of remove buttons and removed values. | reload |
| `add` | colour | `green` | Colour of add buttons and added values. | reload |
| `modify` | colour | `yellow` | Colour of change buttons. | reload |
| `show` | colour | `green` | Colour of show buttons. | reload |
| `inactive` | colour | `dark_gray` | Colour of entries that are off or cannot be used. | reload |
| `active` | colour | `green` | Colour of entries that are on. | reload |
| `neutral` | colour | `dark_aqua` | Colour of neutral text and buttons. | reload |

A colour is a hex value like `'#f05316'` or a named colour like `gold`, `dark_aqua` or `light_purple`. Put hex values in quotes, because `#` starts a comment in YAML. A value that is not a colour falls back to the default of that key.

The keys from `heading` to `neutral` are the colours of the tags `<heading>`, `<name>` and so on in the language files.

## menus

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `menus` | `DIALOG` or `CHAT` | `DIALOG` for a new file | How menus such as the order builder, the order search and confirmations are shown. | reload |

| Value | Menus are shown as |
|---|---|
| `DIALOG` | Dialog windows with input fields and buttons. |
| `CHAT` | Clickable chat messages. |

`menus` is a single value, not a section: write `menus: DIALOG`.

- New installations get `DIALOG`.
- A `config.yml` from an older version has no `menus` key. It keeps showing chat menus until you add `menus: DIALOG`.
- Players with a client older than Minecraft 1.21.6 always get chat menus, for example when they join through ViaVersion.

:::warning TODO
Confirm what happens when `menus:` is written with no value. The code is meant to fall back to chat menus, but this was not tested.
:::

## economy

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `collectorAccount` | text | `''` | The account that receives fees and taxes. A player UUID or a Vault account name. When empty, fees and taxes are removed from the economy. | reload |

See [Economy](../economy.md) for where money goes.

## notifications

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `pollSeconds` | number (seconds) | `10` | How often each server looks for notifications sent to its players from another server. At least 1. | restart |
| `retentionDays` | number (days) | `30` | How long notifications stay in the inbox, read or not. At least 1. Cleaned up by the primary server. | reload |
| `pageSize` | number | `10` | Notifications on one inbox page. From 1 to 50. | reload |
| `actionBar` | true/false | `true` | Also show important notifications on the action bar. | reload |
| `sound` | sound key | `minecraft:entity.experience_orb.pickup` | Sound played with important notifications. Leave it empty for no sound. | reload |

See [Inbox](../../players/inbox.md) for what players see.

## placeholders

The PlaceholderAPI values are kept in memory. These keys set how. All of them need a restart.

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `refreshSeconds` | number (seconds) | `300` | How often the values are loaded again. At least 10. | restart |
| `offlineCacheSize` | number | `500` | How many offline players and companies have their values kept. At least 1. | restart |
| `leaderboardSize` | number | `10` | How many places of each ranking the `top_` placeholders can read. At least 1. | restart |
| `loadingText` | text | `''` | Text shown while a value is loaded for the first time. | restart |

See [Placeholders](../../reference/placeholders.md).

## top

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `cacheSeconds` | number (seconds) | `30` | How long a page of `/company top` and the place of a company are kept. `0` turns the cache off. | restart |

## boards

Order boards on signs and NPCs. See [Boards](../boards.md) for a guide.

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `enabled` | true/false | `true` | Turn sign boards on. With `false`, `/company board` and NPC boards still work. | restart |
| `refreshSeconds` | number (seconds) | `60` | How often the order counts on board signs are updated. At least 10. | reload |
| `signHeader` | text | `[Companies]` | First line that turns a sign into a board. Case does not matter. When empty, `[Companies]` is used. | reload |
| `presets` | map of presets | one preset `iron` | Named filters for boards. | reload |

### Presets

Each preset has a name, like `iron` above. Players use the name on signs, on NPCs and in `/company board`. Names ignore case. If two presets have the same name, the first one counts.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `label` | text | `''` | Shown on signs. When empty, the preset name is shown. |
| `name` | text | `''` | Part of the order name. When empty, any name matches. |
| `materials` | list of materials | `[]` | Materials to look for, like `iron_ingot`. |
| `anyMaterial` | true/false | `true` | `true`: an order with any of the materials matches. `false`: an order needs all of them. |
| `exactMatch` | true/false | `false` | `true`: whole material names. `false`: parts of names, so `iron` also finds `iron_ingot`. |
| `minPrice` | number | `0` | Lowest order price. |
| `maxPrice` | number | `0` | Highest order price. `0` means no limit. |
| `minSize` | number | `0` | Lowest number of items in the order. |
| `maxSize` | number | `0` | Highest number of items in the order. `0` means no limit. |

Changes to presets apply on reload.

## discord

Posts new orders, completed orders and level ups to a Discord channel. See [Discord webhook](../discord-webhook.md) for a guide.

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `enabled` | true/false | `false` | Post to the webhook. | restart |
| `url` | text | `''` | The webhook URL, or `env:NAME` to read it from the environment variable `NAME`. It must start with `https://`. | restart |
| `events` | list | `[ORDER_CREATED, ORDER_COMPLETED, LEVEL_UP]` | What is posted. `ORDER_CREATED`: a new order. `ORDER_COMPLETED`: an order was fully delivered. `LEVEL_UP`: a company reached a higher level. | reload |
| `username` | text | `Companies` | Name of the posts. When empty, the webhook's own name is used. | restart |
| `avatarUrl` | text | `''` | Picture of the posts. When empty, the webhook's own picture is used. | restart |
| `maxPerMinute` | number | `20` | Requests per minute from each server. From 1 to 60. | restart |
| `queueSize` | number | `100` | Messages that may wait to be sent. From 1 to 1000. When the queue is full, the oldest message is dropped. | restart |

:::tip
The webhook URL is a secret. In a network this file is copied to every server through the database. Use `env:NAME` there and set the variable on each server.
:::
