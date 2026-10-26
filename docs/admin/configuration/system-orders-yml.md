---
title: system-orders.yml
sidebar_position: 5
---

# system-orders.yml

Orders the server places itself. Each rule orders one item again and again, at a price and with money from a source
you choose. Guessed orders pick items and prices from the market on their own, to give companies work when players
order little. Companies deliver system orders like any order. The items are removed when the order is complete.

In a network, this file is shared. Edit it on the primary server. See [Configuration](./index.md#in-a-network). Only
the primary server places and removes system orders.

System orders are off by default.

## Default file

```yaml
enabled: false
checkMinutes: 5
mintedDailyCap: 10000.0
rules: []
guessed:
  enabled: false
  historyDays: 14
  recentDays: 3
  minOrders: 3
  allow: []
  deny: []
  competition: SKIP
  margin: 0.3
  factor: 0.8
  maxFactor: 1.0
  jitter: 0.05
  minPrice: 0.0
  maxPrice: 0.0
  sizeShare: 0.5
  maxItems: 512
  repriceAfterHours: 2
  repriceStep: 0.05
  maxReprices: 3
  restHours: 24
  share: 0.2
  minOpen: 3
  maxOpen: 10
  perCompanyDaily: 5
  source:
    type: MINTED
    account: ""
    dailyBudget: 0.0
  learning:
    enabled: true
    days: 30
    fastMinutes: 30
    step: 0.05
    minFactor: 0.5
    weightFloor: 0.1
```

## Keys

| Key | Type | Default | Meaning | Applies |
|---|---|---|---|---|
| `enabled` | true/false | `false` | Whether the server places system orders. When off, open system orders still run out at the end of their lifetime. | reload |
| `checkMinutes` | number (minutes) | `5` | How often the primary server places due orders and removes the ones past their lifetime. At least 1. | restart |
| `mintedDailyCap` | number | `10000.0` | How much new money all rules together may put into orders per day. Orders removed again do not count. `0` allows no new money. | reload |
| `rules` | list | empty | The rules, see below. | reload |
| `guessed` | section | off | Orders the server guesses, see [Guessed orders](#guessed-orders). | reload |

A day is a day of the server's calendar, in the time zone of `generalSettings.timezone` in
[config.yml](./config-yml.md#generalsettings).

## Rules

```yaml
rules:
  - id: iron-weekly
    name: "Royal smithy"
    material: IRON_INGOT
    amount: 256
    price:
      mode: AVERAGE
      factor: 1.1
      jitter: 0.05
      min: 2.0
      max: 6.0
    everyHours: 24
    lifetimeHours: 12
    maxOpen: 1
    source:
      type: ACCOUNT
      account: "Collector"
      dailyBudget: 5000
```

| Key | Type | Default | Meaning |
|---|---|---|---|
| `id` | text | none | Names the rule. Each order remembers it. Use each id once, at most 64 characters. A rule without an id, with the id of an earlier rule, or with the id `guessed`, which guessed orders use, places nothing. |
| `name` | text | the id | The name of the orders. |
| `enabled` | true/false | `true` | Whether the rule places orders. |
| `material` | material | none | The item ordered, for example `IRON_INGOT`. |
| `amount` | number | `64` | How many items one order asks for. |
| `price.mode` | `FIXED` or `AVERAGE` | `AVERAGE` | `FIXED` pays `price.value` per item. `AVERAGE` pays the market average per item times `price.factor`. |
| `price.value` | number | `1.0` | Price per item for `FIXED`. |
| `price.factor` | number | `1.0` | Factor on the market average for `AVERAGE`. |
| `price.jitter` | number | `0.0` | How far the price moves at random either way. `0.05` is up to 5 % less or more. At most `0.9`. |
| `price.min` | number | `0.0` | Lowest price per item. |
| `price.max` | number | `0.0` | Highest price per item. `0` means no highest price. |
| `everyHours` | number (hours) | `24` | How long after one order the next one is placed. |
| `lifetimeHours` | number (hours) | `12` | How long an order waits for a company, counted from when it was placed, last extended with `/companyadmin order extend`, or last given up by a company. After that it is removed and its money goes back to the source. `maxUnclaimedHours` of [order.yml](./order-yml.md) still applies and removes it earlier if it is shorter. |
| `maxOpen` | number | `1` | How many orders of the rule may be open or in work at once. |
| `source.type` | `ACCOUNT` or `MINTED` | `MINTED` | Where the money comes from, see below. |
| `source.account` | text | none | The economy account for `ACCOUNT`: a player's UUID, or an account name your economy plugin knows. |
| `source.dailyBudget` | number | `0.0` | How much the rule may put into orders per day. `0` means no budget of its own. Orders removed again, for example after their lifetime, give their share back. |

A rule places its next order when `everyHours` passed since its last one and it has fewer than `maxOpen` orders open
or in work. The order is placed only when its price fits the budget of the day, and:

- with `ACCOUNT`, the account pays the price at once. If a plugin listening to new orders changes the price, the account
  pays the difference or gets it back. A raise the account cannot pay, or that goes beyond the rule's budget,
  removes the order again. When the order is removed, cancelled by an admin or delivered
  with items missing, that money goes back to the same account. An account that cannot pay places nothing.
- with `MINTED`, nothing is paid when the order is placed. The workers are paid with new money. All minted rules
  together stay within `mintedDailyCap`. A price raised by a plugin beyond the budget or the cap removes the
  order again. Minted money that is not spent goes nowhere.

With `AVERAGE`, a material that nobody delivered lately has no market average, and the rule waits until it has one.

No fee is charged on system orders. Players see them like any order, with the rule's name as the order name.
Deliveries count for company levels and rankings like any order. They are left out of the market prices, the market
list of the dashboard and its price history, so the server's own orders never move the averages they are priced by.

When a rule places nothing, the server log says why, once until the reason changes. A rule without a usable id is
named by its position in the list, for example `#3`.

## Guessed orders

With `guessed.enabled`, the server also places orders it picks itself from what players delivered lately. They fill
gaps and never compete with players.

1. **Candidates** are the materials with at least `minOrders` delivered player orders in the last `historyDays`, plus
   those in `allow`, minus those in `deny`. Each gets a demand score: the items delivered per day over the last
   `recentDays` against the items per day over all `historyDays`. The material players buy least lately comes first.
2. **Competition with players:**
   - `SKIP`: no guessed order for a material players have an open order for. A guessed order is withdrawn as soon
     as a player orders its material.
   - `UNDERBID`: guessed orders are allowed, but pay at most `1 - margin` of the lowest open player price and of the
     market average. A guessed order that pays more once a player orders cheaper is withdrawn. When this cap lies
     below `minPrice`, the material is not guessed.
     An order whose price the cap already holds down is not repriced, since a higher step could not pay more; its
     material rests instead.
3. **Price** is the market average times `factor`, moved by `jitter`, within `minPrice` and `maxPrice`. A material
   without a market average is not guessed.
4. **Size** is `sizeShare` of the items a delivered order of the material usually has, in whole stacks, at most
   `maxItems`.
5. **Repricing:** an order nobody took within `repriceAfterHours` is replaced by one paying `repriceStep` more of the
   average, up to `maxFactor`. After `maxReprices` the order is removed and the material rests for `restHours`. A
   replacement that finds no free slot, or cannot be placed for a reason other than money, lets its material rest
   as well. One that money stopped may be placed later as a new order, starting again at the first price step.
6. **How many:** guessed orders make up at most `share` of all open orders, but `minOpen` stay open even when players
   order nothing, and never more than `maxOpen`. One guessed order per material at a time. When the budget, the
   minted cap or the account stops one guessed order, for example because it lacks the money or does not exist, a
   cheaper one may still be placed. After three such stops, or without an account set, no further guessed order is
   placed in that check.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `enabled` | true/false | `false` | Whether the server places guessed orders. When off, open ones are removed when they would be repriced. |
| `historyDays` | number (days) | `14` | How far back deliveries count. At least 2. |
| `recentDays` | number (days) | `3` | The last days that count as recent. |
| `minOrders` | number | `3` | Delivered player orders a material needs to be guessed. |
| `allow` | list of materials | empty | Materials guessed even with fewer orders, as long as they have a market average. Write them as material names, for example `IRON_INGOT`; an entry that names no item is logged. |
| `deny` | list of materials | empty | Materials never guessed, written like `allow`. |
| `competition` | `SKIP` or `UNDERBID` | `SKIP` | How guessed orders keep out of the way of players. |
| `margin` | number | `0.3` | How much less an underbidding order pays, 0.05 to 0.95. |
| `factor` | number | `0.8` | Factor on the market average of a new guessed order. |
| `maxFactor` | number | `1.0` | Highest factor repricing reaches. |
| `jitter` | number | `0.05` | How far the price moves at random either way. |
| `minPrice` | number | `0.0` | Lowest price per item. |
| `maxPrice` | number | `0.0` | Highest price per item. `0` means none. |
| `sizeShare` | number | `0.5` | Share of the usual delivered amount one order asks for. |
| `maxItems` | number | `512` | Most items one order asks for. |
| `repriceAfterHours` | number (hours) | `2` | How long an order waits for a company before it is repriced, counted from when it was placed, last extended or last given up. |
| `repriceStep` | number | `0.05` | How much the factor rises per reprice. |
| `maxReprices` | number | `3` | Reprices before the order is removed. |
| `restHours` | number (hours) | `24` | How long a material is not guessed after its last reprice found nobody. |
| `share` | number | `0.2` | Share of all open orders guessed orders may make up, up to 0.9. |
| `minOpen` | number | `3` | Guessed orders kept open even when players order nothing. |
| `maxOpen` | number | `10` | Most guessed orders open at once. |
| `perCompanyDaily` | number | `5` | Guessed orders one company may take per day. It counts the guessed orders the company holds or finished that day. Two members accepting at the very same moment can go one past it. `0` means no limit. |
| `source` | section | minted | Where the money comes from, as `source` of a rule. Its `dailyBudget` counts all guessed orders together. |

Players working alone are not limited by `perCompanyDaily`.

### Learning

With `guessed.learning.enabled`, guessed orders learn from how their earlier orders ended. Nothing is trained: the
server counts outcomes and moves prices in small, bounded steps, so you can always read why an order was placed.

- An order counts as **taken** when a company or a player working alone accepts it. When nobody takes it, a copy at
  a higher price replaces it (**repriced**), and the last copy of such a chain counts as **expired**. An order that
  made way for players, or ran out while guessed orders were switched off, counts as **withdrawn**. Orders an admin
  cancelled or the order purge removed have no outcome.
- **Price:** a material's factor starts at `factor` and walks through its taken and expired orders of the last
  `days` in the order they ended. Each order taken within `fastMinutes` lowers it by `step`, since it paid more than
  needed; each expired one raises it by `step`. Slower takes keep it where it is, so a price that sells stays. The
  factor never leaves the range from `minFactor` (or `factor`, when that is lower) to `maxFactor`. Repricing adds
  its steps on top. An underbidding order whose cap held its price down does not move the factor, since a higher
  factor would not have paid more.
- **Choice:** instead of strictly by rank, materials are drawn at random. The weight is the take rate,
  `(taken + 1) / (taken + expired + 2)` and never below `weightFloor`, divided by `1 + score`. Materials players
  rarely buy and that companies take are guessed most, and every candidate is still tried now and then.
- An order taken and then given up, for example when its company missed the deadline, is counted by how it ends
  later instead.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `learning.enabled` | true/false | `true` | Whether guessed orders learn. When off, they are chosen by rank at `factor`. |
| `learning.days` | number (days) | `30` | How many days back guessed orders count for learning. Older ones are also gone once the order history purges them. |
| `learning.fastMinutes` | number (minutes) | `30` | An order taken within this counts as taken fast. |
| `learning.step` | number | `0.05` | How much one fast take or expiry moves the factor. |
| `learning.minFactor` | number | `0.5` | Lowest factor learning reaches. A lower `factor` lowers it further. |
| `learning.weightFloor` | number | `0.1` | Lowest take rate a material is weighted with, 0.01 to 1. |

## See why an order exists

Each system order keeps the numbers it was placed on: the rule, the price and how it was found, the jitter drawn, the
bounds and where the money came from. A guessed order also keeps why its material was picked: its demand score and
rank, the players' open orders it had to stay under, its size, whether it replaced an order nobody took, and what
learning knew of the material: its factor, weight and take rate. See [System orders](../admin-commands.md#system-orders) in the admin commands.
