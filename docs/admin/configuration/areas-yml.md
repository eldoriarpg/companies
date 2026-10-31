---
title: areas.yml
sidebar_position: 8
---

# areas.yml

[Company areas](../../companies/areas.md): plots and regions a company uses, and the company home. This file says whether companies
may get new areas and which plot and claim plugins they come from. It is shared by every server of a network. The
areas themselves are stored in the database.

```yaml
enabled: true
providerOrder:
  - plotsquared
  - worldguard
providers:
  plotsquared: true
  worldguard: true
homeWarmupSeconds: 3
homeCooldownSeconds: 60
homeCost: 0.0
homePaidByTreasury: false
homeInsideArea: true
sellBackPercent: 50
rentWarnHours: 24
```

| Key | Values | Default | Description | Takes effect |
|-----|--------|---------|-------------|--------------|
| `enabled` | true/false | `true` | Companies may get new areas. Areas they have keep working either way. | reload |
| `providerOrder` | list of provider ids | `plotsquared`, `worldguard` | The order the area plugins are asked which area holds a spot; the first that knows one wins. Providers not listed come after the listed ones. | reload |
| `providers` | provider id: true/false | both `true` | Switch an area plugin off for company areas and for [jobs](../../players/jobs.md) on its land (WorldGuard regions keep their own job hook). A provider not listed is on. | reload |
| `homeWarmupSeconds` | number (seconds) | `3` | How long a player stands still before the [teleport home](../../companies/home.md). Moving to another block or taking damage cancels it. `0` teleports at once. | reload |
| `homeCooldownSeconds` | number (seconds) | `60` | How long a player waits after a teleport home before the next. Kept per server. | reload |
| `homeCost` | money | `0` | What a teleport home costs. Paid when the warm-up ends. | reload |
| `homePaidByTreasury` | true/false | `false` | The company's [treasury](../../companies/treasury.md) pays the teleport instead of the player. Every member's teleport is paid then, without the `treasury` permission. | reload |
| `homeInsideArea` | true/false | `true` | A company that has areas sets its home inside one of them. A company without areas sets it anywhere. | reload |
| `sellBackPercent` | 0–100 | `50` | The part of its price a company gets back for a [bought area](../../companies/areas.md#buying-and-renting) it sells back. The owner of a disbanded company gets it for every bought area. | reload |
| `rentWarnHours` | number (hours) | `24` | How long before a rent is due the members who manage areas are told. `0` tells nobody. | reload |

Rents are paid on the primary server; with the [treasury](../../companies/treasury.md) switched off, rents can not be
paid and end when they are due.

## Area plugins

Companies brings providers for **PlotSquared** (a plot, merged plots counting as one) and **WorldGuard** (the region
with the highest priority at a spot, without the global region and the regions of jobs). They load when the plugin is
enabled and `hooks.plotsquared` or `hooks.worldguard` in [config.yml](./config-yml.md#generalsettings) is on. Other plugins can
add providers for their own plots or claims through the API (`CompaniesApi.areas().register(...)`); their id is what
`providerOrder` and `providers` name.

Areas of a provider that is not there, for example because its plugin was removed, stay stored and come back when it
is there again.
