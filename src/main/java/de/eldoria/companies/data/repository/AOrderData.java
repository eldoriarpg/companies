package de.eldoria.companies.data.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.chojo.sqlutil.base.QueryFactoryHolder;
import de.chojo.sqlutil.conversion.UUIDConverter;
import de.chojo.sqlutil.wrapper.QueryBuilderConfig;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.ContentPart;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.OrderState;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public abstract class AOrderData extends QueryFactoryHolder {
    private static final Gson GSON = new GsonBuilder().create();
    private final ExecutorService executorService;

    public AOrderData(QueryBuilderConfig config, DataSource dataSource, ExecutorService executorService) {
        super(dataSource, config);
        this.executorService = executorService;
    }

    // Order Management
    public BukkitFutureResult<Void> submitOrder(OfflinePlayer player, FullOrder order) {
        return CompletableBukkitFuture.runAsync(() -> putOrder(player, order), executorService);
    }

    protected abstract void putOrder(OfflinePlayer player, FullOrder order);

    public BukkitFutureResult<Void> submitOrderStateUpdate(SimpleOrder order, OrderState state) {
        return CompletableBukkitFuture.runAsync(() -> updateOrderState(order, state), executorService);
    }

    protected abstract void updateOrderState(SimpleOrder order, OrderState state);

    public CompletableFuture<List<SimpleOrder>> retrieveExpiredOrders(int hours) {
        return CompletableFuture.supplyAsync(() -> getExpiredOrders(hours), executorService);
    }

    protected abstract List<SimpleOrder> getExpiredOrders(int hours);

    public BukkitFutureResult<Boolean> submitOrderClaim(SimpleCompany company, SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(() -> claimOrder(company, order));
    }

    protected abstract boolean claimOrder(SimpleCompany company, SimpleOrder order);

    public void submitOrderDelivered(SimpleOrder order) {
        CompletableBukkitFuture.runAsync(() -> orderDelivered(order), executorService);
    }

    protected abstract void orderDelivered(SimpleOrder order);

    public void submitUnclaimOrder(SimpleOrder order) {
        CompletableFuture.runAsync(() -> unclaimOrder(order));
    }

    protected abstract void unclaimOrder(SimpleOrder order);

    public BukkitFutureResult<Void> submitDelivery(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        return CompletableBukkitFuture.runAsync(() -> deliver(player, order, material, amount), executorService);
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

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByCompany(SimpleCompany company, OrderState min, OrderState max) {
        return BukkitFutureResult.of(ordersByCompany(company, min, max));
    }

    protected abstract CompletableFuture<List<SimpleOrder>> ordersByCompany(SimpleCompany company, OrderState min, OrderState max);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByPlayer(OfflinePlayer player, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersByPlayer(player, min, max), executorService);
    }

    protected abstract List<SimpleOrder> ordersByPlayer(OfflinePlayer player, OrderState min, OrderState max);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByState(OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersByState(min, max), executorService);
    }

    protected abstract List<SimpleOrder> ordersByState(OrderState min, OrderState max);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByName(String name, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersByName(name, min, max), executorService);
    }

    protected abstract List<SimpleOrder> ordersByName(String name, OrderState min, OrderState max);

    public BukkitFutureResult<List<SimpleOrder>> retrieveOrdersByMaterial(String material, OrderState min, OrderState max) {
        return CompletableBukkitFuture.supplyAsync(() -> ordersByMaterial(material, min, max), executorService);
    }

    protected abstract List<SimpleOrder> ordersByMaterial(String material, OrderState min, OrderState max);

    public SimpleOrder buildSimpleOrder(ResultSet rs) throws SQLException {
        return new SimpleOrder(rs.getInt("id"), UUIDConverter.convert(rs.getBytes("owner_uuid")),
                rs.getString("name"), rs.getTimestamp("last_update").toLocalDateTime(),
                rs.getInt("company"), rs.getTimestamp("created").toLocalDateTime(),
                OrderState.byId(rs.getInt("state")));
    }

    public BukkitFutureResult<List<FullOrder>> retrieveFullOrders(List<SimpleOrder> orders) {
        return CompletableBukkitFuture.supplyAsync(() -> toFullOrders(orders), executorService);
    }

    public List<FullOrder> toFullOrders(List<SimpleOrder> orders) {
        List<CompletableFuture<FullOrder>> fullOrders = new ArrayList<>();
        for (var order : orders) {
            fullOrders.add(CompletableFuture.supplyAsync(() -> toFullOrder(order), executorService));
        }
        CompletableFuture.allOf(fullOrders.toArray(CompletableFuture[]::new));
        return fullOrders.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    public BukkitFutureResult<FullOrder> retrieveFullOrder(SimpleOrder order) {
        return CompletableBukkitFuture.supplyAsync(() -> toFullOrder(order), executorService);
    }

    public FullOrder toFullOrder(SimpleOrder order) {
        var orderContent = getOrderContent(order);
        return order.toFullOrder(orderContent);
    }

    public BukkitFutureResult<Optional<Integer>> retrievePlayerOrderCount(OfflinePlayer player) {
        return CompletableBukkitFuture.supplyAsync(() -> getPlayerOrderCount(player), executorService);
    }

    protected abstract Optional<Integer> getPlayerOrderCount(OfflinePlayer player);

    public BukkitFutureResult<Integer> retrieveCompanyOrderCount(SimpleCompany company) {
        return CompletableBukkitFuture.supplyAsync(() -> getCompanyOrderCount(company), executorService);
    }

    protected abstract Integer getCompanyOrderCount(SimpleCompany company);

    protected abstract List<OrderContent> getOrderContent(SimpleOrder order);

    protected abstract List<ContentPart> getContentParts(SimpleOrder order, Material material);

    protected ItemStack toItemStack(String map) {
        return GSON.fromJson(map, ItemStackContainer.class).toItemStack();
    }

    protected String toString(ItemStack stack) {
        return GSON.toJson(ItemStackContainer.create(stack));
    }

    protected abstract void purgeCompanyOrders(SimpleCompany profile);

    public BukkitFutureResult<Void> submitCompanyOrdersPurge(SimpleCompany profile) {
       return CompletableBukkitFuture.runAsync(() -> purgeCompanyOrders(profile), executorService);
    }

    public BukkitFutureResult<Void> submitOrderDeletion(SimpleOrder order) {
        return CompletableBukkitFuture.runAsync(() -> deleteOrder(order));
    }

    protected abstract void deleteOrder(SimpleOrder order);

    protected static class ItemStackContainer {
        private final Map<String, Object> data;

        private ItemStackContainer(Map<String, Object> data) {
            this.data = data;
        }

        public static ItemStackContainer create(ItemStack stack) {
            return new ItemStackContainer(stack.serialize());
        }

        public ItemStack toItemStack() {
            return ItemStack.deserialize(data);
        }
    }

    protected ExecutorService executorService() {
        return executorService;
    }
}
