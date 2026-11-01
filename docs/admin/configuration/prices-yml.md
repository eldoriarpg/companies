---
title: prices.yml
sidebar_position: 9
---

# prices.yml

Prices from the shop plugins of the server. Companies only knows what a material costs once players delivered orders
for it. On a new server, or for a material nobody ordered yet, it reads what the server's shops charge and pay and
works out a **shop price** per material from it. The order builder offers that price, and
[system orders](./system-orders-yml.md) priced by the market average use it, until enough orders were delivered. This
file is shared by every server of a network.

```yaml
sources: {}
adminOnly: []
sampleMinutes: 30
days: 14
minTrades: 5
minListings: 3
sellFactor: 1.0
buyFactor: 1.0
```

| Key | Values | Default | Description | Takes effect |
|-----|--------|---------|-------------|--------------|
| `sources` | source id: true/false | empty | Switch a shop plugin off as a source of prices. A source not listed is on. | reload |
| `adminOnly` | list of source ids | empty | Sources whose player shops do not count: only admin shops and the prices the server sets count from them. | reload |
| `sampleMinutes` | number (minutes) | `30` | How often each server reads the offers of its shops. At least 1. | reload |
| `days` | number (days) | `14` | How many days of trades count, and how old a read of the offers may be. At least 1. | reload |
| `minTrades` | number | `5` | How many trades of a material the shop price needs before it goes by trades. At least 1. | reload |
| `minListings` | number | `3` | How many offers of player shops a material needs before the shop price goes by them, when there are too few trades. At least 1. | reload |
| `sellFactor` | number | `1.0` | Multiplies the price shops pay players when it is the only side known. | reload |
| `buyFactor` | number | `1.0` | Multiplies the price players pay shops when it is the only side known. | reload |

## How the shop price is worked out

Shops know three kinds of price, and Companies trusts them in this order:

1. **Trades**: money that changed hands in a shop. The shop price takes the median of the daily averages of the last
   `days` days, each day counting with its number of trades, once there are at least `minTrades`.
2. **Offers of player shops**: what players ask for or pay in their shops right now, once there are at least
   `minListings`.
3. **Server prices**: what admins set, in GUI shops, admin signs and admin NPCs.

This is done for each side: what players get for selling to a shop and what they pay for buying from one. The shop
price lies in the middle of the two sides. When only one side is known, it is that side times `sellFactor` or
`buyFactor`. Where the server prices a material, the shop price never falls below the server's sell price nor rises
above its buy price, however player shops trade it.

Only plain items count: a named, enchanted or otherwise changed item says nothing about its material. Shop prices name
no player.

## Shop price or orders

As soon as a material has `market.minOrders` delivered orders (in [order.yml](./order-yml.md)), Companies goes by the
average of its orders again. Below that, the shop price fills in. A material no shop knows uses its orders however few
there are.

The shop prices are worked out on the primary server about once an hour, together with the market prices. Each server
reads the shops it runs itself, so in a network the shops of every server count.

## Shop plugins

Other plugins can add sources for their own shops through the API (`CompaniesApi.prices().register(...)`); their id is
what `sources` and `adminOnly` name. An id is letters, digits, `_`, `-` and `.`, at most 64 characters, and is written in lower case.

The **Servers** tab of the [dashboard](../dashboard.md) lists every source with what it gave: offers, trades and the
shop prices it was used for. It needs `companies.admin.nodes`.
