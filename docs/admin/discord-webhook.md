---
title: Discord webhook
sidebar_position: 4
---

# Discord webhook

Companies can post to a Discord channel when an order is placed, when an order is completed and when a company
reaches a higher level. It uses a Discord webhook, so you need no bot.

## Setup

1. In Discord, open the settings of the channel. Go to **Integrations**, then **Webhooks**, and create a webhook.
2. Copy the webhook URL.
3. Add the `discord` section to `config.yml`:

   ```yaml
   discord:
     enabled: true
     url: env:COMPANIES_DISCORD_WEBHOOK
     events: [ORDER_CREATED, ORDER_COMPLETED, LEVEL_UP]
     username: Companies
     avatarUrl: ''
     maxPerMinute: 20
     queueSize: 100
   ```

4. Put the URL where the `url` setting points (see below).
5. Restart the server.

The webhook is off by default (`enabled: false`). When it starts, the console says "Posting to the Discord webhook."

## The URL

`url` takes the webhook URL itself, or `env:NAME` to read it from the environment variable `NAME` of the server
process.

```yaml
discord:
  url: env:COMPANIES_DISCORD_WEBHOOK
```

```bash
export COMPANIES_DISCORD_WEBHOOK='https://discord.com/api/webhooks/...'
```

:::tip Use env: if you can
The webhook URL contains a secret. Anyone who has it can post to your channel. With `env:`, the secret stays out of
your config files. This matters even more on [several servers](./multiserver.md): the primary server stores
`config.yml` in the database and the other servers copy it. With `env:`, only the variable name is copied, and you
set the variable on each server.
:::

The URL has to start with `https://`. A host other than Discord is allowed, for example a relay, and the console
notes it at start.

If something is wrong, the webhook does not start and the console tells you why, without showing the URL:

- "The environment variable NAME for the Discord webhook is not set."
- "No Discord webhook url is configured."
- "The Discord webhook url is not a valid url."
- "The Discord webhook url has to start with https://."

## Events

`events` picks what is posted:

| Event | Posted when | The message shows |
|---|---|---|
| `ORDER_CREATED` | An order is placed | Order name, items, price |
| `ORDER_COMPLETED` | An order is fully delivered | Order name, items, price |
| `LEVEL_UP` | A company reaches a higher level | Company name, new level |

Order messages never show who placed the order or which company delivered it. They also show no fees, taxes or
payouts. Text written by players is escaped, so an order name cannot format text, post links or ping anyone.

Each event is posted by the one server it happens on. On a network, nothing is posted twice.

## Name and picture

`username` and `avatarUrl` set the name and picture of the posts. Leave them empty to use what you set for the
webhook in Discord.

## Limits

| Key | Default | Range | Meaning |
|---|---|---|---|
| `maxPerMinute` | `20` | 1 to 60 | Requests per minute, for each server |
| `queueSize` | `100` | 1 to 1000 | Messages that can wait to be sent. When the queue is full, the oldest one is dropped |

Each request carries up to ten messages, so busy servers still keep up. Companies also follows the rate limits Discord
sends back.

When something goes wrong:

- **Discord or the connection fails:** Companies tries again three times, after 2, 4 and 8 seconds. Then the messages
  are dropped.
- **Discord rejects a message (400):** the console shows Discord's answer and the message is dropped.
- **The webhook is gone (401, 403 or 404):** the webhook is switched off until the next restart. This happens when
  someone deletes the webhook in Discord.

Dropped messages are reported in the console at most once a minute.

## Reload or restart

| Setting | Applies on |
|---|---|
| `events` | `/companyadmin reload` |
| `enabled`, `url`, `username`, `avatarUrl`, `maxPerMinute`, `queueSize` | Restart |

`/companyadmin reload` does not start, stop or repair the webhook. Restart the server for that.

## Secrets in logs and debug reports

The webhook URL is never written to the log.

`/companyadmin debug` creates a debug report you can share for support. Before it is uploaded, the report masks the
token of every webhook URL it contains, and the `url` of the `discord` section as a whole. An `env:` reference stays
visible, because it only names a variable and holds no secret.

All `discord` keys are also listed on the [config.yml](./configuration/config-yml.md) page.
