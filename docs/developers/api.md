---
title: API
sidebar_position: 1
---

# API

Companies has an API for other plugins. You can read companies, orders, rankings and player data. You can also
create orders, add members to a company and give items to players.

This page shows how to add the API to your plugin and how to use it. The events have their own page:
[Events](./events.md). Coming from Companies 1.x? Read [Migrating from 1.x](./migrating-from-1x.md).

## Add the dependency

The API is published to the EldoNexus repository as `de.eldoria:companies-api`. Companies brings the API classes
with it at runtime, so add it as `compileOnly` (Gradle) or `provided` (Maven). Do not shade it into your plugin.

**Gradle (Kotlin DSL)**

```kotlin
repositories {
    maven("https://eldonexus.de/repository/maven-public/")
}

dependencies {
    compileOnly("de.eldoria", "companies-api", "2.0.0")
}
```

**Gradle (Groovy DSL)**

```groovy
repositories {
    maven { url 'https://eldonexus.de/repository/maven-public/' }
}

dependencies {
    compileOnly 'de.eldoria:companies-api:2.0.0'
}
```

**Maven**

```xml
<repository>
    <id>EldoNexus</id>
    <url>https://eldonexus.de/repository/maven-public/</url>
</repository>

<dependency>
    <groupId>de.eldoria</groupId>
    <artifactId>companies-api</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

:::note Version
`2.0.0` is the version of the 2.0 release. It is available once that release is published. Until then, use the
snapshot version `2.0.0-SNAPSHOT`. It holds the latest build of the main branch.
:::

The API is compiled with **Java 25** and against the Paper API `1.21.11`. Your plugin needs Java 25 or newer to
compile against it. The jar comes with sources and Javadoc, so your IDE shows the documentation of every method.

You can also read the Javadoc online at [docs.eldoria.de/companies/javadoc](pathname:///companies/javadoc/).

## Declare Companies in your plugin.yml

The plugin is called `Companies`. Declare it so that your plugin enables after it:

```yaml
depend: [Companies]
```

If your plugin also works without Companies, use a soft dependency:

```yaml
softdepend: [Companies]
```

## Get the API

Companies registers the API with Bukkit's services manager while it enables. So you can get it in your own
`onEnable`:

```java
CompaniesApi api = CompaniesApi.get();
```

You can also load it from the services manager yourself:

```java
CompaniesApi api = getServer().getServicesManager().load(CompaniesApi.class);
```

`CompaniesApi.get()` throws an `IllegalStateException` when Companies is not enabled. The services manager returns
`null` instead. With `softdepend`, check that Companies is enabled first:

```java
if (getServer().getPluginManager().isPluginEnabled("Companies")) {
    CompaniesApi api = CompaniesApi.get();
}
```

Companies removes the API from the services manager when it disables. Do not keep the instance after that.

The API is split into four parts:

| Method         | Returns      | Use it for                                    |
|----------------|--------------|-----------------------------------------------|
| `companies()`  | `CompanyApi` | Companies, members, statistics and treasuries |
| `orders()`     | `OrderApi`   | Orders, material prices and creating orders   |
| `players()`    | `PlayerApi`  | Items that wait for a player                  |
| `ranking()`    | `RankingApi` | The company ranking of `/company top`         |

## Futures and threads

Every method of the four parts returns a `CompletableFuture`. The future completes **off the main thread**.

- Never call `join()` or `get()` on the main thread. It blocks the server.
- Most Bukkit calls need the main thread. Hop back to it with the scheduler before you touch players, worlds or
  inventories.

```java
api.companies().of(player.getUniqueId())
        .thenAcceptAsync(
                company -> company.ifPresent(c -> player.sendMessage("Member of " + c.name())),
                task -> Bukkit.getScheduler().runTask(plugin, task));
```

There are a few exceptions to "off the main thread":

- A write operation that is refused because Companies is not ready returns a future that has **already failed**.
  Code you chain to it runs right away on your own thread. See [Reads and writes](#reads-and-writes).
- Some invalid arguments throw right away, not through the future:
  `ranking().page(...)` throws an `IllegalArgumentException` when the page or size is below 1, and
  `players().grantItems(...)` throws one when the item is air or the amount is below 1.

Unknown ids, names and tags give an empty `Optional` or an empty list. They do not fail the future.

## Reads and writes

Reads work as soon as the API is registered, so already in your `onEnable`.

Write operations need the economy. Companies looks up the Vault economy one tick after it was enabled, once every
plugin is enabled. Writes open after that. Until then, write futures fail with an `ApiNotReadyException`. This
also happens while Companies runs without an economy, and after Companies was disabled.

These are the write operations:

- `companies().addMember(...)`
- `orders().create(...)`
- `players().grantItems(...)`

`ApiNotReadyException` extends `IllegalStateException`. For `addMember` and `create` the exception arrives wrapped in
a `CompletionException`. For `grantItems` it arrives as is. Unwrap it when you handle failures:

```java
api.orders().create(request).whenComplete((result, error) -> {
    if (error == null) return;
    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (cause instanceof ApiNotReadyException) {
        getLogger().warning("Companies is not ready to create orders yet.");
    } else {
        getLogger().log(Level.SEVERE, "Could not create the order", cause);
    }
});
```

:::tip
Want to write right when the server starts? Do not call a write operation in your `onEnable`. Schedule it from there
with a short delay instead, for example `Bukkit.getScheduler().runTaskLater(plugin, task, 20)`. If it still fails
with `ApiNotReadyException`, the server has no economy and Companies runs in its safe mode.
:::

## Several servers

All data lives in the shared database. On a network, every server reads the same state.

Events fire only on the server where the action happened. Expired orders and the removal of unclaimed orders are
handled on the primary server. See [Several servers](../admin/multiserver.md) for how a network is set up.

## Companies: `companies()`

| Method                                    | Returns                                          | What it gives you                                                                                     |
|-------------------------------------------|--------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `byId(int id)`                            | `CompletableFuture<Optional<? extends ICompanyProfile>>` | The company with this id                                                                      |
| `byName(String name)`                     | same                                             | The company with this name, in any case                                                               |
| `byTag(String tag)`                       | same                                             | The company with this tag, in any case                                                                |
| `of(UUID player)`                         | same                                             | The company the player is a member of                                                                 |
| `stats(int company)`                      | `CompletableFuture<Optional<? extends ICompanyStats>>` | All-time statistics: members, orders, money earned, items delivered                             |
| `memberStats(int company, TimeRange range)` | `CompletableFuture<List<? extends IMemberStats>>` | What each worker contributed in the time range                                                      |
| `treasury(int company)`                   | `CompletableFuture<Optional<? extends ITreasury>>` | Balance and payout share of the treasury                                                            |
| `addMember(int company, UUID player)`     | `CompletableFuture<JoinResult>`                  | **Write.** Adds the player to the company                                                             |

Some details:

- `memberStats` only lists workers with a completed order in the range. Former members who contributed are included.
  `TimeRange` is `ALL`, `LAST_30_DAYS` or `LAST_7_DAYS`.
- `treasury` gives a balance and payout share of 0 for a company that never used its treasury.
- An `ICompanyProfile` holds the members with their company permissions, the owner, the description, tag, colour
  and join mode.

### Add a member

`addMember` adds a player to a company. The join mode of the company does not matter. The member limit of the
company's level still applies.

- Open invites and applications of the player are removed.
- The player gets no permissions in the company.
- On success Companies fires a `CompanyJoinEvent`. Levels and announcements work as if the player joined on their own.

The future completes with a `JoinResult`:

| Result            | Meaning                                                    |
|-------------------|------------------------------------------------------------|
| `JOINED`          | The player is now a member                                 |
| `ALREADY_MEMBER`  | The player is already a member of a company (any company)  |
| `COMPANY_FULL`    | The company has reached the member limit of its level      |
| `UNKNOWN_COMPANY` | No company has this id                                     |
| `FAILED`          | The database refused the change. Companies logs the error  |

```java
api.companies().addMember(companyId, player.getUniqueId()).thenAccept(result -> {
    if (result != JoinResult.JOINED) getLogger().info("Could not add the player: " + result);
});
```

## Orders: `orders()`

| Method                                                | Returns                                          | What it gives you                                    |
|-------------------------------------------------------|--------------------------------------------------|------------------------------------------------------|
| `byId(int id)`                                        | `CompletableFuture<Optional<? extends IFullOrder>>` | The order with its lines                          |
| `byCompany(int company, OrderState min, OrderState max)` | `CompletableFuture<List<? extends IFullOrder>>` | Orders the company claimed, with a state from `min` to `max` |
| `byOwner(UUID owner, OrderState min, OrderState max)` | same                                             | Orders the player placed, with a state from `min` to `max` |
| `bySoloWorker(UUID worker, OrderState min, OrderState max)` | same                                       | Orders the player took to work on alone, with a state from `min` to `max` |
| `materialPrice(Material material)`                    | `CompletableFuture<? extends IMaterialPrice>`    | Average, lowest and highest price paid for the material. All 0 if it was never ordered |
| `create(OrderRequest request)`                        | `CompletableFuture<CreateOrderResult>`           | **Write.** Creates an order                          |

The order states are, in this order: `UNCLAIMED`, `CLAIMED`, `DELIVERED` and `RECEIVED`. To get every active order
of a player, ask for `UNCLAIMED` to `DELIVERED`:

```java
api.orders().byOwner(player.getUniqueId(), OrderState.UNCLAIMED, OrderState.DELIVERED)
        .thenAccept(orders -> getLogger().info(player.getName() + " has " + orders.size() + " active orders"));
```

An `IFullOrder` has its lines in `contents()`. Each line (`IOrderContent`) tells its item, its `kind()` (`MATERIAL`,
`EXACT` or `CRITERIA`), the amount, the unit price and what was delivered so far. A criteria line has its
`ItemCriteria` in `criteria()`; its `stack()` is only the first item of its set, for showing it.

An order is claimed by a company or by a single player working alone, never by both. `company()` is the id of the
company, or a value below 1 when no company claimed it. `soloWorker()` is the id of the player working on it alone,
or empty. See [Working alone](../players/working-alone.md) and the [solo order events](./events.md#solo-order-events).

### Create an order

`create` places an order the same way `/order create` does. Build the request with `OrderRequest.builder`:

```java
OrderRequest request = OrderRequest.builder(owner, "Castle stone")
        .line(OrderLineRequest.material(Material.STONE_BRICKS, 512, 2.5))
        .line(OrderLineRequest.exact(enchantedPickaxe, 1, 500))
        .line(OrderLineRequest.criteria(ItemCriteria.of("#minecraft:logs"), 256, 1.5))
        .charge(false)
        .respectLimits(false)
        .build();

api.orders().create(request).thenAccept(result -> {
    switch (result) {
        case CreateOrderResult.Created created -> getLogger().info("Order " + created.order().id() + " created");
        case CreateOrderResult.Rejected rejected -> getLogger().info("Rejected: " + rejected.reason());
    }
});
```

**The request**

| Builder call                       | Default | What it does                                                         |
|------------------------------------|---------|----------------------------------------------------------------------|
| `OrderRequest.builder(owner, name)` |        | The owner gets the items and may cancel the order. The name must not be blank |
| `.line(OrderLineRequest)`          |         | Adds a line. At least one line is required                           |
| `.charge(boolean)`                 | `true`  | Whether the owner pays for the order                                 |
| `.respectLimits(boolean)`          | `true`  | Whether the limits of a player apply                                 |
| `.build()`                         |         | Builds the request. Throws `IllegalArgumentException` for a blank name or no lines |

**The lines**

- `OrderLineRequest.material(material, amount, unitPrice)` accepts every item of the material.
- `OrderLineRequest.exact(template, amount, unitPrice)` accepts only items that match the template. The server's
  item matching settings decide what matches. The amount of the template stack is ignored.
- `OrderLineRequest.criteria(criteria, amount, unitPrice)` accepts every item meeting the criteria, in any mix: 256
  of `#minecraft:logs` may be oak and birch logs together.

`ItemCriteria` names materials and item tags, and may add enchantment conditions:

```java
ItemCriteria swords = ItemCriteria.of("DIAMOND_SWORD", "NETHERITE_SWORD")
        .withMinEnchantments(2)
        .withEnchantment("minecraft:sharpness", 3);
```

This accepts diamond and netherite swords with at least two enchantments, one of them Sharpness III or higher.
Materials are written as their name, tags with a leading `#`. Enchanted books count their stored enchantments. Other
data of an item does not matter.

The amount must be at least 1. The unit price must be a finite number and not negative. The item must be an item,
not air. A line that breaks one of these rules throws an `IllegalArgumentException` when you create it.

`request.price()` and `line.price()` give the sum of the line prices, without the fee.

**`charge`: who pays**

- `charge(true)`: the owner pays the price and the order fee, like with `/order create`. Companies takes the money on
  the main thread. An owner who cannot afford it gets the order rejected with `INSUFFICIENT_FUNDS`. If the order cannot
  be saved, the money is refunded. Cancelling and removing the order refund the owner as usual.
- `charge(false)`: the server funds the order. Nothing is taken and the fee is 0. The workers are still paid the full
  price when the order is completed, so this money is created at that point. Cancelling or removing such an order
  refunds nothing, because nothing was paid. Use this for rewards and quests.

**`respectLimits`: which limits apply**

- `respectLimits(true)`: the owner's limit of active orders applies. While the owner is online, the limit comes from
  their permissions; otherwise the configured limit is used. The maximum number of lines and items per order from
  [order.yml](../admin/configuration/order-yml.md) applies too.
- `respectLimits(false)`: none of these limits apply.

Some checks apply either way. The name may have at most 31 characters. Every line needs an amount of at least 1 and
a valid price. An exact item must be something players can order. Criteria must name known items, tags and
enchantments, at levels the enchantments have, on items that can carry them.

**What happens**

1. Companies checks the request.
2. It fires a `PreOrderCreateEvent` on the main thread, with the source `API`. Listeners may change or cancel the
   order. Companies checks the changed order again.
3. It checks the limit of active orders.
4. It charges the owner on the main thread, if `charge` is `true`.
5. It saves the order and fires an `OrderCreatedEvent`.

A rule the order breaks does not fail the future. The future completes with a `CreateOrderResult`:

- `Created` holds the saved order in `order()`.
- `Rejected` holds a `reason()` and a `message()`. Nothing was charged. The message is the text a player would see,
  as an Adventure `Component` in the server's language. If a listener cancelled the order with its own reason, the
  message is that reason.

| `RejectReason`       | Why                                                                             |
|----------------------|---------------------------------------------------------------------------------|
| `LIMIT_REACHED`      | The owner has reached their limit of active orders                              |
| `TOO_MANY_LINES`     | The order has more lines than the server allows                                 |
| `TOO_MANY_ITEMS`     | The order requests more items than the server allows                            |
| `INSUFFICIENT_FUNDS` | The owner cannot pay the price and the fee                                     |
| `CANCELLED`          | A listener cancelled the `PreOrderCreateEvent`                                  |
| `INVALID`            | The name is too long, a line is invalid, an exact item cannot be ordered, criteria no item can meet, or a listener left the order broken or empty |

If the order cannot be saved, the money is refunded and the future fails. The cause is an
`IllegalStateException` with the message "The order could not be saved".

## Players: `players()`

| Method                                          | Returns                      | What it does                                         |
|-------------------------------------------------|------------------------------|------------------------------------------------------|
| `grantItems(UUID player, ItemStack stack, int amount)` | `CompletableFuture<Void>` | **Write.** Puts items into the player's returned items |
| `itemsWaiting(UUID player)`                     | `CompletableFuture<Integer>` | How many items wait for the player. 0 when none       |

Players pick up their returned items with `/order collect`. The items are stored in the database. So the player can
pick them up on any server, and also when they are offline right now.

`grantItems` ignores the amount of the stack itself and uses the `amount` you pass. Once the items are stored,
Companies fires an `ItemsReturnedEvent` with the reason `API`. If the items cannot be stored, the future fails with
an `IllegalStateException`.

```java
ItemStack reward = ItemStack.of(Material.DIAMOND);
api.players().grantItems(player.getUniqueId(), reward, 16)
        .thenRun(() -> getLogger().info("Reward stored for " + player.getName()));
```

## Ranking: `ranking()`

| Method                                                    | Returns                                              | What it gives you                     |
|-----------------------------------------------------------|------------------------------------------------------|---------------------------------------|
| `page(TopOrder order, TimeRange range, int page, int size)` | `CompletableFuture<List<? extends ICompanyRank>>` | One page of the ranking, best first. Pages start at 1. Empty past the last page |
| `rankOf(int company, TopOrder order, TimeRange range)`    | `CompletableFuture<Optional<? extends ICompanyRank>>` | The place of one company. Empty when it is not ranked in the range |

`TopOrder` sets what the ranking is sorted by: `ORDERS`, `EARNED`, `MEMBER`, `ITEMS_DELIVERED` or `LEVEL`. Higher
values come first. Only companies with activity in a time range are ranked in it.

An `ICompanyRank` has the statistics of the company plus its `rank()`, `level()`, `tag()` and `color()`.

```java
api.ranking().page(TopOrder.EARNED, TimeRange.LAST_7_DAYS, 1, 10).thenAccept(page ->
        page.forEach(rank -> getLogger().info(rank.rank() + ". " + rank.name())));
```

## Data types

The methods and events hand you these types. They are in `de.eldoria.companies.components` and its subpackages.

| Type | What it holds |
|---|---|
| `ISimpleCompany` | Id, name, founding date and level of a company |
| `ICompanyProfile` | An `ISimpleCompany` with its members, owner, description, tag, colour and `JoinMode` |
| `ICompanyMember` | A member: the player, the company and the company permissions, with `hasPermission(...)`, `permissions()` and `isOwner()` |
| `ICompanyStats`, `ICompanyRank` | Statistics of a company, and its place in a ranking |
| `IMemberStats` | What one worker contributed: items, money earned and orders |
| `ITreasury` | Balance and payout share of a treasury |
| `ICompanyLevel` | A level from `company.yml`, with its `ILevelRequirement` and `ILevelSettings` |
| `ISimpleOrder`, `IFullOrder` | An order without and with its lines |
| `IOrderContent` | One line of an order, with its deliveries |
| `IDeliveredStack` | Items one worker delivered to a line: the worker, the item and the amount |
| `IContentPart` | How many items one worker delivered to a line, from `IOrderContent.parts()` |
| `IOrderDraft`, `IDraftLine` | An order before it is created, as `PreOrderCreateEvent.draft()` hands it to you |
| `IMaterialPrice` | Average, lowest and highest price paid for a material |

The enums are `CompanyPermission`, `JoinMode`, `OrderState`, `OrderSource`, `TimeRange`, `TopOrder` and
`TreasuryKind`. `HistoryOutcome` describes how an order ended in the history. No method or event of the API uses it
yet.

:::note
`ICompanyMember.addPermission(...)` and `removePermission(...)` only change the object you hold. Nothing is saved.
:::

## Keep orders anonymous

Companies keeps orders anonymous. Workers never learn who placed an order. Owners never learn which company works
on theirs.

The API and the events show both sides. Use them for logic, logging or staff tools. Never show the owner of an order
to the members of a company, and never show the company to the owner.
