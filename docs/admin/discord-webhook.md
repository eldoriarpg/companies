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
     url: ''
     events: [ORDER_CREATED, ORDER_COMPLETED, LEVEL_UP, OFFER_STARTED, GOAL_PROGRESS, CONTEST_RESULT]
     username: Companies
     avatarUrl: ''
     maxPerMinute: 20
     queueSize: 100
   ```

4. Set the URL as the environment variable `COMPANIES_DISCORD_WEBHOOK`, or write it into `url` (see below).
5. Run `/companyadmin reload`.

The webhook is off by default (`enabled: false`). When it starts, the console says "Posting to the Discord webhook."

## The URL

`url` takes the webhook URL. The environment variable `COMPANIES_DISCORD_WEBHOOK` of the server process wins over it:

```bash
export COMPANIES_DISCORD_WEBHOOK='https://discord.com/api/webhooks/...'
```

:::tip Use the variable if you can
The webhook URL contains a secret. Anyone who has it can post to your channel. Set through the variable, the secret
stays out of your config files: Companies never writes it into a file, the database or a debug report. This matters
even more on [several servers](./multiserver.md): the primary server stores `config.yml` in the database and the
other servers copy it. Set the variable on each server and leave `url` empty.
:::

The URL has to start with `https://`. A host other than Discord is allowed, for example a relay, and the console
notes it at start.

If something is wrong, the webhook does not start and the console tells you why, without showing the URL:

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
| `OFFER_STARTED` | An [event offer](./admin-commands.md#event-offers), goal or contest, starts | Name, kind, items asked for, price per item, end |
| `GOAL_PROGRESS` | A goal passes 25, 50, 75 or 100 % (100 % is posted as "Goal reached") | Name, progress, items delivered |
| `CONTEST_RESULT` | A contest closes, or is cancelled | Name, the first 10 places with their items and paid prizes |

Order messages never show who placed the order or which company delivered it. They also show no fees, taxes or
payouts. Text written by players is escaped, so an order name cannot format text, post links or ping anyone.

Each event is posted by the one server it happens on. On a network, nothing is posted twice. Event offers are posted
by the primary server. Each progress threshold of a goal is posted once, also across restarts; a goal that passed
several thresholds between two checks posts the highest one only.

A config written before the event offers keeps its list; add the three new events to post them.

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
- **The webhook is gone (401, 403 or 404):** the webhook is switched off until the next `/companyadmin reload` or
  restart. This happens when someone deletes the webhook in Discord. Put in a new URL and reload.

Dropped messages are reported in the console at most once a minute.

## Reload

Every `discord` setting applies on `/companyadmin reload`. The reload starts the webhook again with the new settings.
It also starts, stops or brings back the webhook when you changed `enabled` or `url`. Messages that were still waiting
are sent to the old webhook first.

## Secrets in logs and debug reports

The webhook URL is never written to the log.

`/companyadmin debug` creates a debug report you can share for support. Before it is uploaded, the report masks the
token of every webhook URL it contains, and the `url` of the `discord` section as a whole. A URL set through the
variable is not in the report at all.

All `discord` keys are also listed on the [config.yml](./configuration/config-yml.md) page.
