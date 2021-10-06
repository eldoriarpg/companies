package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sqlutil.conversion.UUIDConverter;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class SqLiteOrderData extends MariaDbOrderData {

    /**
     * Create a new QueryBuilderFactory
     *
     * @param dataSource      data source
     * @param plugin          plugin
     * @param executorService
     */
    public SqLiteOrderData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(dataSource, plugin, executorService);
    }

    @Override
    protected void putOrder(OfflinePlayer player, FullOrder order) {
        var orderId = builder(Integer.class)
                .query("INSERT INTO orders(owner_uuid, name) VALUES(?,?)")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())).setString(order.name()))
                .append()
                //Workaround until jdbc is updated to 3.35 which will support RETURNING
                .query("SELECT id FROM orders WHERE owner_uuid = ? ORDER BY created DESC")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
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
    protected List<SimpleOrder> getExpiredOrders(int hours) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, last_update, company, state, owner_uuid, name, created FROM order_states s LEFT JOIN orders o ON o.id = s.id WHERE last_update < datetime(CURRENT_TIMESTAMP, '-' || ? || ' HOUR') AND company IS NOT NULL AND state = ? ORDER BY last_update")
                .paramsBuilder(stmt -> stmt.setInt(hours).setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected List<SimpleOrder> getExpiredOrdersByCompany(int hours, SimpleCompany company) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, last_update, company, state, owner_uuid, name, created FROM order_states s LEFT JOIN orders o ON o.id = s.id WHERE last_update < datetime(CURRENT_TIMESTAMP, '-' || ? || ' HOUR') AND company = ? AND state = ? ORDER BY last_update")
                .paramsBuilder(stmt -> stmt.setInt(hours).setInt(company.id()).setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    public SimpleOrder buildSimpleOrder(ResultSet rs) throws SQLException {
        return new SimpleOrder(rs.getInt("id"), UUIDConverter.convert(rs.getBytes("owner_uuid")),
                // Sqlite cant read its own timestamp as timestamp. We need to parse them
                rs.getString("name"), SqLiteAdapter.getTimestamp(rs, "created"),
                rs.getInt("company"), SqLiteAdapter.getTimestamp(rs, "last_update"),
                OrderState.byId(rs.getInt("state")));
    }

    @Override
    protected void deliver(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        builder()
                .query("INSERT INTO orders_delivered(id, worker_uuid, material, delivered) VALUES(?,?,?,?) ON CONFLICT(id, worker_uuid, material) DO UPDATE SET delivered = delivered + excluded.delivered")
                .paramsBuilder(stmt -> stmt.setInt(order.id()).setBytes(UUIDConverter.convert(player.getUniqueId()))
                        .setString(material.name()).setInt(amount))
                .update()
                .executeSync();
    }

    @Override
    protected List<FullOrder> getOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max) {
        List<List<Integer>> results = new ArrayList<>();
        Set<Integer> materialMatch;
        var materialFilter = !searchQuery.materials().isEmpty();
        // This is pain. SqLite doesnt support regex from stock so we need to do some dirty looping.
        if (materialFilter) {
            for (var material : searchQuery.materials()) {
                var ids = builder(Integer.class)
                        .query("SELECT DISTINCT c.id FROM order_content c LEFT JOIN order_states s ON s.id = c.id WHERE material LIKE ? AND s.state >= ? AND s.state <= ? ")
                        .paramsBuilder(stmt -> stmt.setString(searchQuery.isExactMatch() ? material : "%" + material + "%"))
                        .readRow(rs -> rs.getInt(1))
                        .allSync();
                results.add(ids);
            }
            if (searchQuery.isAnyMaterial()) {
                materialMatch = results.stream().flatMap(List::stream).collect(Collectors.toSet());
            } else {
                var first = results.remove(0);
                if (!results.isEmpty()) {
                    for (var result : results) {
                        first.retainAll(result);
                    }
                }
                materialMatch = new HashSet<>(first);
            }
        } else {
            materialMatch = new HashSet<>();
        }

        var orders = builder(SimpleOrder.class)
                .query("SELECT o.id, o.owner_uuid, o.name, o.created, os.company, os.last_update, os.state " +
                       "FROM orders o " +
                       "         LEFT JOIN (SELECT c.id, SUM(amount) AS amount, SUM(price) AS price " +
                       "                    FROM order_content c " +
                       "                    GROUP BY id) oc " +
                       "                   ON o.id = oc.id " +
                       "         LEFT JOIN order_states os " +
                       "                   ON o.id = os.id " +
                       "WHERE o.name LIKE ? " +
                       "  AND oc.price >= ? " +
                       "  AND oc.price <= ? " +
                       "  AND oc.amount >= ? " +
                       "  AND oc.amount <= ? " +
                       "  AND os.state >= ? AND os.state <= ? ")
                .paramsBuilder(stmt -> stmt.setString("%" + searchQuery.name() + "%")
                        .setDouble(searchQuery.minPrice()).setDouble(searchQuery.maxPrice())
                        .setInt(searchQuery.minOrderSize()).setInt(searchQuery.maxOrderSize())
                        .setInt(min.stateId()).setInt(max.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
        orders = orders.stream().filter(order -> !materialFilter || materialMatch.contains(order.id())).collect(Collectors.toList());
        var fullOrders = toFullOrders(orders);
        searchQuery.sort(fullOrders);
        return fullOrders;
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
                                    "ON CONFLICT(material) DO UPDATE SET avg_price = excluded.avg_price;")
                .update().executeSync();
    }
}
