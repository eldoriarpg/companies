package de.eldoria.companies.data.repository.impl.postgres;

import de.chojo.sqlutil.conversion.UUIDConverter;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.data.repository.impl.mariadb.MariaDbOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.OrderState;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class PostgresOrderData extends MariaDbOrderData {
    /**
     * Create a new QueryBuilderFactory
     *
     * @param dataSource      data source
     * @param plugin          plugin
     * @param executorService executor service for future handling
     */
    public PostgresOrderData(DataSource dataSource, Plugin plugin, ExecutorService executorService) {
        super(dataSource, plugin, executorService);
    }

    @Override
    protected List<SimpleOrder> getExpiredOrders(int hours) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, last_update, company, state, owner_uuid, name, created FROM order_states s LEFT JOIN orders o ON o.id = s.id WHERE last_update < now() - (? || ' HOUR')::INTERVAL AND company IS NOT NULL AND state = ? ORDER BY last_update")
                .paramsBuilder(stmt -> stmt.setInt(hours).setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected List<SimpleOrder> getExpiredOrdersByCompany(int hours, SimpleCompany company) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, last_update, company, state, owner_uuid, name, created FROM order_states s LEFT JOIN orders o ON o.id = s.id WHERE last_update < now() - (? || ' HOUR')::INTERVAL AND company = ? AND state = ? ORDER BY last_update")
                .paramsBuilder(stmt -> stmt.setInt(hours).setInt(company.id()).setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected List<FullOrder> getOrdersByQuery(SearchQuery searchQuery, OrderState min, OrderState max) {
        var orders = builder(SimpleOrder.class)
                .query("SELECT o.id, o.owner_uuid, o.name, o.created, os.company, os.last_update, os.state " +
                       "FROM orders o " +
                       "         LEFT JOIN (SELECT c.id, string_agg(c.material, ' ') AS materials, SUM(amount) AS amount, SUM(price) AS price " +
                       "                    FROM order_content c " +
                       "                    GROUP BY id) oc " +
                       "                   ON o.id = oc.id " +
                       "         LEFT JOIN order_states os " +
                       "                   ON o.id = os.id " +
                       "WHERE o.name ~~* ? " +
                       "  AND oc.materials ~* ? " +
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
    protected void deliver(OfflinePlayer player, SimpleOrder order, Material material, int amount) {
        builder()
                .query("INSERT INTO orders_delivered(id, worker_uuid, material, delivered) VALUES(?,?,?,?) ON CONFLICT(id, worker_uuid, material) DO UPDATE SET delivered = delivered + excluded.delivered")
                .paramsBuilder(stmt -> stmt.setInt(order.id()).setBytes(UUIDConverter.convert(player.getUniqueId()))
                        .setString(material.name()).setInt(amount))
                .update()
                .executeSync();
    }

    @Override
    public void refreshMaterialPrices() {
        builder()
                .queryWithoutParams("REFRESH MATERIALIZED VIEW material_price;")
                .update().executeSync();
    }
}
