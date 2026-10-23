---
title: Repeating orders
sidebar_position: 2
---

# Repeating orders

Do you need the same items again and again? A repeating order posts itself every day or every week. Each posted order
is called a copy. You set it up once, and Companies places the copies for you.

To companies, a copy looks like any other order. Nothing shows them that it repeats.

## Set up a repeating order

Build the order as usual with `/order create`, as described in [Orders](./orders.md). Before you click `Done`, make
it repeat.

**In the dialog**, click `Repeat`. Choose the interval, `Daily` or `Weekly`. Then enter the number of copies, the
last day, or both. With both, the order stops at whichever comes first.

**In chat or with commands**, use one of these:

| Command | What it does |
|---|---|
| `/order draft repeat daily 10` | Post the order every day, 10 times in all |
| `/order draft repeat weekly 4` | Post it every week, 4 times in all |
| `/order draft repeat weekly until 2026-12-24` | Post it every week until the 24th of December 2026 |
| `/order draft repeat off` | Make it a one-off order again |

The last day is written as `yyyy-MM-dd` and counts as a whole day. It has to be after today.

The builder now shows how the order repeats and what each copy costs. Click `Done` to start it.

:::note
Your server sets the limits. By default you can have 3 active repeating orders, each with up to 52 copies, and the
last day can be at most 365 days ahead. If your server does not allow repeating orders, or not for you, the `Repeat` button is
missing.
:::

## How copies are posted

- The **first copy** is posted at once when you click `Done`.
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

| Command | What it does |
|---|---|
| `/order series` | List your repeating orders |
| `/order series info <id>` | Show one: its lines, what each copy costs, how it repeats, its state and its current copy |
| `/order series stop <id>` | Stop it |
| `/order series resume <id>` | Let a stopped one post again |

In `/order list` the copies of a repeating order are marked with ⟳. `/order info` of a copy links to its repeating
order.

## What stops a repeating order

A repeating order stops in these cases:

| Reason | What happened |
|---|---|
| Stopped by you | You used `/order series stop` or the `Stop` button. |
| A copy could not be paid | You did not have enough money when a copy was due. |
| A copy was cancelled | You cancelled a copy with `/order cancel`. |
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

`/order cancel <id>` on a copy cancels it and stops the whole repeating order. Companies asks you first. Confirm with
the button or `/order cancel confirm`.

### Resuming

`/order series resume <id>` starts a stopped repeating order again. The next copy is due at once. Resuming works only
if you are still allowed to make orders repeat, the repeating order has not reached its end, and you are below the
limit of active repeating orders.

A repeating order that posted its last copy is finished. It cannot be resumed.
