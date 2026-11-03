---
title: Perks
sidebar_position: 10
---

# Perks

Perks are extra abilities a company buys once from its [treasury](./treasury.md). Each perk has a price and a minimum
company level: it can be bought from that level on. The server decides which perks it offers, what they cost and from
which level, in [perks.yml](../admin/configuration/perks-yml.md). Perks come with features that need them and with
plugins built on Companies; while none is registered, `/company perks` says that no perks are offered.

| Command | Who | What it does |
|---|---|---|
| `/company perks` | every member | Lists the perks: what each gives, its price, its level and whether your company owns it. |
| `/company perks buy <perk>` | members with `manage_perks` | Asks you to confirm the price of a perk. |
| `/company perks confirm` | members with `manage_perks` | Buys the perk you were asked about, paid from the treasury. |

The list marks each perk:

- **owned:** your company bought it and can use it.
- **paused:** your company owns it but can not use it right now, because its level dropped below the perk's level or the
  server switched the perk off. It works again once the level is back or the server offers it again; nothing is bought
  twice.
- **available:** your company can buy it. Members with `manage_perks` get a buy button.
- **locked:** your company needs a higher level first.

The price is taken from the treasury, never from your own money, so the treasury has to be switched on and hold enough.
The purchase shows in the treasury's log. A perk is bought once and kept; when the company is disbanded, its perks are
gone with it.
