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

Companies brings these providers:

| Provider | Id | An area is | Link own | Build mirroring | Buy and rent | Jobs |
|---|---|---|---|---|---|---|
| PlotSquared | `plotsquared` | a plot, merged plots counting as one | yes | trusted players | yes | yes |
| WorldGuard | `worldguard` | the region with the highest priority at a spot, without the global region and the regions of jobs | yes | region members | yes | through its own job regions |
| GriefPrevention | `griefprevention` | a top-level claim; a subdivision means the claim it lies in | yes | build trust | yes; given back without a keeper it becomes an admin claim | yes |

PlotSquared and WorldGuard load when the plugin is enabled and `hooks.plotsquared` or `hooks.worldguard` in
[config.yml](./config-yml.md#generalsettings) is on. The other providers load whenever their plugin is enabled; switch
one off with `providers`. A provider switched off carries neither company areas nor jobs.

GriefPrevention notes:

- A claim a company buys counts against the claim blocks of the company's owner.
- A claim that changes hands loses all its trust: nobody the old owner trusted keeps access. That includes a bought
  claim moving to the company's new owner; members with `build` get build trust again within half a minute, any other
  trust has to be given again.
- GriefPrevention keeps one trust per player. A member the claim's owner gave container or access trust gets build
  trust while they may build for the company, and no trust after.
- Subdivisions that do not inherit the claim's trust keep company members out.
- GriefPrevention deletes claims of owners who stay away long enough. Offer admin claims (they have no keeper and are
  never deleted for inactivity), or exempt the keeper's account, and keep in mind that a bought claim of an inactive
  company owner can expire too.

Other plugins can add providers for their own plots or claims through the API (`CompaniesApi.areas().register(...)`);
their id is what `providerOrder` and `providers` name.

Areas of a provider that is not there, for example because its plugin was removed, stay stored and come back when it
is there again.
