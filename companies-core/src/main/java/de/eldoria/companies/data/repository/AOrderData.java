/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.sadu.mapper.wrapper.Row;
import de.eldoria.companies.Companies;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.DeliveredStack;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ItemAmount;
import de.eldoria.companies.data.wrapper.order.ItemReturn;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.ReleasedOrders;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.data.wrapper.order.ReturnedItems;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.DeliveryPart;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.threading.futures.FutureResult;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;
import static de.eldoria.companies.util.Threading.VIRTUAL;

@SuppressWarnings("UnusedReturnValue")
public abstract class AOrderData {
    private final Cache<Integer, Optional<FullOrder>> fullOrderCache =
            CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
    private final Cache<String, MaterialPrice> materialPriceCache =
            CacheBuilder.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).build();
    private final ObjectMapper mapper;
    private volatile IntConsumer fullOrderChanged = id -> {};

    public AOrderData(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Saves a new order with its content.
     *
     * @param player owner of the order
     * @param order  order to save
     * @return a future providing the saved order as it is stored, empty when it could not be saved
     */
    public BukkitFutureResult<Optional<FullOrder>> submitOrder(OfflinePlayer player, FullOrder order) {
        return CompletableBukkitFuture.supplyAsync(
                () -> putOrder(player, order).flatMap(this::orderById).map(this::toFullOrder), VIRTUAL);
    }

    /**
     * Saves a new order with its content.
     *
     * @param player owner of the order
     * @param order  order to save
     * @return the id of the saved order, empty if the order and its content were not saved
     */
    protected abstract Optional<Integer> putOrder(OfflinePlayer player, FullOrder order);

    public BukkitFutureResult<Boolean> submitOrderStateUpdate(
            SimpleOrder order, OrderState expected, OrderState state) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var updated = updateOrderState(order, expected, state);
                    invalidateFullOrder(order);
                    return updated;
                },
                VIRTUAL);
    }

    /**
     * Changes the state of an order if it is still in the expected state.
     *
     * @param order    order to change
     * @param expected state the order has to be in
     * @param state    new state
     * @return true if the state was changed
     */
    protected abstract boolean updateOrderState(SimpleOrder order, OrderState expected, OrderState state);

    protected void invalidateFullOrder(SimpleOrder order) {
        invalidateFullOrder(order.id());
    }

    protected void invalidateFullOrder(int id) {
        fullOrderCache.invalidate(id);
        fullOrderChanged.accept(id);
    }

    /**
     * Tells the listener about every order whose cached full order was dropped after a change on this node, so other
     * nodes can drop theirs.
     *
     * @param listener receives the id of the changed order
     */
    public void onFullOrderChanged(IntConsumer listener) {
        fullOrderChanged = listener;
    }

    /**
     * Drops a cached full order without telling anyone, after another node changed it.
     *
     * @param id id of the order
     */
    public void forgetFullOrder(int id) {
        fullOrderCache.invalidate(id);
    }

    /**
     * Drops every cached full order without telling anyone, after changes of other nodes may have been missed.
     */
    public void forgetFullOrders() {
        fullOrderCache.invalidateAll();
    }

    public CompletableFuture<List<SimpleOrder>> retrieveExpiredOrders(int hours) {
        return CompletableFuture.supplyAsync(() -> getExpiredOrders(hours), VIRTUAL);
    }

    protected abstract List<SimpleOrder> getExpiredOrders(int hours);

    public CompletableFuture<List<SimpleOrder>> retrieveDeadOrders(int hours) {
        return CompletableFuture.supplyAsync(() -> getDeadOrders(hours), VIRTUAL);
    }

    public BukkitFutureResult<Boolean> submitOrderClaim(SimpleCompany company, SimpleOrder order, int deliveryBonus) {
        return CompletableBukkitFuture.supplyAsync(() -> {
            var result = claimOrder(company, order, deliveryBonus);
            invalidateFullOrder(order);
            return result;
        });
    }

    /**
     * Claims an unclaimed order for a company.
     *
     * @param company       company claiming the order
     * @param order         order to claim
     * @param deliveryBonus extra delivery hours the company gets from its level
     * @return true if the order was unclaimed and is now claimed
     */
    protected abstract boolean claimOrder(SimpleCompany company, SimpleOrder order, int deliveryBonus);

    public BukkitFutureResult<Boolean> submitOrderExtension(SimpleOrder order, int maxExtensions) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var extended = extendOrder(order, maxExtensions);
                    invalidateFullOrder(order);
                    return extended;
                },
                VIRTUAL);
    }

    /**
     * Restarts the purge timer of an unclaimed order and counts the extension.
     *
     * @param order         order to extend
     * @param maxExtensions extensions an order may have
     * @return true if the order was unclaimed and had extensions left
     */
    protected abstract boolean extendOrder(SimpleOrder order, int maxExtensions);

    public CompletableFuture<List<SimpleOrder>> retrievePurgeWarnings(int maxUnclaimedHours, int warningHours) {
        return CompletableFuture.supplyAsync(
                () -> getPurgeWarnings(Math.max(0, maxUnclaimedHours - warningHours)), VIRTUAL);
    }

    /**
     * Unclaimed orders whose owner has to be warned about the purge: older than the given hours and not warned since
     * their last update.
     *
     * @param hours hours since the last update after which the owner is warned
     * @return orders to warn about
     */
    protected abstract List<SimpleOrder> getPurgeWarnings(int hours);

    public BukkitFutureResult<Boolean> submitPurgeWarning(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(() -> markPurgeWarning(order), VIRTUAL);
    }

    /**
     * Records that the owner of an unclaimed order was warned about the purge.
     *
     * @param order order the owner is warned about
     * @return true if the order is unclaimed and was not warned about since its last update
     */
    protected abstract boolean markPurgeWarning(SimpleOrder order);

    public BukkitFutureResult<Boolean> submitPriceRaise(
            SimpleOrder order, Map<Integer, Double> increases, double extraFee) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var raised = raisePrice(order, increases, extraFee);
                    invalidateFullOrder(order);
                    return raised;
                },
                VIRTUAL);
    }

    /**
     * Raises the prices of the contents of an unclaimed order and adds to its fee.
     *
     * @param order     order to raise the price of
     * @param increases amount to add to the price of each line, by line number
     * @param extraFee  amount to add to the fee
     * @return true if the order was unclaimed and is now changed
     */
    protected abstract boolean raisePrice(SimpleOrder order, Map<Integer, Double> increases, double extraFee);

    public BukkitFutureResult<Boolean> submitOrderReceive(SimpleOrder order, List<ItemAmount> pending) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var received = receiveOrder(order, pending);
                    invalidateFullOrder(order);
                    return received;
                },
                VIRTUAL);
    }

    /**
     * Marks a delivered order as received and stores the items which wait for the owner.
     *
     * @param order   order to receive
     * @param pending items that did not fit into the owner's inventory
     * @return true if the order was delivered and is now received
     */
    protected abstract boolean receiveOrder(SimpleOrder order, List<ItemAmount> pending);

    /**
     * Marks a claimed and completely delivered order as delivered.
     *
     * @param order order to mark
     * @return a future providing the delivered order, or an empty optional if the order was not claimed or is not completely delivered
     */
    public BukkitFutureResult<Optional<FullOrder>> submitOrderDelivered(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var delivered = orderDelivered(order);
                    invalidateFullOrder(order);
                    if (!delivered) return Optional.<FullOrder>empty();
                    return orderById(order.id()).map(this::toFullOrder);
                },
                VIRTUAL);
    }

    /**
     * Changes the state of a claimed and completely delivered order to delivered.
     *
     * @param order order to change
     * @return true if the state was changed
     */
    protected abstract boolean orderDelivered(SimpleOrder order);

    public BukkitFutureResult<Optional<ReturnedItems>> submitUnclaimOrder(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var result = unclaimOrder(order);
                    invalidateFullOrder(order);
                    return result;
                },
                VIRTUAL);
    }

    /**
     * Releases a claimed order back to the market.
     * <p>
     * The stacks already delivered to the order are moved unchanged to the item returns of the workers who delivered
     * them.
     *
     * @param order order to release
     * @return the items returned to the workers, or an empty optional if the order was not claimed
     */
    protected abstract Optional<ReturnedItems> unclaimOrder(SimpleOrder order);

    /**
     * Marks a claimed order as delivered for an admin, whether or not every item was delivered.
     *
     * @param order order to mark
     * @return a future providing the delivered order, or an empty optional if the order was not claimed
     */
    public BukkitFutureResult<Optional<FullOrder>> submitForcedDelivery(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var delivered = forceDelivered(order);
                    invalidateFullOrder(order);
                    if (!delivered) return Optional.<FullOrder>empty();
                    return orderById(order.id()).map(this::toFullOrder);
                },
                VIRTUAL);
    }

    /**
     * Changes the state of a claimed order to delivered, however much was delivered.
     *
     * @param order order to change
     * @return true if the order was claimed and is now delivered
     */
    protected abstract boolean forceDelivered(SimpleOrder order);

    /**
     * Deletes an unclaimed or claimed order for an admin.
     *
     * @param order order to delete
     * @return a future providing the items returned to the workers (none for an unclaimed order), or an empty
     *     optional if the order was neither unclaimed nor claimed
     */
    public BukkitFutureResult<Optional<ReturnedItems>> submitAdminDeletion(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var deleted = adminDelete(order);
                    invalidateFullOrder(order);
                    return deleted;
                },
                VIRTUAL);
    }

    /**
     * Deletes an unclaimed or claimed order in one step. The stacks delivered to a claimed order are moved unchanged
     * to the item returns of the workers who delivered them first. Delivered and received orders are kept.
     *
     * @param order order to delete
     * @return the items returned to the workers, or an empty optional if nothing was deleted
     */
    protected abstract Optional<ReturnedItems> adminDelete(SimpleOrder order);

    /**
     * Gives a claimed order more time to be delivered.
     *
     * @param order order to extend
     * @param hours hours to add to the delivery time
     * @return a future providing true if the order was claimed and is extended
     */
    public BukkitFutureResult<Boolean> submitClaimExtension(SimpleOrder order, int hours) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var extended = extendClaim(order, hours);
                    invalidateFullOrder(order);
                    return extended;
                },
                VIRTUAL);
    }

    /**
     * Adds hours to the delivery bonus of a claimed order.
     *
     * @param order order to extend
     * @param hours hours to add
     * @return true if the order was claimed and is extended
     */
    protected abstract boolean extendClaim(SimpleOrder order, int hours);

    /**
     * Moves the purge of an unclaimed order back without counting an extension.
     *
     * @param order order to extend
     * @param hours hours to move the purge back
     * @return a future providing true if the order was unclaimed and is extended
     */
    public BukkitFutureResult<Boolean> submitUnclaimedExtension(SimpleOrder order, int hours) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var extended = extendUnclaimed(order, hours);
                    invalidateFullOrder(order);
                    return extended;
                },
                VIRTUAL);
    }

    /**
     * Adds hours to the last update of an unclaimed order, which the purge counts from. The extensions of the owner
     * stay untouched.
     *
     * @param order order to extend
     * @param hours hours to add
     * @return true if the order was unclaimed and is extended
     */
    protected abstract boolean extendUnclaimed(SimpleOrder order, int hours);

    /**
     * One page of all orders for admins, the newest first, optionally of one owner or one company.
     *
     * @param owner   owner to filter by, or null for every owner
     * @param company company to filter by, or null for every company
     * @param page    page, starting at 1
     * @param size    orders per page
     * @return orders of the page
     */
    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersForAdmin(
            @Nullable UUID owner, @Nullable Integer company, int page, int size) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersForAdmin(owner, company, page, size), VIRTUAL);
    }

    protected abstract List<SimpleOrder> ordersForAdmin(
            @Nullable UUID owner, @Nullable Integer company, int page, int size);

    public BukkitFutureResult<List<ItemReturn>> retrieveItemReturns(UUID player, ReturnKind kind) {
        return CompletableBukkitFuture.supplyAsync(() -> getItemReturns(player, kind), VIRTUAL);
    }

    protected abstract List<ItemReturn> getItemReturns(UUID player, ReturnKind kind);

    public BukkitFutureResult<Integer> retrieveItemReturnCount(UUID player, ReturnKind kind) {
        return CompletableBukkitFuture.supplyAsync(() -> countItemReturns(player, kind), VIRTUAL);
    }

    /**
     * Counts the stored items of a kind waiting for a player.
     *
     * @param player player owning the items
     * @param kind   kind of the items
     * @return amount of items
     */
    protected abstract int countItemReturns(UUID player, ReturnKind kind);

    public BukkitFutureResult<Boolean> submitItemReturnTake(ItemReturn itemReturn, int amount) {
        return CompletableBukkitFuture.supplyAsync(() -> takeItemReturn(itemReturn, amount), VIRTUAL);
    }

    /**
     * Removes items from a stored return.
     *
     * @param itemReturn return to take the items from
     * @param amount     amount of items to take
     * @return true if the items were still stored and are now removed
     */
    protected abstract boolean takeItemReturn(ItemReturn itemReturn, int amount);

    public BukkitFutureResult<Boolean> submitItemReturn(UUID player, ReturnKind kind, ItemStack stack, int amount) {
        return CompletableBukkitFuture.supplyAsync(() -> addItemReturn(player, kind, stack, amount), VIRTUAL);
    }

    /**
     * Stores items which have to be handed to a player later.
     *
     * @param player player owning the items
     * @param kind   why the items wait
     * @param stack  item to store
     * @param amount amount of items
     * @return true if the items were stored
     */
    protected abstract boolean addItemReturn(UUID player, ReturnKind kind, ItemStack stack, int amount);

    /**
     * Records the items a worker delivers to an order.
     *
     * @param player worker delivering the items
     * @param order  order receiving the items
     * @param parts  offered items, the plain ones first
     * @return a future providing the recorded amount of each part, in the order of {@code parts}
     */
    public BukkitFutureResult<List<Integer>> submitDeliveries(
            OfflinePlayer player, SimpleOrder order, List<DeliveryPart> parts) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var delivered = deliver(player, order, parts);
                    invalidateFullOrder(order);
                    return delivered;
                },
                VIRTUAL);
    }

    /**
     * Records the items a worker delivers as delivered stacks.
     * <p>
     * Nothing is recorded unless the order is claimed. The parts of a line are capped in list order by the amount the
     * line still misses, and every recorded part is stored as its own stack.
     *
     * @param player worker delivering the items
     * @param order  order receiving the items
     * @param parts  offered items
     * @return recorded amount of each part, in the order of {@code parts}
     */
    protected abstract List<Integer> deliver(OfflinePlayer player, SimpleOrder order, List<DeliveryPart> parts);

    public BukkitFutureResult<Optional<SimpleOrder>> retrieveOrderById(int id) {
        return CompletableBukkitFuture.supplyAsync(() -> orderById(id), VIRTUAL);
    }

    protected abstract Optional<SimpleOrder> orderById(int id);

    public BukkitFutureResult<Optional<SimpleOrder>> retrieveCompanyOrderById(int id, int company) {
        return CompletableBukkitFuture.supplyAsync(() -> companyOrderById(id, company), VIRTUAL);
    }

    protected abstract Optional<SimpleOrder> companyOrderById(int id, int company);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByCompany(
            ISimpleCompany company, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersByCompany(company, min, max));
    }

    protected abstract List<SimpleOrder> ordersByCompany(ISimpleCompany company, OrderState min, OrderState max);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByPlayer(
            OfflinePlayer player, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersByPlayer(player, min, max), VIRTUAL);
    }

    protected abstract List<SimpleOrder> ordersByPlayer(OfflinePlayer player, OrderState min, OrderState max);

    public SimpleOrder buildSimpleOrder(Row row) throws SQLException {
        return new SimpleOrder(
                row.getInt("id"),
                row.get("owner_uuid", UUID_BYTES),
                row.getString("name"),
                row.getTimestamp("created").toLocalDateTime(),
                row.getInt("company"),
                row.getTimestamp("last_update").toLocalDateTime(),
                OrderState.byId(row.getInt("state")),
                row.getDouble("fee"),
                row.getInt("extensions"),
                row.getInt("delivery_bonus"),
                row.getInt("series_id"));
    }

    public BukkitFutureResult<List<FullOrder>> retrieveFullOrders(List<SimpleOrder> orders) {
        return CompletableBukkitFuture.supplyAsync(() -> toFullOrders(orders), VIRTUAL);
    }

    public List<FullOrder> toFullOrders(List<SimpleOrder> orders) {
        List<CompletableFuture<Optional<FullOrder>>> fullOrders = new ArrayList<>();
        for (var order : orders) {
            fullOrders.add(CompletableFuture.supplyAsync(
                    () -> cacheFullOrder(order, () -> Optional.of(toFullOrder(order))), VIRTUAL));
        }
        return fullOrders.stream()
                .map(CompletableFuture::join)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    protected Optional<FullOrder> cacheFullOrder(SimpleOrder order, Callable<Optional<FullOrder>> orderCallable) {
        return cacheFullOrder(order.id(), orderCallable);
    }

    public @NotNull FullOrder toFullOrder(SimpleOrder order) {
        var orderContent = getOrderContent(order);
        return order.toFullOrder(orderContent);
    }

    protected Optional<FullOrder> cacheFullOrder(int id, Callable<Optional<FullOrder>> orderCallable) {
        try {
            return fullOrderCache.get(id, orderCallable);
        } catch (ExecutionException e) {
            Companies.logger().log(Level.SEVERE, "Could not compute value for order " + id);
        }
        return Optional.empty();
    }

    protected abstract List<OrderContent> getOrderContent(SimpleOrder order);

    public BukkitFutureResult<FullOrder> retrieveFullOrder(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(
                () -> cacheFullOrder(order, () -> Optional.of(toFullOrder(order)))
                        .get(),
                VIRTUAL);
    }

    public BukkitFutureResult<Integer> retrievePlayerOrderCount(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerOrderCount(player), VIRTUAL);
    }

    protected abstract Integer getPlayerOrderCount(OfflinePlayer player);

    public BukkitFutureResult<Integer> retrieveCompanyOrderCount(ISimpleCompany company) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyOrderCount(company), VIRTUAL);
    }

    protected abstract Integer getCompanyOrderCount(ISimpleCompany company);

    public BukkitFutureResult<ReleasedOrders> submitCompanyOrdersPurge(SimpleCompany profile) {
        return CompletableBukkitFuture.supplyAsync(() -> purgeCompanyOrders(profile), VIRTUAL);
    }

    /**
     * Releases all orders claimed by the company.
     *
     * @param profile company to release the orders of
     * @return the released orders and the workers who got items returned
     */
    protected abstract ReleasedOrders purgeCompanyOrders(SimpleCompany profile);

    public BukkitFutureResult<Boolean> submitOrderPurge(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var purged = purgeOrder(order);
                    invalidateFullOrder(order);
                    return purged;
                },
                VIRTUAL);
    }

    /**
     * Removes an order which was not claimed for too long.
     *
     * @param order order to remove
     * @return true if the order was still unclaimed and is now removed
     */
    protected abstract boolean purgeOrder(SimpleOrder order);

    public BukkitFutureResult<Boolean> submitOrderDeletion(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var deleted = deleteOrder(order);
                    invalidateFullOrder(order);
                    return deleted;
                },
                VIRTUAL);
    }

    /**
     * Deletes an order if it is still unclaimed.
     *
     * @param order order to delete
     * @return true if the order was unclaimed and is now deleted
     */
    protected abstract boolean deleteOrder(SimpleOrder order);

    public BukkitFutureResult<List<FullOrder>> retrieveOrdersByQuery(
            SearchQuery searchQuery, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> getOrdersByQuery(searchQuery, min, max), VIRTUAL);
    }

    protected abstract List<FullOrder> getOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max);

    public FutureResult<Void> submitMaterialPriceRefresh() {
        return CompletableBukkitFuture.runAsync(this::refreshMaterialPrices, VIRTUAL);
    }

    public abstract void refreshMaterialPrices();

    /**
     * Get the material price from cache.
     * <p>
     * If no cached result is present this will return a empty optional and queue a pull from the database to retrieve the price.
     * <p>
     * Use {@link #retrieveMaterialPrice(String)} for async retrieval of the material price
     *
     * @param material material to check
     * @return material price if present or empty optional
     */
    public MaterialPrice getMaterialPrice(String material) {
        var price = materialPriceCache.getIfPresent(material);
        if (price != null) return price;
        retrieveMaterialPrice(material);
        return new MaterialPrice(material);
    }

    /**
     * Retrieves a material price from the database or returns a cached result.
     *
     * @param material material to retrieve
     * @return future which provides a material price
     */
    public FutureResult<MaterialPrice> retrieveMaterialPrice(String material) {
        return CompletableBukkitFuture.supplyAsync(
                () -> {
                    var price = findMaterialPrice(material).orElse(new MaterialPrice(material));
                    materialPriceCache.put(material, price);
                    return price;
                },
                VIRTUAL);
    }

    protected abstract Optional<MaterialPrice> findMaterialPrice(String material);

    public void invalidateMaterialPriceCache() {
        materialPriceCache.invalidateAll();
    }

    /**
     * Orders which are unclaimed and were not updated for the given amount of hours.
     *
     * @param hours hours since the last update
     * @return orders
     */
    protected abstract List<SimpleOrder> getDeadOrders(int hours);

    /**
     * The stacks delivered to one line of an order.
     *
     * @param order order of the line
     * @param line  line number
     * @return delivered stacks in the order they were delivered
     */
    protected abstract List<DeliveredStack> getDeliveredStacks(SimpleOrder order, int line);

    protected ItemStack toItemStack(String json) throws SQLException {
        try {
            return mapper.readValue(json, ItemStack.class);
        } catch (JacksonException e) {
            throw new SQLException("Could not parse json to item stack", e);
        }
    }

    protected String toJson(ItemStack stack) {
        try {
            return mapper.writeValueAsString(stack);
        } catch (JacksonException e) {
            throw new RuntimeException("Could not parse item stack to json", e);
        }
    }
}
