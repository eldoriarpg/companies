---
title: Events
sidebar_position: 2
---

# Events

Companies fires Bukkit events for everything that happens to companies, orders and treasuries. Listen to them like
to any other Bukkit event. Add the API to your plugin first, see [API](./api.md).

```java
public final class OrderLogger implements Listener {
    private final Logger logger;

    public OrderLogger(Logger logger) {
        this.logger = logger;
    }

    @EventHandler
    public void onOrderCreated(OrderCreatedEvent event) {
        logger.info("Order " + event.order().id() + " created from " + event.source());
    }
}
```

The events live in three packages:

- `de.eldoria.companies.events.company` for companies and treasuries
- `de.eldoria.companies.events.order` for orders
- `de.eldoria.companies.events.player` for items that return to a player

## Threads

Most events are fired **off the main thread**. Code in such a listener must not call Bukkit methods that need the main
thread. Hop over with the scheduler, for example `Bukkit.getScheduler().runTask(plugin, () -> ...)`.

There are three kinds:

- **Main thread.** The event is synchronous. Only `PreOrderCreateEvent` is always fired on the main thread.
- **Async.** The event is always asynchronous and is fired off the main thread.
- **Either.** The event is asynchronous when it is fired off the main thread and synchronous on the main thread. The
  tables say where it usually happens. Check `event.isAsynchronous()` when it matters to you.

The base classes `CompanyEvent` and `OrderEvent` have no handler list. Listen to the events below, not to the bases.

## Pre-events and cancel reasons

Three events fire **before** an action and can stop it:

- `PreCompanyCreateEvent`: a player is about to found a company.
- `PreOrderCreateEvent`: an order is about to be created.
- `PreOrderAcceptEvent`: a member is about to claim an order for their company.

They implement `CancellableWithReason`. Cancel them in one of two ways:

- `event.cancel(Component reason)` stops the action. The player who triggered it gets your reason as a chat message,
  exactly as you built it.
- `event.setCancelled(true)` stops the action without a reason. The player sees the generic message
  "Another plugin prevented this."

`setCancelled(false)` lets the action go on again and drops your reason. `cancelReason()` returns the current reason.

When the action came from the API, no player sees the reason. The calling plugin gets it instead, in the message of
the `Rejected` result. See [Create an order](./api.md#create-an-order).

```java
@EventHandler
public void onPreOrderAccept(PreOrderAcceptEvent event) {
    if (isInJail(event.player())) {
        event.cancel(Component.text("You cannot claim orders while you are in jail.", NamedTextColor.RED));
    }
}
```

### Change an order before it is created

`PreOrderCreateEvent` fires on the main thread, before the owner is charged. Its `draft()` can be changed: rename it,
change, remove or add lines. `source()` tells where the order comes from:

| `OrderSource` | The order comes from                                         |
|---------------|--------------------------------------------------------------|
| `PLAYER`      | A player with `/order create`                                |
| `API`         | A plugin with `orders().create(...)`                         |
| `REPEATING`   | A copy posted by a [repeating order](../players/repeating-orders.md). The owner pays for it, but the limits of the owner do not apply |

```java
@EventHandler
public void onPreOrderCreate(PreOrderCreateEvent event) {
    for (IDraftLine line : event.draft().lines()) {
        if (line.template().getType() == Material.BEDROCK) line.remove();
    }
}
```

Before the event, Companies checks the order against its rules. An order that breaks one is rejected right away, and
the event does not fire.

After all listeners ran, Companies checks the order again. The fee is computed from the changed price. If your
changes broke a rule, or left the order without lines, the order is not created and nothing is charged. The player
sees "Another plugin changed the order so that it breaks the order rules. Nothing was charged." An API call gets the
reason `INVALID`.

## Company events

| Event                       | When                                                                                                   | Thread                   | Cancellable | Key methods |
|-----------------------------|--------------------------------------------------------------------------------------------------------|--------------------------|-------------|-------------|
| `PreCompanyCreateEvent`     | A player confirmed founding a company. Before the founding price is charged                             | Either, usually main     | With reason | `player()`, `name()`, `price()` |
| `CompanyCreateEvent`        | A company was founded and saved                                                                         | Either, usually off main | No          | `company()`, `player()` (the founder) |
| `CompanyRenameEvent`        | A company was renamed, by its owner or an admin                                                         | Either, usually off main | No          | `oldName()`, `newName()`, `actor()`, `byAdmin()` |
| `CompanyOwnerTransferEvent` | The ownership passed to another member, by the owner or an admin                                        | Either, usually off main | No          | `previousOwner()`, `newOwner()`, `byAdmin()` |
| `CompanyProfileEditEvent`   | A member changed the description, tag, colour or join mode                                              | Either                   | No          | `previous()` (the profile before), `company()` (after), `player()` |
| `CompanyJoinEvent`          | A player joined: open join, accepted invite or application, or `addMember` of the API                   | Either, usually off main | No          | `player()` |
| `CompanyLeaveEvent`         | A member left the company, or was kicked                                                                | Async                    | No          | `player()` |
| `CompanyKickEvent`          | A member was kicked by another member                                                                   | Async                    | No          | `player()` (the kicked member) |
| `CompanyLevelUpEvent`       | The level of a company went up                                                                          | Async                    | No          | `oldLevel()`, `newLevel()` |
| `CompanyLevelDownEvent`     | The level of a company went down                                                                        | Async                    | No          | `oldLevel()`, `newLevel()` |
| `CompanyDisbandEvent`       | A company was disbanded. Its orders were released and its treasury was emptied                          | Async                    | No          | `company()` |
| `TreasuryTransactionEvent`  | Money moved into or out of a treasury, after it was saved                                               | Either, usually off main | No          | `kind()`, `amount()`, `balance()`, `actor()` |

Every company event also has `company()`.

**Leaving and kicking.** `CompanyKickEvent` extends `CompanyLeaveEvent`. A listener of `CompanyLeaveEvent` gets kicks
too. Check `event instanceof CompanyKickEvent` to tell them apart, or listen to `CompanyKickEvent` for kicks only.

**Levels.** Listen to `CompanyLevelUpEvent` and `CompanyLevelDownEvent`. Their parent `CompanyLevelChangeEvent` is never
fired, and a listener of it receives nothing. To react to both directions, listen to both events.

**Renames.** `actor()` is the player who renamed the company. It is empty when the console did it.

**Treasury.** `amount()` is signed: positive when money came in, negative when it went out, 0 for a share change.
`balance()` is the balance after the transaction. `actor()` is the member or admin who caused it. It is empty for
payout shares and system actions, so workers and owners stay anonymous. `kind()` tells why the money moved:

| `TreasuryKind`  | Meaning                                                   |
|-----------------|-----------------------------------------------------------|
| `DEPOSIT`       | A member paid money in                                    |
| `WITHDRAW`      | A member took money out                                   |
| `PAYOUT_SHARE`  | The company's share of a worker's payout                  |
| `RENAME`        | The owner paid a rename from the treasury                 |
| `REFUND`        | Money came back after a failed payment                    |
| `DISBAND`       | The company was disbanded or deleted. The balance went to the owner |
| `ADMIN_GIVE`    | An admin added money                                      |
| `ADMIN_TAKE`    | An admin took money                                       |
| `SHARE_CHANGED` | A member changed the payout share. No money moved         |

An exception thrown by a `TreasuryTransactionEvent` listener is caught and logged. It does not undo the transaction.

## Order events

| Event                 | When                                                                                     | Thread                   | Cancellable | Key methods |
|-----------------------|------------------------------------------------------------------------------------------|--------------------------|-------------|-------------|
| `PreOrderCreateEvent` | An order is about to be created, before the owner is charged                              | Main                     | With reason | `owner()`, `draft()`, `source()` |
| `OrderCreatedEvent`   | An order was created and saved                                                           | Either, usually off main | No          | `owner()`, `source()` |
| `PreOrderAcceptEvent` | A member is about to claim an order for their company                                    | Either, usually off main | With reason | `company()`, `player()` (the member) |
| `OrderAcceptEvent`    | A company claimed an order                                                               | Async                    | No          | `company()` |
| `OrderDeliveryEvent`  | A worker delivered at least one item to an order                                         | Either, usually off main | No          | `company()`, `player()` (the worker), `deliveries()` |
| `OrderDoneEvent`      | All items are delivered. The order is now `DELIVERED`. Fired before the workers are paid  | Async                    | No          | `company()` |
| `OrderPaymentEvent`   | A worker is about to be paid their share of a completed order. Once per worker           | Async                    | Yes         | `company()`, `player()`, `amount()`, `gross()`, `tax()`, `share()` |
| `OrderReceivedEvent`  | The owner received the items. The order is now `RECEIVED`                                | Either, usually off main | No          | `company()` |
| `OrderCanceledEvent`  | A company gave a claimed order back. It is open again for other companies                | Async                    | No          | `company()` |
| `OrderExpiredEvent`   | A company did not deliver a claimed order in time. It is open again for other companies  | Async                    | No          | `company()` |
| `OrderRemovedEvent`   | An order was deleted before it was delivered                                             | Async                    | No          | `reason()` |

Every order event also has `order()` and `owner()`, the player who placed the order.

**Payments.** The worker gets `amount()`. That is the `gross()` share of the order price, minus the `tax()`, minus
the `share()` the company keeps in its treasury. Cancel the event (plain `setCancelled(true)`) and the worker is not
paid, not taxed and nothing goes to the treasury.

**Removed orders.** `reason()` tells why the order was deleted:

| `OrderRemovedEvent.Reason` | Meaning                                                                     |
|----------------------------|-----------------------------------------------------------------------------|
| `PURGED`                   | Nobody claimed the order in time. The owner got back what they paid        |
| `CANCELLED`                | The owner cancelled the unclaimed order                                     |
| `ADMIN`                    | An admin deleted the order                                                  |

A claimed order that the company gives back fires `OrderCanceledEvent`. One that runs out of time fires
`OrderExpiredEvent`.

## Returned items

`ItemsReturnedEvent` fires when items are put into a player's returned items, which they pick up with
`/order collect`. It fires after the items were stored. Thread: either, usually off main. It is not cancellable.

- `player()`: the player who gets the items.
- `stacks()`: the returned items. Each `ReturnedStack` has a `stack()` (one item) and an `amount()`.
- `order()`: the order the items came from, if there is one.
- `reason()`: why the items came back.

| `ItemsReturnedEvent.Reason` | Meaning                                                                                          |
|-----------------------------|--------------------------------------------------------------------------------------------------|
| `ORDER_ABORTED`             | The company gave the order back. The worker gets back what they delivered                        |
| `ORDER_EXPIRED`             | The company did not deliver in time. The worker gets back what they delivered                    |
| `COMPANY_DISBANDED`         | The company was disbanded while it worked on the order. The worker gets back what they delivered |
| `ADMIN_RELEASE`             | An admin cancelled the order or took it away from the company                                    |
| `DELIVERY_OVERFLOW`         | Items could not be handed over: the owner's inventory was full, or the worker went offline before the surplus of a delivery was given back |
| `ADMIN_GRANT`               | An admin granted the items with a command                                                        |
| `API`                       | A plugin granted the items with `players().grantItems(...)`                                      |

## Several servers

On a network, an event fires only on the server where the action happened. These run on the primary server only:

- `OrderExpiredEvent`
- `OrderRemovedEvent` with the reason `PURGED`
- `ItemsReturnedEvent` with the reason `ORDER_EXPIRED`

See [Several servers](../admin/multiserver.md) for how a network is set up.

## Keep orders anonymous

Companies keeps orders anonymous. Workers never learn who placed an order. Owners never learn which company works
on theirs.

The events show both sides: order events have the `owner()` and, once claimed, the `company()`. Use them for logic,
logging or staff tools. In your listeners:

- Never show the owner of an order to the worker or the other members of the company.
- Never show the company or the workers to the owner.
- When items return to a worker, never show who owned the order.

:::tip
Logging both sides for your staff is fine. Just keep that log away from players.
:::
