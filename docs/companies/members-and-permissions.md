---
title: Members and permissions
sidebar_position: 3
---

# Members and permissions

A company runs best when its members can help. This page shows how to see your members, give them permissions, and
what the owner can do: kick members, hand over the company or disband it.

## Your members

`/company member` lists the members of your company. Each member shows whether they are online, their permissions and
what they contributed: delivered items, earned money and orders, for all time and for the last 30 days.

Sort the list by a contribution:

- `/company member items` sorts by delivered items.
- `/company member earned 30d` sorts by money earned in the last 30 days.
- The sortings are `name`, `items`, `earned` and `orders`. The ranges are `all` and `30d`.

`/company member id <id>` lists the members of another company. You see who is in it, but not their permissions or
contributions.

How contributions are counted is explained in [Rankings and history](./rankings-and-history.md#member-statistics).

## Company permissions

Company permissions decide what a member may do in the company. They have nothing to do with the permissions of your
server.

| Permission | Allows |
|---|---|
| `invite` | Invite players, take invites back and answer applications |
| `kick` | Kick members who do not have `kick` themselves |
| `manage_orders` | Accept and abort orders, and manage [order alerts](./order-alerts.md) |
| `manage_permissions` | Give and remove permissions of other members |
| `edit_profile` | Change the description, tag, colour and join mode |
| `treasury` | Take money from the [treasury](./treasury.md) and set the payout share |

The **owner** holds every permission. There is exactly one owner, and nobody can give or take that role with these
commands. Use `/company transfer` for that.

Every member can do a few things without any permission: deliver items to the orders of the company, look at its
orders, alerts, history and treasury, and pay money into the treasury.

New members start without permissions.

### Give and remove permissions

You need the `manage_permissions` permission, or you are the owner.

- `/company permission <member>` opens the permission editor of a member. The member list has a button for it too.
- `/company permission <member> give <permission>` gives a permission.
- `/company permission <member> remove <permission>` removes one.

In the dialog, every permission is a switch. Click `Save` to apply your changes. In chat, every permission is a button
that gives or removes it right away.

There are two rules:

- You can only give or remove permissions that you hold yourself. The editor shows the others greyed out.
- Nobody can change the permissions of the owner.

:::tip
Give `manage_orders` to the members who should pick the orders. Everyone else can still deliver.
:::

## Kick a member

`/company kick <member>` removes a member from the company. Confirm with the button or `/company kick confirm`.

- The owner can kick anyone.
- A member with the `kick` permission can kick members who do not have `kick` themselves.
- Nobody can kick the owner.

The kicked player and the other members get a message.

## Hand over the company

Only the owner can do this. `/company transfer <member>` makes another member the owner. Confirm with the button or
`/company transfer confirm`.

You stay in the company as a normal member, without owner rights. The new owner gets a message and can give you
permissions again.

## Disband the company

Only the owner can do this. `/company disband` dissolves the company. Confirm with the button or
`/company disband confirm`.

When a company is disbanded:

- The orders it worked on are open again for other companies. Items that were already delivered go back to the
  members who delivered them.
- The money in the treasury is paid to the owner.
- Every member gets a message.

:::danger
Disbanding can not be undone. The company, its profile and its members are gone.
:::

## Leave

Members leave with `/company leave`, see [Founding and joining](./founding-and-joining.md#leave-a-company). The owner
has to hand over the company or disband it first.
