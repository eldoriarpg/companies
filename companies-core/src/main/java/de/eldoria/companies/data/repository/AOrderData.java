/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.chojo.sadu.base.QueryFactory;
import de.chojo.sadu.wrapper.QueryBuilderConfig;
import de.chojo.sadu.wrapper.util.Row;
import de.eldoria.companies.Companies;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.ContentPart;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.util.SerializeContainer;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import de.eldoria.eldoutilities.threading.futures.FutureResult;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

@SuppressWarnings("UnusedReturnValue")
public abstract class AOrderData extends QueryFactory {
    private final ExecutorService executorService;
    private final Cache<Integer, Optional<FullOrder>> fullOrderCache = CacheBuilder.newBuilder()
                                                                                   .expireAfterAccess(5L, TimeUnit.MINUTES)
                                                                                   .build();
    private final Cache<String, MaterialPrice> materialPriceCache = CacheBuilder.newBuilder()
                                                                                .expireAfterAccess(1L, TimeUnit.HOURS)
                                                                                .build();

    public AOrderData(Plugin plugin, DataSource dataSource, ExecutorService executorService) {
        super(dataSource, QueryBuilderConfig.builder()
                .withExceptionHandler(e -> plugin.getLogger()
                                                 .log(Level.SEVERE, "Query exception", e))
                .build());
        this.executorService = executorService;
    }

    // Order Management
    public BukkitFutureResult<Void> submitOrder(OfflinePlayer player, FullOrder order) {
        return CompletableBukkitFuture.runAsync(() -> putOrder(player, order), executorService);
    }

    protected abstract void putOrder(OfflinePlayer player, FullOrder order);

    public BukkitFutureResult<Void> submitOrderStateUpdate(SimpleOrder order, OrderState state) {
        return CompletableBukkitFuture.runAsync(() -> {
            updateOrderState(order, state);
            invalidateFullOrder(order);
        }, executorService);
    }

    protected abstract void updateOrderState(SimpleOrder order, OrderState state);

    protected void invalidateFullOrder(SimpleOrder order) {
        invalidateFullOrder(order.id());
    }

    protected void invalidateFullOrder(int id) {
        fullOrderCache.invalidate(id);
    }

    public CompletableFuture<List<SimpleOrder>> retrieveExpiredOrders(int hours) {
        return CompletableFuture.supplyAsync(() -> getExpiredOrders(hours), executorService);
    }

    protected abstract List<SimpleOrder> getExpiredOrders(int hours);

    public CompletableFuture<List<SimpleOrder>> retrieveDeadOrders(int hours) {
        return CompletableFuture.supplyAsync(() -> getExpiredOrders(hours), executorService);
    }

    public CompletableFuture<List<SimpleOrder>> retrieveExpiredOrdersByCompany(int hours, SimpleCompany company) {
        return CompletableFuture.supplyAsync(() -> getExpiredOrdersByCompany(hours, company), executorService);
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

    public FutureResult<Void> submitOrderDelivered(SimpleOrder order) {
        return CompletableBukkitFuture.runAsync(() -> {
            orderDelivered(order);
            invalidateFullOrder(order);
        }, executorService);
    }

    protected abstract void orderDelivered(SimpleOrder order);

    public FutureResult<Void> submitUnclaimOrder(SimpleOrder order) {
        return CompletableBukkitFuture.runAsync(() -> {
            unclaimOrder(order);
            invalidateFullOrder(order);
        });
    }

    protected abstract void unclaimOrder(SimpleOrder order);

    public BukkitFutureResult<Void> submitDelivery(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        return CompletableBukkitFuture.runAsync(() -> {
            deliver(player, order, material, amount);
            invalidateFullOrder(order);
        }, executorService);
    }

    protected abstract void deliver(OfflinePlayer player, SimpleOrder order, Material material, int amount);

    public BukkitFutureResult<Optional<SimpleOrder>> retrieveOrderById(int id) {
        return CompletableBukkitFuture.supplyAsync(() -> orderById(id), executorService);
    }

    protected abstract Optional<SimpleOrder> orderById(int id);

    public BukkitFutureResult<Optional<SimpleOrder>> retrieveCompanyOrderById(int id, int company) {
        return CompletableBukkitFuture.supplyAsync(() -> companyOrderById(id, company), executorService);
    }

    protected abstract Optional<SimpleOrder> companyOrderById(int id, int company);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByCompany(ISimpleCompany company, OrderState min, OrderState max) {
        return BukkitFutureResult.of(ordersByCompany(company, min, max));
    }

    protected abstract CompletableFuture<List<SimpleOrder>> ordersByCompany(ISimpleCompany company, OrderState min, OrderState max);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByPlayer(OfflinePlayer player, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersByPlayer(player, min, max), executorService);
    }

    protected abstract List<SimpleOrder> ordersByPlayer(OfflinePlayer player, OrderState min, OrderState max);

    public SimpleOrder buildSimpleOrder(Row row) throws SQLException {
        return new SimpleOrder(row.getInt("id"), row.getUuidFromBytes("owner_uuid"),
                row.getString("name"), row.getTimestamp("created")
                                          .toLocalDateTime(),
                row.getInt("company"), row.getTimestamp("last_update")
                                          .toLocalDateTime(),
                OrderState.byId(row.getInt("state")));
    }

    public BukkitFutureResult<List<FullOrder>> retrieveFullOrders(List<SimpleOrder> orders) {
        return CompletableBukkitFuture.supplyAsync(() -> toFullOrders(orders), executorService);
    }

    public List<FullOrder> toFullOrders(List<SimpleOrder> orders) {
        List<CompletableFuture<Optional<FullOrder>>> fullOrders = new ArrayList<>();
        for (var order : orders) {
            fullOrders.add(CompletableFuture.supplyAsync(() -> cacheFullOrder(order, () -> Optional.of(toFullOrder(order))), executorService));
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
        return CompletableBukkitFuture.supplyAsync(() -> cacheFullOrder(order, () -> Optional.of(toFullOrder(order))).get(), executorService);
    }

    public BukkitFutureResult<Integer> retrievePlayerOrderCount(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerOrderCount(player), executorService);
    }

    protected abstract Integer getPlayerOrderCount(OfflinePlayer player);

    public BukkitFutureResult<Integer> retrieveCompanyOrderCount(ISimpleCompany company) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyOrderCount(company), executorService);
    }

    protected abstract Integer getCompanyOrderCount(ISimpleCompany company);

    public BukkitFutureResult<Void> submitCompanyOrdersPurge(SimpleCompany profile) {
        return CompletableBukkitFuture.runAsync(() -> purgeCompanyOrders(profile), executorService);
    }

    protected abstract void purgeCompanyOrders(SimpleCompany profile);

    public BukkitFutureResult<Void> submitOrderPurge(SimpleOrder order) {
        return CompletableBukkitFuture.runAsync(() -> purgeOrder(order), executorService);
    }

    protected abstract void purgeOrder(SimpleOrder order);

    public BukkitFutureResult<Void> submitOrderDeletion(SimpleOrder order) {
        return CompletableBukkitFuture.runAsync(() -> {
            invalidateFullOrder(order);
            deleteOrder(order);
        });
    }

    protected abstract void deleteOrder(SimpleOrder order);

    public BukkitFutureResult<List<FullOrder>> retrieveOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> getOrdersByQuery(searchQuery, min, max), executorService);
    }

    protected abstract List<FullOrder> getOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max);

    public FutureResult<Void> submitMaterialPriceRefresh() {
        return CompletableBukkitFuture.runAsync(this::refreshMaterialPrices, executorService);
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
        }, executorService);
    }

    protected abstract Optional<MaterialPrice> findMaterialPrice(String material);

    public void invalidateMaterialPriceCache() {
        materialPriceCache.invalidateAll();
    }

    protected abstract List<SimpleOrder> getDeadOrders(int hours);

    protected abstract List<ContentPart> getContentParts(SimpleOrder order, Material material);

    protected ItemStack toItemStack(String json) {
        return SerializeContainer.deserializeFromJson(json, ItemStack.class);
    }

    protected String toString(ItemStack stack) {
        return SerializeContainer.serializeToJson(stack);
    }

    protected ExecutorService executorService() {
        return executorService;
    }
}
