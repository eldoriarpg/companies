---
title: Repeating orders
sidebar_position: 2
---

# Repeating orders

Do you need the same items again and again? A repeating order posts itself every day or every week. Each posted order
is called a copy. You set it up once, and Companies places the copies for you.

To companies, a copy looks like any other order. Nothing shows them that it repeats.

## Set up a repeating order

On the [dashboard](./dashboard.md#placing-an-order), switch on **Repeat this order** in the order builder. Choose
**Every** day or week, and when it **Ends**: after a number of copies, or on a last day. The cost box shows what each
copy costs. **Place order** starts it.

In game, build the order with `/order create` as described in [Orders](./orders.md) and, before you click `Done`, click
`Repeat` in the dialog. There you choose the interval and the number of copies, the last day, or both; with both, the
order stops at whichever comes first. In chat, `/order draft repeat daily 10` or
`/order draft repeat weekly until 2026-12-24` does the same; the [Commands](../reference/commands.md) page has the
rest. The last day counts as a whole day and has to be after today.

:::note
Your server sets the limits. By default you can have 3 active repeating orders, each with up to 52 copies, and the last
day can be at most 365 days ahead. If your server does not allow repeating orders, or not for you, the `Repeat` button
is missing in game, and the dashboard refuses the order with the reason.
:::

## How copies are posted

- The **first copy** is posted at once when you place the order.
- The **next copies** follow every day or every week at the same time of day. They can be a few minutes late.
- **Each copy is paid when it is posted**, price and fee. You do not pay for all copies at the start.

A copy waits in two cases:

- **The previous copy is still open.** A new copy is only posted once a company claimed the previous one. You are not
  told about this.
- **You reached your order limit.** You get one message when this holds a copy back. The copy is posted once you have
  room again.

Copies that were missed while it waited are skipped. They are not posted later all at once. The order still repeats on
its original schedule.

You get a message in your [inbox](./inbox.md) every time a copy is posted, with what it cost. You also get one when the
last copy is posted.

## Your repeating orders

On the dashboard, the copies show under *Your orders* like any other order. Listing, stopping and resuming repeating
orders happens in game:

- `/order series` lists your repeating orders, and `/order series info <id>` shows one with its lines, what each copy
  costs, how it repeats, its state and its current copy.
- `/order series stop <id>` stops one, `/order series resume <id>` lets a stopped one post again.

In `/order list` the copies of a repeating order are marked with ⟳. `/order info` of a copy links to its repeating
order.

## What stops a repeating order

A repeating order stops in these cases:

| Reason | What happened |
|---|---|
| Stopped by you | You used `/order series stop` or the `Stop` button. |
| A copy could not be paid | You did not have enough money when a copy was due. |
| A copy was cancelled | You cancelled a copy on the dashboard or with `/order cancel`. |
| An admin removed a copy | An admin cancelled one of the copies. |
| A copy breaks the current order rules | The first copy could not be posted, for example because the server lowered its limits. |

When it stops on its own or because of an admin, you get a message in your inbox. `/order series info` shows the reason.

A later copy that breaks the order rules, or that another plugin prevents, is skipped instead. Its turn counts as
used, and the order goes on with the next one.

:::tip
Make sure you have enough money for the next copy. A copy you cannot pay stops the whole repeating order.
:::

### Stopping

`/order series stop <id>` asks you to confirm. Confirm with the button or `/order series stop confirm`. The current copy
stays posted. Only new copies are no longer made.

### Cancelling a copy

Cancelling a copy, on the dashboard or with `/order cancel <id>`, cancels it and stops the whole repeating order.
Companies asks you first and says that the repeating order stops with it.

### Resuming

`/order series resume <id>` starts a stopped repeating order again. The next copy is due at once. Resuming works only
if you are still allowed to make orders repeat, the repeating order has not reached its end, and you are below the
limit of active repeating orders.

A repeating order that posted its last copy is finished. It cannot be resumed.
