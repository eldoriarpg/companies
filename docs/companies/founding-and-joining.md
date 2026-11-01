---
title: Founding and joining
sidebar_position: 1
---

# Founding and joining

A company is a group of players who work on orders together. You can found your own company or join one. A player can
be in one company at a time.

## Found a company

On the [dashboard](../players/dashboard.md), open **Company** while you are in none. **Found a company** takes a name,
shows what founding costs, and takes the price from your balance, also while you are not online.

In game:

1. Type `/company create <name>`. The name can have up to 31 characters and must not be taken.
2. Companies shows what founding costs. The default is 20,000.
3. Confirm with the button or `/company create confirm`. `/company create deny` or the deny button stops it.

The price is taken from your balance and you are the owner of the new company. The owner holds every
[company permission](./members-and-permissions.md) and can never be kicked.

:::note
A confirmation is valid for 30 seconds. After that, run the command again.
:::

A new company starts at level 1. The level decides how many members the company can have and how many orders it can
work on at once. It grows as the company completes orders. The company page of the dashboard and `/company` show the
level and the progress to the next one. Server owners set up the levels, see [Levels](../admin/levels.md).

## How players join

Every company has a join mode. It decides how new players get in. The profile of a company shows its join mode.

| Join mode | How players join |
|---|---|
| Open | Anyone can join with `/company join <company>`. |
| Applications | Players apply with a short message. The company accepts or denies it. |
| Invite only | Only invited players can join. This is the default for new companies. |

Invites work in every join mode.

Change the join mode under **Joining** in **Edit profile** on the dashboard's company page, or with
`/company joinmode open`, `/company joinmode applications` or `/company joinmode invite` in game. This needs the
`edit_profile` permission. Applications that are already open stay open when you change the mode, and you can still
accept them.

A company cannot take new members while it is at the member limit of its level.

## Invites

### Invite a player

Inviting happens in game: `/company invite <player>` invites a player to your company. You need the `invite` permission.
The player does not need to be online. They get a message in their [inbox](../players/inbox.md) when they join.

An invite is open for 24 hours by default. Inviting the same player again starts the time again. **Take back** on the
dashboard's company page, or `/company invite revoke <player>`, takes an invite back.

### Answer an invite

Answering happens in game. `/company invites` shows the invites you received, with buttons to accept or deny them.

- `/company invites accept <company>` joins the company.
- `/company invites deny <company>` declines the invite.

The member who invited you is told about your answer. If you do not answer in time, the invite expires and you
both get a message.

## Applications

### Apply to a company

If a company takes applications, apply in game with a short message:

`/company apply <company> <message>`

Write a company name with spaces in quotes, for example `/company apply "Stone Works" I mine a lot of stone`. The
message can have up to 120 characters. You can have up to 3 open applications at the same time, across all companies.

`/company invites` also shows your open applications. `/company apply withdraw <company>` takes one back.

### Answer applications

Members with the `invite` permission get a message when someone applies. The dashboard's company page lists the open
applications with their messages, with **Accept** and **Deny**. In game, `/company applications` shows them, and
`/company applications accept <player>` or `deny <player>` answers one.

The player is told about your answer. An application that nobody answers expires after 24 hours by default.

## After joining

When you join a company, every other invite and application of yours is removed. The other members get a message that
you joined.

New members start without any company permissions. They can deliver items to the orders of the company and pay into
its treasury. The owner, or a member with the `manage_permissions` permission, can give them more. See
[Members and permissions](./members-and-permissions.md).

## Leave a company

**Leave the company** on the dashboard's company page, or `/company leave` in game, leaves your company. Either asks
you to confirm first.

The owner cannot leave. Hand the company to another member first, or disband it. Both are explained in [Members and
permissions](./members-and-permissions.md).

## Permissions

Founding needs `companies.company.create`. Joining, accepting invites and applying need `companies.company.join`. Every
player has both by default. See [Permissions](../reference/permissions.md).
