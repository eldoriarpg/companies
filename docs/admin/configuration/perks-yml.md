---
title: perks.yml
sidebar_position: 10
---

# perks.yml

The [perks](../../companies/perks.md) companies can buy. A perk is offered once it is registered, by a feature that
needs it or by a plugin built on Companies, with the price and minimum level it was registered with. This file changes that per perk: its
price, its level, or whether it is offered at all. It is shared by every server of a network.

```yaml
perks:
  fast-delivery:
    enabled: true
    price: 2500.0
    minLevel: 2
```

Each key under `perks` is the id of a perk, as `/company perks buy` takes it.

| Key | Values | Default | Description | Takes effect |
|-----|--------|---------|-------------|--------------|
| `enabled` | true/false | `true` | Whether companies can buy and use the perk. Switched off, companies that bought it keep it, paused, and use it again once it is switched on. | reload |
| `price` | money, 0 or more | the perk's own | What the perk costs, paid once from the company's treasury. A perk costing 0 is free. | reload |
| `minLevel` | company level, 1 or more | the perk's own | The level from which a company can buy the perk and use it. A company that falls below it keeps the perk, paused. | reload |

A perk that is not listed is offered with its own price and level. A value left out keeps the perk's own value.
Changing a price never touches perks companies bought already.
