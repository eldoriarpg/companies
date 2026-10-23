---
title: Order alerts
sidebar_position: 6
---

# Order alerts

Your company mines iron? Then you want to hear about every new iron order right away. An order alert is a saved order
search. When a new order matches it, the members who manage orders get a message.

## Create an alert

You need the `manage_orders` permission.

1. Open the order search with `/company order search`.
2. Set the filters you want, for example the material `iron_ingot` and a minimum price. The search is explained in
   [Claiming and delivering](./claiming-and-delivering.md#find-orders).
3. Save it as an alert:
   - In the dialog, click `Save as alert` and enter a name.
   - In chat, click `[Save as alert]` or type `/company alerts add <name>`.

The name can have up to 32 letters, digits, `-` and `_`, for example `iron` or `big_wood_orders`.

An alert keeps the name filter, the materials, how materials match, and the price and size limits. The sorting and the
"I can fill this" filter are not part of an alert.

## Manage your alerts

`/company alerts` lists the alerts of your company. Each one shows what it matches, whether it is on, and when it last
sent a message. Every member can look at the list.

| Command | Who | What it does |
|---|---|---|
| `/company alerts` | every member | List the alerts |
| `/company alerts show <name>` | every member | Search the open orders the alert matches |
| `/company alerts add <name>` | `manage_orders` | Save your current search as an alert |
| `/company alerts off <name>` | `manage_orders` | Switch an alert off |
| `/company alerts on <name>` | `manage_orders` | Switch it on again |
| `/company alerts remove <name>` | `manage_orders` | Delete an alert |

A company can have 3 alerts by default. Your server may allow more or fewer per level. On some servers or levels,
alerts are not available at all.

## The message

When a new order matches, every member with `manage_orders` gets a message. It shows:

- the name of the alert, or of several alerts if more than one matched,
- the name of the order and its first lines,
- the price and what is left after the tax of your company.

The `[View]` button opens the order, and `[Accept]` claims it right away. The message never says who placed the order.

Messages reach you on every server of the network. They are in the `alerts` category of your
[inbox](../players/inbox.md), which you can mute.

## Which orders you hear about

You only hear about orders your company could claim. You get no message:

- about orders of your own members, unless your server allows them,
- about orders larger than your level allows,
- while your company works on as many orders as its level allows.

## Not too many messages

Each alert sends at most one message every 15 minutes by default. New orders that match in between are not lost. They
are counted, and you get one summary later, for example "3 more orders match iron". Its button shows them all.
