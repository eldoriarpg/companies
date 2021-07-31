package de.eldoria.companies.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.chojo.sqlutil.conversion.UUIDConverter;
import de.chojo.sqlutil.wrapper.QueryBuilderConfig;
import de.chojo.sqlutil.wrapper.QueryBuilderFactory;
import de.eldoria.companies.data.wrapper.company.Company;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.order.ContentPart;
import de.eldoria.companies.data.wrapper.order.Order;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.oders.OrderState;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class CompanyData extends QueryBuilderFactory {
    private static final Gson GSON = new GsonBuilder().create();

    public CompanyData(DataSource dataSource, Plugin plugin) {
        super(QueryBuilderConfig.builder()
                .withExceptionHandler(e -> plugin.getLogger().log(Level.SEVERE, "Query exception", e))
                .build(), dataSource);
    }

    // Order Management

    private void placeOrder(OfflinePlayer player, Order order) {
        var orderId = builder(Long.class)
                .query("INSERT INTO orders(owner_uuid, name) VALUES(?,?) RETURNING id")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())).setString(order.name()))
                .readRow(rs -> rs.getLong("id"))
                .firstSync().get();

        for (var content : order.contents()) {
            builder()
                    .query("INSERT INTO order_content(id, material, stack, amount, price) VALUES(?,?,?,?,?)")
                    .paramsBuilder(stmt -> stmt.setLong(orderId).setString(content.stack().getType().name())
                            .setString(toString(content.stack())).setInt(content.amount()).setFloat(content.price()))
                    .update();
        }
    }

    private void updateMember(CompanyMember member) {
        builder()
                .query("REPLACE company_member(id, uuid, permission) VALUES(?,?,?)")
                .paramsBuilder(stmt -> stmt.setInt(member.company()).setBytes(UUIDConverter.convert(member.uuid())).setLong(member.permission()))
                .update().executeSync();
    }

    private void updateOrderState(Order order, OrderState state) {
        builder()
                .query("UPDATE order_states SET state = ? WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(state.stateId()).setLong(order.id()))
                .update().executeSync();
    }

    private List<Long> getExpiredOrders(int days) {
        return builder(Long.class).query("SELECT id FROM order_states WHERE claimed > ? AND company IS NOT NULL")
                .emptyParams()
                .readRow(rs -> rs.getLong("id"))
                .allSync();
    }

    private void claimOrder(SimpleCompany company, Order order) {
        builder()
                .query("UPDATE order_states SET state = ?, company = ?, claimed = NOW() WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(OrderState.CLAIMED.stateId()).setInt(company.id()).setLong(order.id()))
                .update().executeSync();
    }

    private void orderDelivered(SimpleCompany company, Order order) {
        builder()
                .query("UPDATE order_states SET state = ?, claimed = NULL WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(OrderState.DELIVERED.stateId()).setLong(order.id()))
                .update().executeSync();
    }

    private void unclaimOrder(Order order) {
        builder()
                .query("UPDATE order_states SET state = ?, company = NULL, claimed = NULL WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(OrderState.UNCLAIMED.stateId()))
                .update().executeSync();
    }

    private void deliver(OfflinePlayer player, long orderId, Material material, int amount) {
        builder()
                .query("INSERT INTO orders_delivered(id, worker_uuid, material, delivered) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE delivered = delivered + ?")
                .paramsBuilder(stmt -> stmt.setLong(orderId).setBytes(UUIDConverter.convert(player.getUniqueId()))
                        .setString(material.name()).setInt(amount).setInt(amount))
                .update().executeSync();
    }

    private List<Order> getCompanyOrders(int companyId) {
        var orders = builder(Order.class)
                .query("SELECT id, owner_uuid, name, created, company, claimed, state FROM orders o LEFT JOIN order_states oc ON o.id = oc.id WHERE company = ?")
                .paramsBuilder(stmt -> stmt.setInt(companyId))
                .readRow(rs -> new Order(rs.getLong("id"), UUIDConverter.convert(rs.getBytes("owner_uuid")),
                        rs.getString("name"), rs.getTimestamp("created").toLocalDateTime(),
                        companyId, rs.getTimestamp("claimed").toLocalDateTime(), OrderState.byId(rs.getInt("state"))))
                .allSync();

        for (var order : orders) {
            order.contents(getOrderContent(order.id()));
        }
        return orders;
    }

    private List<Order> getPlayerOrders(OfflinePlayer player) {
        var orders = builder(Order.class)
                .query("SELECT id, owner_uuid, name, created, company, claimed, state FROM orders o LEFT JOIN order_states oc ON o.id = oc.id WHERE owner_uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(rs -> new Order(rs.getLong("id"), UUIDConverter.convert(rs.getBytes("owner_uuid")),
                        rs.getString("name"), rs.getTimestamp("created").toLocalDateTime(),
                        rs.getInt("company"), rs.getTimestamp("claimed").toLocalDateTime(),
                        OrderState.byId(rs.getInt("state"))))
                .allSync();

        for (var order : orders) {
            order.contents(getOrderContent(order.id()));
        }
        return orders;
    }

    private List<OrderContent> getOrderContent(long orderId) {
        var orderContents = builder(OrderContent.class)
                .query("SELECT material, stack, amount, price FROM order_content WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setLong(orderId))
                .readRow(rs -> new OrderContent(toItemStack(rs.getString("stack")), rs.getInt("amount")))
                .allSync();

        for (var orderContent : orderContents) {
            orderContent.parts(getContentParts(orderId, orderContent.stack().getType()));
        }
        return orderContents;
    }

    private List<ContentPart> getContentParts(long orderId, Material material) {
        return builder(ContentPart.class)
                .query("SELECT worker_uuid, delivered FROM orders_delivered WHERE id = ? AND material = ?")
                .paramsBuilder(stmt -> stmt.setLong(orderId).setString(material.name()))
                .readRow(rs -> new ContentPart(UUIDConverter.convert(rs.getBytes("uuid")), rs.getInt("amount")))
                .allSync();
    }

    // Company Profiles

    private Optional<SimpleCompany> getPlayerCompany(OfflinePlayer player) {
        return builder(SimpleCompany.class)
                .query("SELECT id, c.name, c.founded FROM company_member LEFT JOIN companies c ON c.id = company_member.id WHERE uuid = ?")
                .paramsBuilder(stmt -> stmt.setBytes(UUIDConverter.convert(player.getUniqueId())))
                .readRow(rs -> new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                        rs.getTimestamp("founded").toLocalDateTime()))
                .firstSync();
    }

    private Optional<Company> getCompany(int companyId) {
        return getSimpleCompany(companyId)
                .map(company -> company.toCompanyProfile(getCompanyMember(companyId)))
                .map(company -> company.toCompany(getCompanyOrders(companyId)));
    }

    private List<CompanyMember> getCompanyMember(int companyId) {
        return builder(CompanyMember.class)
                .query("SELECT uuid, permission FROM company_member WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(companyId))
                .readRow(rs -> new CompanyMember(companyId, UUIDConverter.convert(rs.getBytes("uuid")),
                        rs.getLong("permission")))
                .allSync();
    }

    private Optional<SimpleCompany> getSimpleCompany(int companyId) {
        return builder(SimpleCompany.class)
                .query("SELECT * FROM companies WHERE id = ?")
                .paramsBuilder(stmt -> stmt.setInt(companyId))
                .readRow(rs -> new SimpleCompany(rs.getInt("id"), rs.getString("name"),
                        rs.getTimestamp("founded").toLocalDateTime()))
                .firstSync();
    }

    private ItemStack toItemStack(String map) {
        return GSON.fromJson(map, ItemStackContainer.class).toItemStack();
    }

    private String toString(ItemStack stack) {
        return GSON.toJson(ItemStackContainer.create(stack));
    }

    private static class ItemStackContainer {
        private Map<String, Object> data;

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
}
