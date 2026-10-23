---
title: Order boards
sidebar_position: 4
---

# Order boards

An order board shows the open orders right where you are. Your server may have board signs at the market, or NPCs
that open a board when you click them. You can also open a board with a command.

A board is a ready-made order search. It shows open orders, often only those for certain items, like iron or wood.

## Open a board

- **Sign.** Right-click a board sign. Its first line is `[Companies]` on most servers. The sign shows what the board is about and how many
  orders are open.
- **NPC.** Right-click the NPC.
- **Command.** `/company board` shows every open order. `/company board <board>` shows the orders of a board, for
  example `/company board iron`.

Instead of a board name, you can also type materials separated by spaces, for example
`/company board oak_log birch_log`. Each one matches every material whose name contains it, so `iron` finds iron ingots, iron blocks, raw iron
and more.

Your server decides which boards exist. Tab completion suggests their names.

## What you see

The board shows the first page of results. Each order shows its items and price when you hover over it, and has an
info button.

- **You are in a company** and may manage its orders: an `[Accept]` button claims an order for your company. The board
  only shows orders your company could take. Read [Claiming and delivering](../companies/claiming-and-delivering.md)
  for what comes next.
- **You are in a company** but may not manage orders: you see the orders, but cannot claim them.
- **You are in no company**: you see every open order, but cannot claim it. It is a good way to see what is in demand
  before you found or join a company.
- **Your server lets you work alone**: a `[Work alone]` button shows the orders of the board you could take on your
  own. See [Working alone](./working-alone.md).

`[Change]` opens the full search form, where you can change the filter. The search is explained in
[Claiming and delivering](../companies/claiming-and-delivering.md#find-orders).

:::note
A board opens at most once per second, so a double click shows one menu. Boards need the permission
`companies.boards.use`, which every player has by default.
:::

## For server owners

How to create board signs, define board presets and bind NPCs is explained in [Boards](../admin/boards.md) in the
admin section.
