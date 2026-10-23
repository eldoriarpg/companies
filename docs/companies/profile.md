---
title: Profile
sidebar_position: 2
---

# Profile

The profile is the face of your company. It shows the name, the level, the members and the join mode. You can add a
description, a short tag and a colour, so players recognise your company at a glance.

## View a profile

| Command | Shows |
|---|---|
| `/company` or `/company profile` | The profile of your own company |
| `/company profile name <name>` | The profile of a company by its name |
| `/company profile id <id>` | The profile of a company by its id |
| `/company profile player <player>` | The profile of the company of an online player |

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

Click `[Edit]` on your own profile, or type `/company profile edit`. In the dialog you change all fields in one form
and click `Save`. In chat, every field has a `[Change]` and a `[Clear]` button, and you pick the colour by clicking it.

You can also use commands:

| Command | What it does |
|---|---|
| `/company profile edit description <text>` | Set the description |
| `/company profile edit description` | Remove the description |
| `/company profile edit tag <tag>` | Set the tag |
| `/company profile edit tag clear` | Remove the tag |
| `/company profile edit color <colour>` | Set the colour. Tab completion suggests the colours. |
| `/company profile edit color clear` | Remove the colour |

The other members get a message when the profile changes.

The join mode is part of the profile too. It is changed with `/company joinmode`, see
[Founding and joining](./founding-and-joining.md#how-players-join).

## Rename the company

Only the owner can rename the company.

1. Type `/company rename <new name>`. The name can have up to 31 characters and must not be taken.
2. Companies shows the price. The default is 10,000.
3. Confirm with the button or `/company rename confirm`.

The price is taken from your balance. If your server allows it and the [treasury](./treasury.md) holds enough money,
the confirmation also offers `Pay from the treasury`. Typing `/company rename confirm treasury` does the same.
