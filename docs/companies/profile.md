---
title: Profile
sidebar_position: 2
---

# Profile

The profile is the face of your company. It shows the name, the level, the members and the join mode. You can add a
description, a short tag and a colour, so players recognise your company at a glance.

## View a profile

The [dashboard](../players/dashboard.md)'s company page shows your own company's profile with its level, rank,
orders and members. In game, `/company` shows your own profile, and `/company profile name <name>`, `id <id>` or
`player <player>` shows the profile of another company.

Every profile shows the tag and name in the company colour, the description, the join mode, the level, the founding
date, the owner and the members.

Your own profile shows more. You see the progress towards the next level and the orders your company works on. Hover
over the level to see its requirements and limits.

## Description, tag and colour

| Field | What it is | Rules |
|---|---|---|
| Description | A short text about your company | Up to 160 characters |
| Tag | A few letters or digits, shown as `[TAG]` before the name | 2 to 5 letters or digits. Every tag is unique. |
| Colour | The colour of tag and name | One of the colours your server offers |

Tags are always stored in upper case, so `ore` becomes `ORE`. Some tags are reserved and can never be used, by default
`ADMIN`, `MOD` and `STAFF`. Your server may change these rules in [company.yml](../admin/configuration/company-yml.md).

### Edit the profile

Members with the `edit_profile` permission can change the profile. The owner always can.

On the dashboard, **Edit profile** on the company page changes description, tag and colour in one form, and
**Save profile** applies them. In game, click `[Edit]` on your own profile or type `/company profile edit`: in the
dialog you change all fields in one form and click `Save`; in chat, every field has a `[Change]` and a `[Clear]`
button, and you pick the colour by clicking it. The [Commands](../reference/commands.md) page lists the
`/company profile edit` commands for each field.

The other members get a message when the profile changes.

The join mode is part of the profile too, see
[Founding and joining](./founding-and-joining.md#how-players-join).

## Rename the company

Only the owner can rename the company.

On the dashboard, **Rename** on the company page takes the new name and shows the price, 10,000 by default. Pay it
yourself, or from the [treasury](./treasury.md) when your server allows it and the treasury holds enough money.

In game, type `/company rename <new name>`, see the price, and confirm with the button or `/company rename confirm`;
`Pay from the treasury` in the confirmation, or `/company rename confirm treasury`, pays from the treasury. The name
can have up to 31 characters and must not be taken.
