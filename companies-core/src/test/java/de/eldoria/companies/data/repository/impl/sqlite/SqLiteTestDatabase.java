/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.sqlite.databases.SqLite;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.sqlite.SQLiteDataSource;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Creates a migrated SQLite database in a file and provides helpers to prepare test data without a running server.
 */
final class SqLiteTestDatabase {
    private SqLiteTestDatabase() {
    }

    /**
     * Creates a database file in the directory, applies all migrations and registers it as the default query configuration.
     *
     * @param directory directory for the database file
     * @return the data source of the database
     */
    static SQLiteDataSource create(Path directory) throws SQLException, IOException {
        var dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + directory.resolve("data.db"));
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        SqlUpdater.builder(dataSource, SqLite.get())
                  .setVersionTable("companies_db_version")
                  .withClassLoader(SqLiteTestDatabase.class.getClassLoader())
                  .setReplacements(new QueryReplacement("companies_schema", "companies_schema"))
                  .execute();
        return dataSource;
    }

    /**
     * Creates a mapper which stores items by type and amount only, since item metadata needs a running server.
     *
     * @return mapper for item stacks
     */
    static ObjectMapper mapper() {
        var module = new SimpleModule();
        module.addSerializer(ItemStack.class, new ValueSerializer<>() {
            @Override
            public void serialize(ItemStack value, JsonGenerator gen, SerializationContext context) {
                gen.writeStartObject();
                gen.writeStringProperty("type", value.getType().name());
                gen.writeNumberProperty("amount", value.getAmount());
                gen.writeEndObject();
            }
        });
        module.addDeserializer(ItemStack.class, new ValueDeserializer<>() {
            @Override
            public ItemStack deserialize(JsonParser p, DeserializationContext ctxt) {
                JsonNode node = ctxt.readTree(p);
                return new ItemStack(Material.valueOf(node.get("type").asString()), node.get("amount").asInt());
            }
        });
        return JsonMapper.builder().addModule(module).build();
    }

    /**
     * Creates an offline player which only knows its id.
     *
     * @param uuid id of the player
     * @return player
     */
    static OfflinePlayer player(UUID uuid) {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(), new Class[]{OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "OfflinePlayer(" + uuid + ")";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Sets the state and company of an order directly.
     *
     * @param order   id of the order
     * @param state   new state id
     * @param company company id
     */
    static void setState(int order, int state, int company) {
        query("UPDATE order_states SET state = ?, company = ? WHERE id = ?")
                .single(call().bind(state).bind(company).bind(order))
                .update();
    }

    /**
     * Counts the rows of a table matching a condition.
     *
     * @param table     table to count
     * @param condition sql condition
     * @return amount of rows
     */
    static int count(String table, String condition) {
        return query("SELECT COUNT(1) AS count FROM " + table + " WHERE " + condition)
                .single()
                .map(row -> row.getInt("count"))
                .first()
                .orElseThrow();
    }
}
