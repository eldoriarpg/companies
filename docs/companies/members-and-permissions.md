---
title: Members and permissions
sidebar_position: 3
---

# Members and permissions

A company runs best when its members can help. This page shows how to see your members, give them permissions, and
what the owner can do: kick members, hand over the company or disband it.

## Your members

The **Members** panel of the [dashboard](../players/dashboard.md)'s company page lists your members with their
permissions and what they delivered, how many orders they worked on and what they earned. Search it by name.

In game, `/company member` shows the same, with whether each member is online and the contributions of all time and
of the last 30 days; sort it with `/company member items` or `/company member earned 30d` (the sortings are `name`,
`items`, `earned` and `orders`, the ranges `all` and `30d`). `/company member id <id>` lists the members of another
company: who is in it, but not their permissions or contributions.

How contributions are counted is explained in [Rankings and history](./rankings-and-history.md#member-statistics).

## Company permissions

Company permissions decide what a member may do in the company. They have nothing to do with the permissions of your
server.

| Permission | Allows |
|---|---|
| `invite` | Invite players, take invites back and answer applications |
| `kick` | Kick members who do not have `kick` themselves |
| `manage_orders` | Claim orders and give them back, and manage [order alerts](./order-alerts.md) |
| `manage_permissions` | Give and remove permissions of other members |
| `edit_profile` | Change the description, tag, colour and join mode |
| `treasury` | Take money from the [treasury](./treasury.md) and set the payout share |
| `build` | Build in the company's areas |
| `containers` | Open chests, barrels, furnaces and other containers in the company's areas |
| `manage_areas` | Set the company's home and link, buy, rent and give up areas, paid from the treasury |

The **owner** holds every permission. There is exactly one owner, and nobody can give or take that role with these
commands. [Hand over the company](#hand-over-the-company) for that.

Every member can do a few things without any permission: deliver items to the orders of the company, look at its
orders, alerts, history and treasury, and pay money into the treasury.

New members start without permissions.

### Give and remove permissions

You need the `manage_permissions` permission, or you are the owner.

On the dashboard, open a member in the members list: every permission is a switch, and **Save permissions** applies
your changes. In game, `/company permission <member>` opens the same editor, as a dialog with switches and `Save`, or
in chat with a button per permission that gives or removes it right away.

There are two rules:

- You can only give or remove permissions that you hold yourself. The editor shows the others greyed out.
- Nobody can change the permissions of the owner.

:::tip
Give `manage_orders` to the members who should pick the orders. Everyone else can still deliver.
:::

## Kick a member

**Remove from the company** on a member, or `/company kick <member>`, removes a member from the company after you
confirm.

- The owner can kick anyone.
- A member with the `kick` permission can kick members who do not have `kick` themselves.
- Nobody can kick the owner.

The kicked player and the other members get a message.

## Hand over the company

Only the owner can do this. **Hand the company over** on a member, or `/company transfer <member>`, makes another
member the owner after you confirm.

You stay in the company as a normal member, without owner rights. The new owner gets a message and can give you
permissions again.

## Disband the company

Only the owner can do this. **Dissolve the company** on the dashboard asks you to type the company's name first;
`/company disband` in game asks you to confirm.

When a company is disbanded:

- The orders it worked on are open again for other companies. Items that were already delivered go back to the
  members who delivered them.
- The money in the treasury is paid to the owner.
- Every member gets a message.

:::danger
Disbanding cannot be undone. The company, its profile and its members are gone.
:::

## Leave

Members leave with **Leave the company** or `/company leave`, see [Founding and
joining](./founding-and-joining.md#leave-a-company). The owner has to hand over the company or disband it first.
