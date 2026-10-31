---
title: areas.yml
sidebar_position: 8
---

# areas.yml

Company areas: plots and regions a company uses. This file says whether companies
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
```

| Key | Values | Default | Description | Takes effect |
|-----|--------|---------|-------------|--------------|
| `enabled` | true/false | `true` | Companies may get new areas. Areas they have keep working either way. | reload |
| `providerOrder` | list of provider ids | `plotsquared`, `worldguard` | The order the area plugins are asked which area holds a spot; the first that knows one wins. Providers not listed come after the listed ones. | reload |
| `providers` | provider id: true/false | both `true` | Switch an area plugin off for company areas. A provider not listed is on. | reload |

## Area plugins

Companies brings providers for **PlotSquared** (a plot, merged plots counting as one) and **WorldGuard** (the region
with the highest priority at a spot, without the global region and the regions of jobs). They load when the plugin is
enabled and `hooks.plotsquared` or `hooks.worldguard` in [config.yml](./config-yml.md#generalsettings) is on. Other plugins can
add providers for their own plots or claims through the API (`CompaniesApi.areas().register(...)`); their id is what
`providerOrder` and `providers` name.

Areas of a provider that is not there, for example because its plugin was removed, stay stored and come back when it
is there again.
