---
title: Migrating from 1.x
sidebar_position: 3
---

# Migrating from 1.x

Companies 2.0 comes with a new API. This page lists what changed since 1.x and how to update your plugin.

The short version: in 1.x, `CompaniesApi.getInstance()` was not public, so plugins could not reach the API instance.
Most plugins only used the events. Those keep working, with a few changes listed below.

## Update the dependency

The coordinates stay the same: `de.eldoria:companies-api` from the EldoNexus repository. Change the version to
`2.0.0`. Keep it `compileOnly`. Companies brings the API with it.

```kotlin
repositories {
    maven("https://eldonexus.de/repository/maven-public/")
}

dependencies {
    compileOnly("de.eldoria", "companies-api", "2.0.0")
}
```

The API now needs **Java 25** to compile against, up from Java 21 in 1.x. Set the Java toolchain of your plugin to 25:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

The API is built against the Paper API `1.21.11`. Maven and Groovy snippets are on the [API](./api.md#add-the-dependency)
page.

## Get the API

The API is now an interface that Companies registers with Bukkit's services manager:

```java
CompaniesApi api = CompaniesApi.get();
// or
CompaniesApi api = getServer().getServicesManager().load(CompaniesApi.class);
```

Add `depend: [Companies]` or `softdepend: [Companies]` to your `plugin.yml`. See [Get the API](./api.md#get-the-api).

## What changed

| 1.x                                                           | 2.0                                                                         |
|---------------------------------------------------------------|-----------------------------------------------------------------------------|
| `CompaniesApi.getInstance()` (not public)                     | `CompaniesApi.get()` or `ServicesManager.load(CompaniesApi.class)`          |
| `getPlayerCompanyProfile(OfflinePlayer)`, `getPlayerCompany(OfflinePlayer)` | `companies().of(uuid)`                                        |
| `getCompanyStats(ISimpleCompany)`                             | `companies().stats(id)`                                                     |
| `getCompanyByName(name)`, `getCompanyById(id)`                | `companies().byName(name)`, `companies().byId(id)`, now an `ICompanyProfile` |
| `retrieveOrderById(id)`                                       | `orders().byId(id)`, now an `IFullOrder`                                    |
| `retrieveOrdersByCompany(...)`, `retrieveOrdersByPlayer(...)` | `orders().byCompany(...)`, `orders().byOwner(...)`, now `IFullOrder`s       |
| `retrieveMaterialPrice(Material)`                             | `orders().materialPrice(material)`                                          |
| Methods returned their values directly and blocked            | Every method returns a `CompletableFuture` that completes off the main thread |
| Company and player objects as arguments                       | Company ids (`int`) and player `UUID`s                                      |
| `OrderRemovedEvent` only when an unclaimed order was purged   | Also when the owner cancels or an admin deletes the order. Check `reason()` |
| `OrderPaymentEvent` with `player()` and `amount()`            | Also `company()`, `gross()`, `tax()` and `share()`. `amount()` is what the worker gets |
| Order events had `order()`                                    | All order events also have `owner()`                                        |
| `CompanyPermission`                                           | New constants `EDIT_PROFILE` and `TREASURY`. Exhaustive switches need a branch for them |
| `TopOrder` was not part of the API                            | `TopOrder` is in the API, with `ORDERS`, `EARNED`, `MEMBER`, `ITEMS_DELIVERED` and the new `LEVEL` |
| `ICompanyRank` with `rank()`                                  | Also `level()`, `tag()` and `color()`                                       |
| `ICompanyProfile`                                             | Also `description()`, `tag()`, `color()` and `joinMode()`                   |
| `ISimpleOrder`                                                | Also `fee()`                                                                |
| `IOrderContent`                                               | Lines with `line()`, `exact()`, `unitPrice()` and `deliveries()`. `IFullOrder.line(int)` finds a line. `IFullOrder.content(Material)` is deprecated |

### Orders have lines now

In 1.x an order asked for materials. In 2.0 an order has lines. A line is either a material line, which accepts every
item of the material, or an exact line, which accepts only items that match a template. An order can have several
lines of the same material, so `IFullOrder.content(Material)` cannot tell which one you mean. It is deprecated.

Go through `contents()` or ask for a line by its number:

```java
for (IOrderContent line : order.contents()) {
    getLogger().info("Line " + line.line() + ": " + line.delivered() + "/" + line.amount()
            + (line.exact() ? " exact " : " ") + line.material());
}

order.line(0).ifPresent(first -> getLogger().info("First line costs " + first.unitPrice() + " per item"));
```

### Futures instead of return values

Every method now returns a `CompletableFuture`. Never `join()` it on the main thread. Chain your code and hop back to
the main thread for Bukkit calls. See [Futures and threads](./api.md#futures-and-threads).

```java
api.companies().of(player.getUniqueId())
        .thenAcceptAsync(
                company -> company.ifPresent(c -> player.sendMessage("Member of " + c.name())),
                task -> Bukkit.getScheduler().runTask(plugin, task));
```

### Removed orders

A listener of `OrderRemovedEvent` that handled purged orders now also gets cancelled and deleted orders. Check the
reason:

```java
@EventHandler
public void onOrderRemoved(OrderRemovedEvent event) {
    if (event.reason() != OrderRemovedEvent.Reason.PURGED) return;
    getLogger().info("Nobody claimed order " + event.order().id());
}
```

### Payments

`amount()` of `OrderPaymentEvent` is what the worker gets after tax and after the company's treasury share. The
share before both is `gross()`.

## New in 2.0

These did not exist in 1.x. You do not need to change anything for them, but you may want to use them.

**API methods**

- `companies().byTag(...)`, `memberStats(...)` and `treasury(...)`
- `orders().create(...)` to create orders, see [Create an order](./api.md#create-an-order)
- `companies().addMember(...)` to add a member, see [Add a member](./api.md#add-a-member)
- `players().grantItems(...)` and `itemsWaiting(...)` for returned items
- `ranking().page(...)` and `rankOf(...)` for the company ranking

**Events**

- `PreCompanyCreateEvent`, `CompanyCreateEvent`, `CompanyRenameEvent`, `CompanyOwnerTransferEvent`,
  `CompanyProfileEditEvent`
- `TreasuryTransactionEvent`
- `PreOrderCreateEvent`, `OrderCreatedEvent`, `PreOrderAcceptEvent`, `OrderDeliveryEvent`, `OrderReceivedEvent`
- `ItemsReturnedEvent`

The pre-events can be cancelled with a reason that the player sees. See [Events](./events.md).

:::note Levels
In 1.x and in 2.0, a listener of `CompanyLevelChangeEvent` receives nothing. Listen to `CompanyLevelUpEvent` and
`CompanyLevelDownEvent`.
:::
