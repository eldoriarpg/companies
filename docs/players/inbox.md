---
title: Inbox
sidebar_position: 3
---

# Inbox

Companies keeps every message about your orders and your company in your inbox. You miss nothing, even when you were
offline or played on another server of the network.

## How messages reach you

- **You are online.** The message shows in chat right away. Important ones, like a delivered order, also show on the
  action bar and play a sound.
- **You are online on another server of the network.** The message arrives after a few seconds, at once when the
  network uses Redis, and within about a second in low latency mode.
- **You are offline.** The message waits in your inbox. When you join, you see how many unread messages you have, with
  an `[Open]` button.

Many messages have a button, for example `[Receive your items]` on a delivered order or a button that opens your
invites. The button still works later from the inbox.

## Open your inbox

| Command | What it does |
|---|---|
| `/company inbox [page]` | Show your messages, newest first, grouped by day |
| `/company inbox read` | Mark every message as read |
| `/company inbox clear` | Delete the messages you have read |
| `/company inbox settings` | Choose which categories are muted |

You do not need to be in a company to use the inbox.

New messages are marked as new. A page counts as read as soon as you open it. Messages are deleted after 30 days by
default, read or not.

## Categories

Every message belongs to a category. Some categories can be muted.

| Category | What it holds | Can be muted |
|---|---|---|
| `my_orders` | Your own orders: claimed, delivered, open again, about to be removed, removed, changed by an admin. Your repeating orders. Items an admin gave you. | no |
| `invites` | Invites and applications | no |
| `company` | Your company was disbanded, you were kicked, ownership changes, profile changes, treasury share changes and payouts | no |
| `members` | Members joining, leaving or being kicked | yes |
| `orders` | Orders of your company: claimed, given back, delivered, expired or changed by an admin | yes |
| `payments` | Your payments for delivered orders, and money an admin gave you | yes |
| `level` | Your company level going up or down | yes |
| `alerts` | New orders that match an order alert of your company | yes |

## Muting

In a busy company, many messages fly by. Mute the categories you do not want to see live.

- `/company inbox mute <category>` mutes a category.
- `/company inbox unmute <category>` shows it again.
- `/company inbox settings` shows a switch for each category. The inbox menu has a button for it too.

Muted messages still land in your inbox. They are just not shown in chat, and they are not counted in the summary when
you join. Look at them any time with `/company inbox`.

:::note
Messages about your own orders, invites and your company cannot be muted. They are always shown.
:::
