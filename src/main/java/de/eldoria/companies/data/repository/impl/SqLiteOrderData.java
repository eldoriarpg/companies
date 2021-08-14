package de.eldoria.companies.data.repository.impl;

import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.orders.OrderState;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.ExecutorService;

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
    protected List<SimpleOrder> getExpiredOrders(int hours) {
        return builder(SimpleOrder.class)
                .query("SELECT o.id, last_update, company, state, owner_uuid, name, created FROM order_states s LEFT JOIN orders o ON o.id = s.id WHERE last_update < datetime(current_timestamp, '-' || ? || ' HOUR') AND company IS NOT NULL AND state = ?")
                .paramsBuilder(stmt -> stmt.setInt(hours).setInt(OrderState.CLAIMED.stateId()))
                .readRow(this::buildSimpleOrder)
                .allSync();
    }

    @Override
    protected boolean claimOrder(SimpleCompany company, SimpleOrder order) {
        return builder()
                       .query("UPDATE order_states SET state = ?, company = ?, last_update = current_timestamp WHERE id = ? AND state = ?")
                       .paramsBuilder(stmt -> stmt.setInt(OrderState.CLAIMED.stateId()).setInt(company.id()).setInt(order.id()).setInt(OrderState.UNCLAIMED.stateId()))
                       .update().executeSync() > 0;
    }

    @Override
    protected void orderDelivered(SimpleOrder order) {
        builder()
                .query("UPDATE order_states SET state = ?, last_update = current_timestamp WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(OrderState.DELIVERED.stateId()).setInt(order.id()))
                .append()
                .query("DELETE FROM orders_delivered WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()))
                .update().executeSync();
    }

    @Override
    protected void unclaimOrder(SimpleOrder order) {
        builder()
                .query("UPDATE order_states SET state = ?, company = NULL, last_update = current_timestamp WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(OrderState.UNCLAIMED.stateId()).setInt(order.id()))
                .append()
                .query("DELETE FROM orders_delivered WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(order.id()))
                .update().executeSync();
    }
}
