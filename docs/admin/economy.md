---
title: Economy
sidebar_position: 5
---

# Economy

Companies has no currency of its own. It uses your economy plugin through Vault: players pay for orders with their
normal money, and workers are paid with it.

## Vault and an economy plugin

You need [Vault](https://github.com/MilkBowl/Vault) and an economy plugin that works with Vault, such as EssentialsX.
Companies looks for the economy shortly after the server started, so it does not matter which plugin loads first.

If no economy is found, Companies does not work. The console shows "No Vault economy provider is registered. Install
an economy plugin that supports Vault." and commands answer "The plugin is still starting. Try again in a moment."
Install an economy plugin and restart.

Amounts are shown in the format of your economy plugin, with its currency name.

## Where money goes

| Action | Who pays | Who gets it |
|---|---|---|
| Placing an order | The player pays the price plus the fee | The order holds the money |
| Raising the price | The player pays the increase plus the fee on it | The order holds the money |
| Completing an order | The order | Workers get their share minus tax and treasury share. Tax and fee go to the collector. The treasury share goes to the company treasury |
| Cancelling an order (`/order cancel`) | The order | The owner gets the price back. The fee goes to the collector |
| Order removed because nobody claimed it | The order | The owner gets price and fee back |
| Founding a company | The founder pays `foundingPrice` | Nobody. The money leaves the economy |
| Renaming a company | The owner pays `renamePrice`, from the wallet or the treasury | Nobody. The money leaves the economy |
| Disbanding a company | The treasury | The owner |
| Admin cancels an order with `keep` | The order | Price and fee go to the collector. The owner gets nothing back |

The other admin actions have their own rules. See [Settling orders](./admin-commands.md#settling-orders).

Orders that other plugins create through the [API](../developers/api.md) can be paid by the server instead. Then
nothing is taken from the player and the order has no fee.

## Prices

The price of an order is the sum of its lines: amount times unit price. The player sets the unit prices in the order
builder, or takes the market price, an average price for that item.

Players can raise the price of an order nobody has claimed yet. See [Placing orders](../players/orders.md).

`company.yml` sets the prices for companies:

```yaml
foundingPrice: 20000.0
renamePrice: 10000.0
```

A `renamePrice` of `0` or less makes renaming free.

## Fees

The fee is charged on top of the price when an order is placed. It is set in `order.yml`:

```yaml
fee:
  fixed: 0.0
  percent: 0.0
  min: 0.0
  max: 0.0
```

The fee is `fixed` plus `percent` of the price (`5` means 5 %). If `min` is above 0, the fee is at least `min`. If
`max` is above 0, the fee is at most `max`. The result is rounded down to whole cents and is never negative. With
the defaults, there is no fee.

When a player raises the price, they pay `percent` of the increase. `min` and `max` apply to the order's total fee.
The fixed part is not charged again.

The fee stays with the order until the order ends. Then it goes to the collector, or back to the owner if the order
was removed because nobody claimed it.

## Tax

The tax is taken from what workers earn when an order is completed. Set it in `company.yml`:

```yaml
tax:
  percent: 0.0
```

`5` means 5 %. A level can set its own `taxPercent`, which replaces the global value for companies of that level. See
[Levels](./levels.md). Workers see the tax in their payment notification.

## The collector account

Fees and taxes go to the collector account. So does the price of an order an admin cancels with `keep`. Set the
account in `config.yml`:

```yaml
economy:
  collectorAccount: ''
```

- **Empty:** the money leaves the economy. This is the default.
- **A player UUID:** that player gets the money.
- **Any other text:** it is passed to your economy plugin as an account name. Many economy plugins use this for
  server or bank accounts.

If the economy refuses the deposit, the console logs a warning with the amount and the account.

## Company treasury

Every company has a treasury. Members pay into it, and members with the `TREASURY` permission take money out. The
balance is stored by Companies in its database, not in your economy plugin.

Companies can also keep a part of every payout: the **payout share**. The company sets it with
`/company bank share <percent>`, up to `treasury.maxPayoutShare` (default `25`). When you lower the cap, it applies
to every company at once.

```yaml
treasury:
  enabled: true
  maxPayoutShare: 25
  minTransaction: 1.0
  payRename: true
```

`treasury.enabled: false` hides the bank commands and takes no payout share. Balances are kept.

When a company is disbanded, or an admin deletes it, the owner gets the balance. Some economy plugins cannot pay
players who are offline. Then the amount is logged as lost.

For players' view of the treasury, see [Treasury](../companies/treasury.md). All `treasury` keys are on the
[company.yml](./configuration/company-yml.md) page.

## How a payout is split

When an order is completed, each worker earns the price of what they delivered. For each line, a worker gets the
line price times the part of the line they delivered. Then:

1. **Tax.** The amount after tax is rounded down to whole cents. The tax is the rest.
2. **Treasury share.** The payout share is taken from the amount after tax and rounded down to whole cents.
3. **Net.** The worker gets what is left.

Tax, share and net always add up to what the worker earned. No money is created by rounding.

**Example:** a worker earns 100.00, the tax is 5 % and the payout share is 10 %.

| Step | Amount |
|---|---|
| Earned | 100.00 |
| Tax (5 %) | 5.00 |
| After tax | 95.00 |
| Treasury share (10 % of 95.00) | 9.50 |
| Paid to the worker | 85.50 |

Other plugins can stop a single payment with the `OrderPaymentEvent`. A stopped payment is not paid, taxed or shared.
See [Events](../developers/events.md).
