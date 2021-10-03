package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sqlutil.conversion.UUIDConverter;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.ContentPart;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.components.order.OrderState;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class MariaDbOrderData extends AOrderData {

    /**
     * Create a new QueryBuilderFactory
     *
     * @param dataSource      data source
     * @param plugin          plugin
     * @param executorService executor service for future handling
     */
    public MariaDbOrderData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(plugin, dataSource, executorService);
    }

    @Override
    protected void putOrder(OfflinePlayer player, FullOrder order) {
        var orderId = builder(Integer.class)
                .query("INSERT INTO orders(owner_uuid, name) VALUES(?,?) RETURNING id")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())).setString(order.name()))
                .readRow(rs -> rs.getInt(1))
                .firstSync().get();

        var builder = builder();
        for (var content : order.contents()) {
            builder.query("INSERT INTO order_content(id, material, stack, amount, price) VALUES(?,?,?,?,?)")
                    .paramsBuilder(stmt -> stmt.setInt(orderId).setString(content.stack().getType().name())
                            .setString(toString(content.stack())).setInt(content.amount()).setDouble(content.price()))
                    .append();
        }
        builder.query("INSERT INTO order_states(id, state) VALUES(?, ?)")
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
                .query("SELECT o.id, last_update, company, state, owner_uuid, name, created FROM order_states s LEFT JOIN orders o ON o.id = s.id WHERE last_update < NOW() - INTERVAL ? HOUR AND company IS NOT NULL AND state = ? ORDER BY last_update")
                .paramsBuilder(stmt -> stmt.setInt(hours).setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected List<SimpleOrder> getDeadOrders(int hours) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, last_update, company, state, owner_uuid, name, created FROM order_states s LEFT JOIN orders o ON o.id = s.id WHERE last_update < NOW() - INTERVAL ? HOUR AND company IS NOT NULL AND state = ? ORDER BY last_update")
                .paramsBuilder(stmt -> stmt.setInt(hours).setInt(OrderState.UNCLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected List<SimpleOrder> getExpiredOrdersByCompany(int hours, SimpleCompany company) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, last_update, company, state, owner_uuid, name, created FROM order_states s LEFT JOIN orders o ON o.id = s.id WHERE last_update < NOW() - INTERVAL ? HOUR AND company = ? AND state = ? ORDER BY last_update")
                .paramsBuilder(stmt -> stmt.setInt(hours).setInt(company.id()).setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected boolean claimOrder(SimpleCompany company, SimpleOrder order) {
        return builder()
                       .query("UPDATE order_states SET state = ?, company = ?, last_update = CURRENT_TIMESTAMP WHERE id = ? AND state = ?")
                       .paramsBuilder(stmt -> stmt.setInt(OrderState.CLAIMED.stateId()).setInt(company.id()).setInt(order.id()).setInt(OrderState.UNCLAIMED.stateId()))
                       .update().executeSync() > 0;
    }

    @Override
    protected void orderDelivered(SimpleOrder order) {
        builder()
                .query("UPDATE order_states SET state = ?, last_update = CURRENT_TIMESTAMP WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(OrderState.DELIVERED.stateId()).setInt(order.id()))
                .append()
                .query("DELETE FROM orders_delivered WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()))
                .update().executeSync();
    }

    @Override
    protected void unclaimOrder(SimpleOrder order) {
        builder()
                .query("UPDATE order_states SET state = ?, company = NULL, last_update = CURRENT_TIMESTAMP WHERE id = ?")
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
                .update()
                .executeSync();
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
    protected CompletableFuture<List<SimpleOrder>> ordersByCompany(ISimpleCompany company, OrderState min, OrderState max) {
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
    protected Integer getPlayerOrderCount(OfflinePlayer player) {
        return builder(Integer.class)
                .query("SELECT COUNT(1) AS count FROM orders LEFT JOIN order_states s ON orders.id = s.id WHERE owner_uuid = ? AND s.state < ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())).setInt(OrderState.DELIVERED.stateId()))
                .readRow(rs -> rs.getInt("count"))
                .firstSync().get();
    }

    @Override
    protected Integer getCompanyOrderCount(ISimpleCompany company) {
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
                .readRow(rs -> new OrderContent(toItemStack(rs.getString("stack")), rs.getInt("amount"), rs.getDouble("price")))
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
                .readRow(rs -> new ContentPart(UUIDConverter.convert(rs.getBytes("worker_uuid")), rs.getInt("delivered")))
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
                .query("SELECT o.id, o.owner_uuid, o.name, o.created, os.company, os.last_update, os.state " +
                       "FROM orders o " +
                       "         LEFT JOIN (SELECT c.id, GROUP_CONCAT(c.material, ' ') AS materials, SUM(amount) AS amount, SUM(price) AS price " +
                       "                    FROM order_content c " +
                       "                    GROUP BY id) oc " +
                       "                   ON o.id = oc.id " +
                       "         LEFT JOIN order_states os " +
                       "                   ON o.id = os.id " +
                       "WHERE o.name LIKE ? " +
                       "  AND oc.materials REGEXP ? " +
                       "  AND oc.price >= ? " +
                       "  AND oc.price <= ? " +
                       "  AND oc.amount >= ? " +
                       "  AND oc.amount <= ? " +
                       "  AND os.state >= ? AND os.state <= ? ")
                .paramsBuilder(stmt -> stmt.setString("%" + searchQuery.name() + "%")
                        .setString(searchQuery.materialRegex())
                        .setDouble(searchQuery.minPrice()).setDouble(searchQuery.maxPrice())
                        .setInt(searchQuery.minOrderSize()).setInt(searchQuery.maxOrderSize())
                        .setInt(min.stateId()).setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
        var fullOrders = toFullOrders(orders);
        searchQuery.sort(fullOrders);
        return fullOrders;
    }

    @Override
    protected Optional<MaterialPrice> findMaterialPrice(String material) {
        return builder(MaterialPrice.class).query("SELECT material, avg_price, min_price, max_price FROM material_price WHERE material = ?")
                .paramsBuilder(stmt -> stmt.setString(material.toLowerCase(Locale.ROOT)))
                .readRow(rs -> new MaterialPrice(material.toLowerCase(Locale.ROOT), rs.getDouble("avg_price"),
                        rs.getDouble("min_price"), rs.getDouble("max_price")))
                .firstSync();
    }

    @Override
    protected void purgeCompanyOrders(SimpleCompany profile) {
        for (var simpleOrder : ordersByCompany(profile, OrderState.CLAIMED, OrderState.CLAIMED).join()) {
            unclaimOrder(simpleOrder);
        }
    }

    @Override
    protected void purgeOrder(SimpleOrder order) {
        builder().query("DELETE FROM orders WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()))
                .delete()
                .executeSync();
    }

    @Override
    public void refreshMaterialPrices() {
        builder()
                .queryWithoutParams("INSERT INTO material_price(material, avg_price, min_price, max_price) " +
                                    "SELECT material, avg_price, min_price, max_price " +
                                    "FROM (SELECT c.material, " +
                                    "             AVG(c.price / c.amount) AS avg_price, " +
                                    "             MIN(c.price / c.amount) AS min_price, " +
                                    "             MAX(c.price / c.amount) AS max_price " +
                                    "      FROM (SELECT ROW_NUMBER() OVER (PARTITION BY material ORDER BY last_update DESC) AS id, material, amount, price, last_update " +
                                    "            FROM order_content c " +
                                    "                     LEFT JOIN order_states s ON c.id = s.id " +
                                    "            WHERE s.state >= 200) c " +
                                    "      WHERE c.id < 100 " +
                                    "      GROUP BY c.material) avg " +
                                    "WHERE TRUE " +
                                    "ON DUPLICATE KEY UPDATE avg_price = avg.avg_price;")
                .update().executeSync();
    }
}
