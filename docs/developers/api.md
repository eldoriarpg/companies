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

The API is split into five parts:

| Method         | Returns      | Use it for                                    |
|----------------|--------------|-----------------------------------------------|
| `companies()`  | `CompanyApi` | Companies, members, statistics and treasuries |
| `orders()`     | `OrderApi`   | Orders, material prices and creating orders   |
| `players()`    | `PlayerApi`  | Items that wait for a player                  |
| `ranking()`    | `RankingApi` | The company ranking of `/company top`         |
| `events()`     | `ServerEventApi` | Server events: tax and fee reductions, reward multipliers |

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

- `companies().addMember(...)`, `companies().found(...)`, `companies().setHome(...)`
- `orders().create(...)`
- `players().grantItems(...)`
- `events().create(...)`, `events().end(...)`, `events().cancel(...)`
- `jobs().post(...)`
- `areas().offer(...)`, `areas().withdraw(...)`

`ApiNotReadyException` extends `IllegalStateException`. Depending on the write it arrives wrapped in a
`CompletionException` or as is, so unwrap it as below. A request Companies refuses completes normally with a result
that says why. The future fails for errors, such as a world that was unloaded meanwhile or a database that does not
answer. Invalid arguments throw an `IllegalArgumentException` when you build the request; `orders().create` fails its
future with one for an owner id reserved for the server:

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
| `areas(int company)`                      | `CompletableFuture<List<? extends ICompanyArea>>` | The plots and regions the company linked, bought or rents, oldest first                             |
| `home(int company)`                       | `CompletableFuture<Optional<? extends ICompanyHome>>` | The company's home, empty when it has set none                                                  |
| `addMember(int company, UUID player)`     | `CompletableFuture<JoinResult>`                  | **Write.** Adds the player to the company                                                             |
| `found(FoundRequest request)`             | `CompletableFuture<FoundResult>`                 | **Write.** Founds a company. New in 2.1                                                               |
| `setHome(HomeRequest request)`            | `CompletableFuture<HomeResult>`                  | **Write.** Sets the home of a company. New in 2.1                                                     |

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

### Found a company

`found` founds a company with the same checks as `/company create`. The owner need not be online.

- The name must be valid and free, and the owner must not be a member of a company.
- With `charge` true the owner pays the founding price; with false nothing is charged and Companies logs the
  founding.
- While the owner is online on this server, a `PreCompanyCreateEvent` fires and other plugins may cancel it. For an
  uncharged founding the event names a price of 0. A founded company fires `CompanyCreateEvent`.

The future completes with `FoundResult.Founded` and the new `ICompanyProfile`, or with `FoundResult.Rejected` and a
`FoundProblem`: `INVALID_NAME`, `NAME_TAKEN`, `ALREADY_MEMBER`, `NO_MONEY`, `CANCELLED` or `FAILED`. `Rejected`
also holds the message a player would have seen.

```java
api.companies().found(new FoundRequest(player.getUniqueId(), "Stonebridge", false)).thenAccept(result -> {
    if (result instanceof FoundResult.Founded founded) {
        getLogger().info("Founded company " + founded.company().id());
    }
});
```

### Set the home

`setHome` sets the home of a company on this server, replacing its former one. `HomeRequest` takes the company id,
a location (copied, so changing it later moves nothing) and who set it. A company with areas sets its home inside one
of them while `homeInsideArea` is on in `areas.yml`; the setter needs no company permission. The area plugins are
asked on the main thread. The future completes with `HomeResult.Stored` and the `ICompanyHome`, or `HomeResult.Rejected`
with `NO_COMPANY`, `NOT_IN_AREA` or `FAILED`. Companies fires `CompanyAreasChangeEvent`.

```java
api.companies().setHome(new HomeRequest(companyId, player.getLocation(), player.getUniqueId()))
        .thenAccept(result -> {
            if (result instanceof HomeResult.Rejected rejected) player.sendMessage(rejected.message());
        });
```

## Orders: `orders()`

| Method                                                | Returns                                          | What it gives you                                    |
|-------------------------------------------------------|--------------------------------------------------|------------------------------------------------------|
| `byId(int id)`                                        | `CompletableFuture<Optional<? extends IFullOrder>>` | The order with its lines                          |
| `byCompany(int company, OrderState min, OrderState max)` | `CompletableFuture<List<? extends IFullOrder>>` | Orders the company claimed, with a state from `min` to `max` |
| `byOwner(UUID owner, OrderState min, OrderState max)` | same                                             | Orders the player placed, with a state from `min` to `max` |
| `bySoloWorker(UUID worker, OrderState min, OrderState max)` | same                                       | Orders the player took to work on alone, with a state from `min` to `max` |
| `materialPrice(Material material)`                    | `CompletableFuture<? extends IMaterialPrice>`    | Average, lowest and highest price paid for the material, all 0 if it was never ordered, and its shop baseline. `price()` is the one Companies goes by |
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
        .line(OrderLineRequest.criteria(ItemCriteria.ofTags(Tag.ITEMS_LOGS), 256, 1.5))
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
ItemCriteria swords = ItemCriteria.ofMaterials(Material.DIAMOND_SWORD, Material.NETHERITE_SWORD)
        .withMinEnchantments(2)
        .withEnchantment(Enchantment.SHARPNESS, 3);
```

This accepts diamond and netherite swords with at least two enchantments, one of them Sharpness III or higher.
`ofTags(Tag...)` and `withTag(Tag)` add item tags such as `Tag.ITEMS_LOGS`, `withMaterial(Material)` another material.
These refuse with an `IllegalArgumentException` what the server can not order: a material that is no item, a tag the
server does not register for items (a block tag such as `Tag.MINEABLE_AXE`; a block tag whose item tag of the same key
has other members), and an enchantment level above its maximum. Prefer the `Tag.ITEMS_*` constants.
The criteria keep their entries as text, as orders store them: `items()` lists material names and tags with a leading
`#`, `tags()` the tags among them the server knows, and `Required.lookup()` finds an enchantment in the server's
registry. `of(String...)` and `withEnchantment(String, int)` take those texts directly, for entries read from a
config; `create` rejects an order whose criteria name something the server does not know, an enchantment that does
not fit the items, or two that conflict. Enchanted books count their stored enchantments. Other data of an item does
not matter.

The amount must be at least 1. The unit price must be a finite number and not negative. The item must be an item,
not air. A line that breaks one of these rules throws an `IllegalArgumentException` when you create it.

`request.price()` and `line.price()` give the sum of the line prices, without the fee.

**`charge`: who pays**

- `charge(true)`: the owner pays the price and the order fee, like with `/order create`. Companies takes the money on
  the main thread. An owner who cannot afford it gets the order rejected with `INSUFFICIENT_FUNDS`. If the order cannot
  be saved, the money is refunded. Cancelling and removing the order refund the owner as usual.
- `charge(false)`: the server funds the order. Nothing is taken and the fee is 0. The workers are still paid the full
  price when the order is completed, so this money is created at that point. Cancelling or removing such an order
  refunds nothing, because nothing was paid; `charged()` on the order says so. Its owner can not raise its price
  either. Use this for rewards and quests.

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

## Server events: `events()`

Server events lower the tax or the order fee, or pay extra on top of payouts, for a time (see
[Server events](../players/server-events.md)). Every server of a network shares them; the primary server starts and
ends them at their times.

| Method                                  | Returns                                                | What it gives you                     |
|-----------------------------------------|--------------------------------------------------------|---------------------------------------|
| `live()`                                | `CompletableFuture<List<? extends IServerEvent>>`      | The events that wait for their start or run, by start |
| `recent(int limit)`                     | `CompletableFuture<List<? extends IServerEvent>>`      | The newest events, newest first, whatever their state |
| `event(int id)`                         | `CompletableFuture<Optional<? extends IServerEvent>>`  | One event, empty when there is none   |
| `create(Plugin plugin, EventRequest request)` | `CompletableFuture<CreateEventResult>`           | Creates an event that waits for its start. Write |
| `end(Plugin plugin, int id)`            | `CompletableFuture<EventChange>`                       | Ends a running event now. Write       |
| `cancel(Plugin plugin, int id)`         | `CompletableFuture<EventChange>`                       | Cancels a waiting or running event. Write |

An event checks like one an admin creates with `/companyadmin event create`: a name of at most 64 characters, a value
that fits its `EventKind`, an end after the start and at most a year ahead, materials that name items, and no level
scope on a fee reduction. `create` answers `CreateEventResult.Created` with the stored event, or
`CreateEventResult.Rejected` with an `EventProblem`. Your plugin is noted as `<plugin>@api` as who created, ended or
cancelled an event. `end` and `cancel` answer an `EventChange`: `DONE`, `UNKNOWN`, `CLOSED`, `REFUSED` (ending an
event that has not started; cancel it instead) or `FAILED`.

| `EventKind`         | `value()`                               | Money |
|---------------------|-----------------------------------------|-------|
| `TAX_REDUCTION`     | Percentage points off the tax, up to 100 | None |
| `REWARD_MULTIPLIER` | Factor on the gross payout, above 1      | Minted within a budget, or from an account with an optional budget |
| `FEE_REDUCTION`     | Percent off the order fee, up to 100     | None |

```java
var now = LocalDateTime.now(ZoneOffset.UTC);
var request = EventRequest.of("Harvest week", EventKind.REWARD_MULTIPLIER, 1.5, now.plusHours(1), now.plusDays(7))
        .withMaterials(List.of("WHEAT", "CARROT"))
        .mintedWithin(5000);
api.events().create(this, request).thenAccept(result -> {
    if (result instanceof CreateEventResult.Rejected rejected) {
        getLogger().warning("Event refused: " + rejected.problem());
    }
});
```

All times are UTC. A `REWARD_MULTIPLIER` needs `mintedWithin(...)` or `paidFrom(...)`; without either it is rejected
as `ACCOUNT`. Money fields of a kind that pays nothing are ignored. The start and end of an event fire their events
only on the server that moved it, usually the primary (see [Events](events.md)); listen on every server, or read
`live()`, to follow events on the others. Writes fail with `ApiNotReadyException` until
the economy is set up, like the other writes.

## Jobs: `jobs()`

:::info New in 2.1
:::

[Jobs](../players/jobs.md) are work a company takes like an order: in an area of the owner's land (`DIG`,
`TERRAFORM`, `HARVEST`, `BUILD`) or described in words (`CONTRACT`).

| Method | Returns | What it gives you |
|---|---|---|
| `byId(int id)` | `CompletableFuture<Optional<? extends IJob>>` | The job with this id |
| `open(int limit)` | `CompletableFuture<List<? extends IJob>>` | Jobs waiting for a company, newest first |
| `ofOwner(UUID owner, int limit)` | `CompletableFuture<List<? extends IJob>>` | The player's jobs in any state, newest first |
| `ofCompany(int company)` | `CompletableFuture<List<? extends IJob>>` | The jobs a company holds: worked on, waiting for approval or disputed |
| `post(JobRequest request)` | `CompletableFuture<PostJobResult>` | **Write.** Posts a job |

`post` checks like `/order job`: the area lies on this server and within the world's height, on land the owner owns
(or on unowned land where `jobs.yml` allows jobs in that world), and within the size and count limits. Start a
request from the factory of its kind: `JobRequest.dig`, `terraform`, `harvest`, `build` or `contract`. A build job
takes its blueprint as `BuildBlock`s relative to the point it is placed at, so your plugin needs no WorldEdit.

With `charge(false)` the owner pays nothing: the job takes no fee, its company is still paid when the work checks out,
and nothing goes back to the owner when it ends unfinished. `IJob.charged()` tells the two apart.

The future completes with `PostJobResult.Posted` and the `IJob`, or `PostJobResult.Rejected` with a `JobProblem`, such
as `FOREIGN_LAND`, `VOLUME` or `FUNDS`. Nothing was charged for a rejected job, except `STRANDED`: a build job taken
before its blueprint was saved, whose money stays held until an admin settles it.

```java
var request = JobRequest.dig(player.getUniqueId(), "Cellar", 250, 24, new JobBox("world", 10, 50, 10, 19, 59, 19));
api.jobs().post(request).thenAccept(result -> {
    if (result instanceof PostJobResult.Rejected rejected) getLogger().info("Job refused: " + rejected.problem());
});
```

Taking, finishing and ending jobs happen in game and on the dashboard; the API has no events for jobs yet.

## Areas: `areas()`

Companies use plots and regions as [areas](../companies/areas.md). They come from area providers: Companies brings
providers for PlotSquared, WorldGuard, GriefPrevention, HuskClaims, Lands and Towny, and your plugin can register one for its own plots or claims.

| Method                                         | Returns        | What it does                                              |
|------------------------------------------------|----------------|-----------------------------------------------------------|
| `register(Plugin plugin, AreaProvider provider)` | `void`       | Registers a provider; it is dropped when your plugin is disabled |
| `unregister(String id)`                        | `void`         | Removes the provider with that id                         |
| `providers()`                                  | `List<String>` | The ids of the providers in use, in the order they are asked |
| `offer(AreaOfferRequest request)`              | `CompletableFuture<OfferResult>` | **Write.** Offers an area to companies. New in 2.1 |
| `offers()`                                     | `CompletableFuture<List<? extends IAreaOffer>>` | The offers no company has yet. New in 2.1 |
| `withdraw(int id, String by)`                  | `CompletableFuture<WithdrawResult>` | **Write.** Withdraws an offer no company has. New in 2.1 |

An `AreaProvider` names its areas with references of its own, such as a plot id or a region id. It says what it can
do with `capabilities()`: `LINK_OWNED` (an owner links their own area), `TRUST_LIST` (Companies adds and removes the
members with the build permission) and `TRANSFER` (the area can be bought or rented: Companies moves it to the
company's owner and gives it back with `release`) and `JOB_LAND` (players post [jobs](../players/jobs.md) on areas
they own; the working company's members are made builders while they work). `protects(Location)` marks land the plugin
guards without an area there, such as roads between plots, where jobs are refused. `releasesToNobody()` says whether
`release` works without a keeper; the server only offers an area nobody owns when it does. Every method of a provider is
called on the main thread. Read the areas of a company with `companies().areas(id)`.

For a plugin made of claims, extend `ClaimAreaProvider<C>` instead of implementing every method: it reaches your
claims through a few methods (`claimAt`, `claim(ref)`, `ref`, `claimOwners`, `world`, `boxes`, and `trusted`, `trust`,
`untrust`, `transfer`, `giveBack` for the capabilities you declare) and does the rest the way Companies' own providers
do: a subclaim stands for its top-level claim, a player is trusted once, and a claim that is gone refuses every
change.

```java
public final class MyClaims extends ClaimAreaProvider<MyClaim> {
    @Override public String id() { return "myclaims"; }
    @Override public Set<AreaCapability> capabilities() {
        return EnumSet.of(AreaCapability.LINK_OWNED, AreaCapability.TRUST_LIST, AreaCapability.JOB_LAND);
    }
    @Override protected Optional<MyClaim> claimAt(Location location) { return MyPlugin.claimAt(location); }
    @Override protected Optional<MyClaim> claim(String ref) { return MyPlugin.claim(Long.parseLong(ref)); }
    @Override protected String ref(MyClaim claim) { return Long.toString(claim.id()); }
    @Override protected Set<UUID> claimOwners(MyClaim claim) { return Set.of(claim.owner()); }
    @Override protected String world(MyClaim claim) { return claim.world(); }
    @Override protected List<AreaBounds.Box> boxes(MyClaim claim) { return List.of(claim.box()); }
    @Override protected Set<UUID> trusted(MyClaim claim) { return claim.builders(); }
    @Override protected boolean trust(MyClaim claim, UUID player) { return claim.trust(player); }
    @Override protected boolean untrust(MyClaim claim, UUID player) { return claim.untrust(player); }
}

api.areas().register(this, new MyClaims());
```

### Offer areas

`offer` does what `/companyadmin area offer` does. Name the area with `AreaOfferRequest.at(location, ...)` or by its
plugin and reference with `AreaOfferRequest.of(provider, ref, ...)`, and choose `OfferKind.BUY` or `RENT` with a
price and, for a rent, the days of a period. The price lies between 0 and `AreaOfferRequest.MAX_PRICE` and is
rounded down to cents; a rent period lasts 1 to `MAX_PERIOD_DAYS` (3650) days, a sale has 0. The area's plugin must
have `TRANSFER`, and someone must keep the area for the server: its owner, or nobody where the plugin
`releasesToNobody()`. The future completes with `OfferResult.Offered` and the `IAreaOffer`, or `OfferResult.Rejected`
with `NO_AREA`, `NOT_MOVABLE`, `NO_KEEPER`, `IN_USE` or `ALREADY_OFFERED`. `withdraw` answers `WITHDRAWN`, `TAKEN` (a
company has the area) or `UNKNOWN`.

```java
var request = AreaOfferRequest.of("plotsquared", "plots|3;-2", OfferKind.RENT, 250, 7, getName());
api.areas().offer(request).thenAccept(result -> {
    if (result instanceof OfferResult.Offered offered) getLogger().info("Offered as #" + offered.offer().id());
});
```

## Prices: `prices()`

Companies reads what items cost in the server's shops and works out a baseline price per material from it, which
order prices fall back to while a material has few delivered orders (see [prices.yml](../admin/configuration/prices-yml.md)).
The prices come from price sources, one per shop plugin; your plugin can register one for its own shops. New in 2.1.

| Method                                        | Returns        | What it does                                               |
|-----------------------------------------------|----------------|------------------------------------------------------------|
| `register(Plugin plugin, PriceSource source)` | `void`         | Registers a source; it is dropped when your plugin is disabled |
| `unregister(String id)`                       | `void`         | Removes the source with that id                            |
| `sources()`                                   | `List<String>` | The ids of the sources in use                              |

A `PriceSource` has an `id()` and a `name()` and gives prices in one or both ways:

- `quotes()` returns what the shops offer right now as `ShopQuote`s. Companies calls it on the main thread every
  `sampleMinutes`, so the source needs no index of its own.
- `record(PriceRecorder recorder)` is called once on registration. Hand each trade to `recorder.trade(ShopTrade)` as
  it happens, usually from your plugin's transaction event. The recorder may be called from any thread.

`ShopQuote` and `ShopTrade` both carry the item, the amount, the price of all those items, the `ShopSide` (`BUY`:
the player pays the shop, `SELL`: the shop pays the player) and the `ShopOrigin` (`PLAYER`, `ADMIN` for admin shops in
the world, `SERVER` for prices the server configures). Give prices in the server's main currency only, and leave out
trades of a player with their own shop. Companies drops items that are not plain and prices or amounts of zero.

```java
public final class MyShops implements PriceSource {
    @Override public String id() { return "myshops"; }
    @Override public String name() { return "MyShops"; }
    @Override public Collection<ShopQuote> quotes() {
        return MyPlugin.shops().stream()
                .map(shop -> new ShopQuote(shop.item(), shop.amount(), shop.price(), ShopSide.BUY, ShopOrigin.PLAYER))
                .toList();
    }
}

api.prices().register(this, new MyShops());
```

`orders().materialPrice(material)` gives the result: `baseline()` and `basis()` (`TRADES`, `LISTINGS` or `SERVER`)
next to the prices of past orders, `fromOrders()` and `price()`, the price Companies goes by.

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
| `ICompanyArea` | An area of a company: provider, reference, world, server, `AreaSource` (`LINKED`, `BOUGHT`, `RENTED`), price and until when a rent is paid |
| `ICompanyHome` | The home of a company: server, world, position and who set it |
| `ICompanyLevel` | A level from `company.yml`, with its `ILevelRequirement` and `ILevelSettings` |
| `ISimpleOrder`, `IFullOrder` | An order without and with its lines |
| `IOrderContent` | One line of an order, with its deliveries |
| `IDeliveredStack` | Items one worker delivered to a line: the worker, the item and the amount |
| `IContentPart` | How many items one worker delivered to a line, from `IOrderContent.parts()` |
| `IOrderDraft`, `IDraftLine` | An order before it is created, as `PreOrderCreateEvent.draft()` hands it to you |
| `IMaterialPrice` | Average, lowest and highest price paid for a material, its shop baseline and the price Companies goes by |
| `IJob`, `IJobArea` | A job: owner, kind, state, title, price and fee, the company working on it, its block box and times, and whether it was charged |
| `JobBox`, `BuildBlock` | A block box for a job request, corners in any order, and one block of a build job's blueprint |
| `IAreaOffer` | An area the server offers: provider, reference, world, server, `OfferKind`, price, rent period and keeper |

The enums are `CompanyPermission`, `JoinMode`, `OrderState`, `OrderSource`, `TimeRange`, `TopOrder`,
`TreasuryKind`, `JobKind`, `JobState` and `OfferKind`. `HistoryOutcome` describes how an order ended in the history. No method or event of the API uses it
yet.

:::note
`ICompanyMember.addPermission(...)` and `removePermission(...)` only change the object you hold. Nothing is saved.
:::

## Keep orders anonymous

Companies keeps orders anonymous. Workers never learn who placed an order. Owners never learn which company works
on theirs.

The API and the events show both sides. Use them for logic, logging or staff tools. Never show the owner of an order
to the members of a company, and never show the company to the owner.
