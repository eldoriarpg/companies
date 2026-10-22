/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.sadu.mapper.wrapper.Row;
import de.eldoria.companies.Companies;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.ContentPart;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.ItemReturn;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.threading.futures.FutureResult;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;
import static de.eldoria.companies.util.Threading.VIRTUAL;

@SuppressWarnings("UnusedReturnValue")
public abstract class AOrderData {
    private final Cache<Integer, Optional<FullOrder>> fullOrderCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5L, TimeUnit.MINUTES)
            .build();
    private final Cache<String, MaterialPrice> materialPriceCache = CacheBuilder.newBuilder()
            .expireAfterAccess(1L, TimeUnit.HOURS)
            .build();
    private final ObjectMapper mapper;

    public AOrderData(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // Order Management
    public BukkitFutureResult<Void> submitOrder(OfflinePlayer player, FullOrder order) {
        return CompletableBukkitFuture.runAsync(() -> putOrder(player, order), VIRTUAL);
    }

    protected abstract void putOrder(OfflinePlayer player, FullOrder order);

    public BukkitFutureResult<Boolean> submitOrderStateUpdate(SimpleOrder order, OrderState expected, OrderState state) {
        return CompletableBukkitFuture.supplyAsync(() -> {
            var updated = updateOrderState(order, expected, state);
            invalidateFullOrder(order);
            return updated;
        }, VIRTUAL);
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
    }

    public CompletableFuture<List<SimpleOrder>> retrieveExpiredOrders(int hours) {
        return CompletableFuture.supplyAsync(() -> getExpiredOrders(hours), VIRTUAL);
    }

    protected abstract List<SimpleOrder> getExpiredOrders(int hours);

    public CompletableFuture<List<SimpleOrder>> retrieveDeadOrders(int hours) {
        return CompletableFuture.supplyAsync(() -> getExpiredOrders(hours), VIRTUAL);
    }

    public CompletableFuture<List<SimpleOrder>> retrieveExpiredOrdersByCompany(int hours, SimpleCompany company) {
        return CompletableFuture.supplyAsync(() -> getExpiredOrdersByCompany(hours, company), VIRTUAL);
    }

    protected abstract List<SimpleOrder> getExpiredOrdersByCompany(int hours, SimpleCompany company);

    public BukkitFutureResult<Boolean> submitOrderClaim(SimpleCompany company, SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(() -> {
            var result = claimOrder(company, order);
            invalidateFullOrder(order);
            return result;
        });
    }

    protected abstract boolean claimOrder(SimpleCompany company, SimpleOrder order);

    /**
     * Marks a claimed and completely delivered order as delivered.
     *
     * @param order order to mark
     * @return a future providing the delivered order, or an empty optional if the order was not claimed or is not completely delivered
     */
    public BukkitFutureResult<Optional<FullOrder>> submitOrderDelivered(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(() -> {
            var delivered = orderDelivered(order);
            invalidateFullOrder(order);
            if (!delivered) return Optional.<FullOrder>empty();
            return orderById(order.id()).map(this::toFullOrder);
        }, VIRTUAL);
    }

    /**
     * Changes the state of a claimed and completely delivered order to delivered.
     *
     * @param order order to change
     * @return true if the state was changed
     */
    protected abstract boolean orderDelivered(SimpleOrder order);

    public BukkitFutureResult<Optional<Set<UUID>>> submitUnclaimOrder(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(() -> {
            var result = unclaimOrder(order);
            invalidateFullOrder(order);
            return result;
        }, VIRTUAL);
    }

    /**
     * Releases a claimed order back to the market.
     * <p>
     * Items already delivered to the order are moved to the item returns of the workers who delivered them.
     *
     * @param order order to release
     * @return the workers who got items returned, or an empty optional if the order was not claimed
     */
    protected abstract Optional<Set<UUID>> unclaimOrder(SimpleOrder order);

    public BukkitFutureResult<List<ItemReturn>> retrieveItemReturns(UUID worker) {
        return CompletableBukkitFuture.supplyAsync(() -> getItemReturns(worker), VIRTUAL);
    }

    protected abstract List<ItemReturn> getItemReturns(UUID worker);

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

    public BukkitFutureResult<Boolean> submitItemReturn(UUID worker, ItemStack stack, int amount) {
        return CompletableBukkitFuture.supplyAsync(() -> addItemReturn(worker, stack, amount), VIRTUAL);
    }

    /**
     * Stores items which have to be handed to a worker later.
     *
     * @param worker worker owning the items
     * @param stack  item to store
     * @param amount amount of items
     * @return true if the items were stored
     */
    protected abstract boolean addItemReturn(UUID worker, ItemStack stack, int amount);

    public BukkitFutureResult<Integer> submitDelivery(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        return CompletableBukkitFuture.supplyAsync(() -> {
            var delivered = deliver(player, order, material, amount);
            invalidateFullOrder(order);
            return delivered;
        }, VIRTUAL);
    }

    /**
     * Records items delivered by a worker.
     * <p>
     * Nothing is recorded unless the order is claimed, and the amount is capped to the amount the order still misses.
     *
     * @param player   worker delivering the items
     * @param order    order receiving the items
     * @param material material of the items
     * @param amount   amount of items offered
     * @return amount of items which were recorded
     */
    protected abstract int deliver(OfflinePlayer player, SimpleOrder order, Material material, int amount);

    public BukkitFutureResult<Optional<SimpleOrder>> retrieveOrderById(int id) {
        return CompletableBukkitFuture.supplyAsync(() -> orderById(id), VIRTUAL);
    }

    protected abstract Optional<SimpleOrder> orderById(int id);

    public BukkitFutureResult<Optional<SimpleOrder>> retrieveCompanyOrderById(int id, int company) {
        return CompletableBukkitFuture.supplyAsync(() -> companyOrderById(id, company), VIRTUAL);
    }

    protected abstract Optional<SimpleOrder> companyOrderById(int id, int company);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByCompany(ISimpleCompany company, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersByCompany(company, min, max));
    }

    protected abstract List<SimpleOrder> ordersByCompany(ISimpleCompany company, OrderState min, OrderState max);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByPlayer(OfflinePlayer player, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersByPlayer(player, min, max), VIRTUAL);
    }

    protected abstract List<SimpleOrder> ordersByPlayer(OfflinePlayer player, OrderState min, OrderState max);

    public SimpleOrder buildSimpleOrder(Row row) throws SQLException {
        return new SimpleOrder(row.getInt("id"), row.get("owner_uuid", UUID_BYTES),
                row.getString("name"), row.getTimestamp("created")
                .toLocalDateTime(),
                row.getInt("company"), row.getTimestamp("last_update")
                .toLocalDateTime(),
                OrderState.byId(row.getInt("state")));
    }

    public BukkitFutureResult<List<FullOrder>> retrieveFullOrders(List<SimpleOrder> orders) {
        return CompletableBukkitFuture.supplyAsync(() -> toFullOrders(orders), VIRTUAL);
    }

    public List<FullOrder> toFullOrders(List<SimpleOrder> orders) {
        List<CompletableFuture<Optional<FullOrder>>> fullOrders = new ArrayList<>();
        for (var order : orders) {
            fullOrders.add(CompletableFuture.supplyAsync(() -> cacheFullOrder(order, () -> Optional.of(toFullOrder(order))), VIRTUAL));
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
            Companies.logger()
                    .log(Level.SEVERE, "Could not compute value for order " + id);
        }
        return Optional.empty();
    }

    protected abstract List<OrderContent> getOrderContent(SimpleOrder order);

    public BukkitFutureResult<FullOrder> retrieveFullOrder(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(() -> cacheFullOrder(order, () -> Optional.of(toFullOrder(order))).get(), VIRTUAL);
    }

    public BukkitFutureResult<Integer> retrievePlayerOrderCount(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerOrderCount(player), VIRTUAL);
    }

    protected abstract Integer getPlayerOrderCount(OfflinePlayer player);

    public BukkitFutureResult<Integer> retrieveCompanyOrderCount(ISimpleCompany company) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyOrderCount(company), VIRTUAL);
    }

    protected abstract Integer getCompanyOrderCount(ISimpleCompany company);

    public BukkitFutureResult<Set<UUID>> submitCompanyOrdersPurge(SimpleCompany profile) {
        return CompletableBukkitFuture.supplyAsync(() -> purgeCompanyOrders(profile), VIRTUAL);
    }

    /**
     * Releases all orders claimed by the company.
     *
     * @param profile company to release the orders of
     * @return the workers who got items returned
     */
    protected abstract Set<UUID> purgeCompanyOrders(SimpleCompany profile);

    public BukkitFutureResult<Void> submitOrderPurge(SimpleOrder order) {
        return CompletableBukkitFuture.runAsync(() -> purgeOrder(order), VIRTUAL);
    }

    protected abstract void purgeOrder(SimpleOrder order);

    public BukkitFutureResult<Boolean> submitOrderDeletion(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(() -> {
            var deleted = deleteOrder(order);
            invalidateFullOrder(order);
            return deleted;
        }, VIRTUAL);
    }

    /**
     * Deletes an order if it is still unclaimed.
     *
     * @param order order to delete
     * @return true if the order was unclaimed and is now deleted
     */
    protected abstract boolean deleteOrder(SimpleOrder order);

    public BukkitFutureResult<List<FullOrder>> retrieveOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max) {
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
        return CompletableBukkitFuture.supplyAsync(() -> {
            var price = findMaterialPrice(material).orElse(new MaterialPrice(material));
            materialPriceCache.put(material, price);
            return price;
        }, VIRTUAL);
    }

    protected abstract Optional<MaterialPrice> findMaterialPrice(String material);

    public void invalidateMaterialPriceCache() {
        materialPriceCache.invalidateAll();
    }

    protected abstract List<SimpleOrder> getDeadOrders(int hours);

    protected abstract List<ContentPart> getContentParts(SimpleOrder order, Material material);

    protected ItemStack toItemStack(String json) throws SQLException {
        try {
            return mapper.readValue(json, ItemStack.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Could not parse json to item stack", e);
        }
    }

    protected String toJson(ItemStack stack) {
        try {
            return mapper.writeValueAsString(stack);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not parse item stack to json", e);
        }
    }
}
