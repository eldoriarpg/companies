package de.eldoria.companies.data.repository.impl;

import de.chojo.sqlutil.conversion.UUIDConverter;
import de.chojo.sqlutil.wrapper.QueryBuilderConfig;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.ContentPart;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.OrderState;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public class MariaDbOrderData extends AOrderData {

    /**
     * Create a new QueryBuilderFactory
     *
     * @param dataSource      data source
     * @param plugin          plugin
     * @param executorService
     */
    public MariaDbOrderData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(QueryBuilderConfig.builder()
                .withExceptionHandler(e -> plugin.getLogger().log(Level.SEVERE, "Query exception", e))
                .build(), dataSource, executorService);
    }

    @Override
    protected void putOrder(OfflinePlayer player, FullOrder order) {
        var orderId = builder(Integer.class)
                .query("INSERT INTO orders(owner_uuid, name) VALUES(?,?) RETURNING id")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())).setString(order.name()))
                .readRow(rs -> rs.getInt(1))
                .firstSync().get();

        for (var content : order.contents()) {
            builder()
                    .query("INSERT INTO order_content(id, material, stack, amount, price) VALUES(?,?,?,?,?)")
                    .paramsBuilder(stmt -> stmt.setInt(orderId).setString(content.stack().getType().name())
                            .setString(toString(content.stack())).setInt(content.amount()).setDouble(content.price()))
                    .update()
                    .executeSync();
        }
        builder()
                .query("INSERT INTO order_states(id, state) VALUES(?, ?)")
                .paramsBuilder(stmt -> stmt.setInt(orderId).setInt(OrderState.UNCLAIMED.stateId()))
                .update()
                .executeSync();
    }

    @Override
    protected void updateOrderState(SimpleOrder order, OrderState state) {
        builder()
                .query("UPDATE order_states SET state = ? WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(state.stateId()).setInt(order.id()))
                .update().executeSync();
    }

    @Override
    protected List<SimpleOrder> getExpiredOrders(int hours) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, last_update, company, state, owner_uuid, name, created FROM order_states s LEFT JOIN orders o ON o.id = s.id WHERE last_update < NOW() - INTERVAL ? HOUR AND company IS NOT NULL AND state = ?")
                .paramsBuilder(stmt -> stmt.setInt(hours).setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected boolean claimOrder(SimpleCompany company, SimpleOrder order) {
        return builder()
                       .query("UPDATE order_states SET state = ?, company = ?, last_update = NOW() WHERE id = ? AND state = ?")
                       .paramsBuilder(stmt -> stmt.setInt(OrderState.CLAIMED.stateId()).setInt(company.id()).setInt(order.id()).setInt(OrderState.UNCLAIMED.stateId()))
                       .update().executeSync() > 0;
    }

    @Override
    protected void orderDelivered(SimpleOrder order) {
        builder()
                .query("UPDATE order_states SET state = ?, last_update = NOW() WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(OrderState.DELIVERED.stateId()).setInt(order.id()))
                .append()
                .query("DELETE FROM orders_delivered WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()))
                .update().executeSync();
    }

    @Override
    protected void unclaimOrder(SimpleOrder order) {
        builder()
                .query("UPDATE order_states SET state = ?, company = NULL, last_update = NOW() WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(OrderState.UNCLAIMED.stateId()).setInt(order.id()))
                .append()
                .query("DELETE FROM orders_delivered WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()))
                .update().executeSync();
    }

    @Override
    protected void deliver(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        builder()
                .query("INSERT INTO orders_delivered(id, worker_uuid, material, delivered) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE delivered = delivered + ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()).setBytes(UUIDConverter.convert(player.getUniqueId()))
                        .setString(material.name()).setInt(amount).setInt(amount))
                .update().executeSync();
    }

    @Override
    protected Optional<SimpleOrder> orderById(int id) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, owner_uuid, name, created, company, last_update, state FROM orders o LEFT JOIN order_states oc ON o.id = oc.id WHERE o.id = ?")
                .paramsBuilder(stmt -> stmt.setInt(id))
                .readRow(this::buildSimpleOrder)
                .firstSync();
    }

    @Override
    protected Optional<SimpleOrder> companyOrderById(int id, int company) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, owner_uuid, name, created, company, last_update, state FROM orders o LEFT JOIN order_states oc ON o.id = oc.id WHERE o.id = ? AND company = ?")
                .paramsBuilder(stmt -> stmt.setInt(id).setInt(company))
                .readRow(this::buildSimpleOrder)
                .firstSync();
    }

    @Override
    protected CompletableFuture<List<SimpleOrder>> ordersByCompany(SimpleCompany company, OrderState min, OrderState max) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, owner_uuid, name, created, company, last_update, state FROM orders o LEFT JOIN order_states oc ON o.id = oc.id WHERE oc.company = ? AND state >= ? AND state <= ?")
                .paramsBuilder(stmt -> stmt.setInt(company.id()).setInt(min.stateId()).setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .all(executorService());
    }

    @Override
    protected List<SimpleOrder> ordersByPlayer(OfflinePlayer player, OrderState min, OrderState max) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, owner_uuid, name, created, company, last_update, state FROM orders o LEFT JOIN order_states oc ON o.id = oc.id WHERE o.owner_uuid = ? AND state >= ? AND state <= ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())).setInt(min.stateId()).setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected List<SimpleOrder> ordersByState(OrderState min, OrderState max) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, owner_uuid, name, created, company, last_update, state FROM orders o LEFT JOIN order_states oc ON o.id = oc.id WHERE state >= ? AND state <= ?")
                .paramsBuilder(stmt -> stmt.setInt(min.stateId()).setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected List<SimpleOrder> ordersByName(String name, OrderState min, OrderState max) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, owner_uuid, name, created, company, last_update, state FROM orders o LEFT JOIN order_states oc ON o.id = oc.id WHERE name = ? AND state >= ? AND state <= ?")
                .paramsBuilder(stmt -> stmt.setString("%" + name + "%").setInt(min.stateId()).setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected List<SimpleOrder> ordersByMaterial(String material, OrderState min, OrderState max) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, owner_uuid, name, created, company, last_update, state FROM (SELECT id FROM order_content WHERE material LIKE ?) m LEFT JOIN orders o ON m.id = o.id LEFT JOIN order_states oc ON o.id = oc.id WHERE  state >= ? AND state <= ?")
                .paramsBuilder(stmt -> stmt.setString("%" + material + "%").setInt(min.stateId()).setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected Optional<Integer> getPlayerOrderCount(OfflinePlayer player) {
        return builder(Integer.class)
                .query("SELECT COUNT(1) AS count FROM orders LEFT JOIN order_states s ON orders.id = s.id WHERE owner_uuid = ? AND s.state < ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())).setInt(OrderState.DELIVERED.stateId()))
                .readRow(rs -> rs.getInt("count"))
                .firstSync();
    }

    @Override
    protected Integer getCompanyOrderCount(SimpleCompany company) {
        return builder(Integer.class)
                .query("SELECT COUNT(1) AS count FROM orders LEFT JOIN order_states s ON orders.id = s.id WHERE company = ? AND s.state = ?")
                .paramsBuilder(stmt -> stmt.setInt(company.id()).setInt(OrderState.CLAIMED.stateId()))
                .readRow(rs -> rs.getInt("count"))
                .firstSync().get();
    }

    @Override
    protected List<OrderContent> getOrderContent(SimpleOrder order) {
        var orderContents = builder(OrderContent.class)
                .query("SELECT material, stack, amount, price FROM order_content WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()))
                .readRow(rs -> new OrderContent(toItemStack(rs.getString("stack")), rs.getInt("amount"), rs.getFloat("price")))
                .allSync();

        for (var orderContent : orderContents) {
            orderContent.parts(getContentParts(order, orderContent.stack().getType()));
        }
        return orderContents;
    }

    @Override
    protected List<ContentPart> getContentParts(SimpleOrder order, Material material) {
        return builder(ContentPart.class)
                .query("SELECT worker_uuid, delivered FROM orders_delivered WHERE id = ? AND material = ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()).setString(material.name()))
                .readRow(rs -> new ContentPart(UUIDConverter.convert(rs.getBytes("uuid")), rs.getInt("amount")))
                .allSync();
    }

    @Override
    protected void deleteOrder(SimpleOrder order) {
        builder()
                .query("DELETE FROM orders WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()))
                .update().executeSync();
    }

    @Override
    protected List<FullOrder> getOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max) {
        var orders = builder(SimpleOrder.class)
                .query("SELECT o.id, owner_uuid, name, created, company, last_update, state FROM orders o " +
                       "LEFT JOIN (SELECT c.id,GROUP_CONCAT(c.material, ' ') AS materials, SUM(amount) AS amount, SUM(price) AS price FROM order_content c GROUP BY id) oc " +
                       "LEFT JOIN order_states os ON o.id = os.id " +
                       "ON o.id = oc.id " +
                       "WHERE name = ? " +
                       "AND oc.materials REGEXP ? " +
                       "AND oc.price > ? AND oc.price < ? " +
                       "AND oc.amount > ? AND oc.price < ?")
                .paramsBuilder(stmt -> stmt.setString("%" + searchQuery.name() + "%")
                        .setString(searchQuery.materialRegex())
                        .setDouble(searchQuery.minPrice()).setDouble(searchQuery.maxPrice())
                        .setInt(searchQuery.minOrderSize()).setInt(searchQuery.maxOrderSize()))
                .readRow(this::buildSimpleOrder)
                .allSync();
        var fullOrders = toFullOrders(orders);
        searchQuery.sort(fullOrders);
        return fullOrders;
    }

    @Override
    protected void purgeCompanyOrders(SimpleCompany profile) {
        for (var simpleOrder : ordersByCompany(profile, OrderState.CLAIMED, OrderState.CLAIMED).join()) {
            unclaimOrder(simpleOrder);
        }
    }
}
