/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.ASavedItemData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.repository.impl.Transactions;
import de.eldoria.companies.data.wrapper.saved.SaveResult;
import de.eldoria.companies.data.wrapper.saved.SavedItem;
import org.intellij.lang.annotations.Language;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * The saved items for MariaDB, the base of the other dialects. A name taken twice at the same time is refused by the
 * unique key.
 */
public class MariaDbSavedItemData extends ASavedItemData {
    @Language("mariadb")
    private static final String ITEMS = """
            SELECT id, owner_uuid, name, stack, created
            FROM saved_items
            """;

    public MariaDbSavedItemData(ObjectMapper mapper, ExecutorService executor) {
        super(mapper, executor);
    }

    /**
     * @return how a time is bound, so it compares with the dialect's time columns
     */
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.TIMESTAMP;
    }

    /**
     * @param row    row
     * @param column name of the column
     * @return the time
     * @throws SQLException when the column is missing
     */
    protected LocalDateTime readTime(Row row, String column) throws SQLException {
        return row.getObject(column, LocalDateTime.class);
    }

    @Override
    protected SaveResult saveItem(UUID owner, String name, String stack, int max, LocalDateTime now) {
        @Language("mariadb")
        var names = """
                SELECT name FROM saved_items WHERE owner_uuid = ?""";
        @Language("mariadb")
        var insert = """
                INSERT INTO saved_items(owner_uuid, name, stack, created)
                VALUES (?, ?, ?, ?)""";
        var errors = new ArrayList<SQLException>();
        var conf = Transactions.open(errors);
        try (conf) {
            var taken = conf.query(names)
                    .single(call().bind(owner, UUID_BYTES))
                    .map(row -> row.getString("name"))
                    .all();
            if (!errors.isEmpty()) {
                Transactions.rollback(conf, errors);
                return SaveResult.FAILED;
            }
            if (taken.contains(name)) return SaveResult.NAME_TAKEN;
            if (taken.size() >= max) return SaveResult.LIMIT_REACHED;
            conf.query(insert)
                    .single(call().bind(owner, UUID_BYTES)
                            .bind(name)
                            .bind(stack)
                            .bind(now, time()))
                    .insert();
            if (!errors.isEmpty()) {
                return Transactions.rollback(conf, errors) ? SaveResult.NAME_TAKEN : SaveResult.FAILED;
            }
        }
        return SaveResult.SAVED;
    }

    @Override
    protected List<SavedItem> getItems(UUID owner) {
        return query(ITEMS + "WHERE owner_uuid = ? ORDER BY name")
                .single(call().bind(owner, UUID_BYTES))
                .map(this::parseItem)
                .all();
    }

    @Override
    protected Optional<SavedItem> getItem(UUID owner, String name) {
        return query(ITEMS + "WHERE owner_uuid = ? AND name = ?")
                .single(call().bind(owner, UUID_BYTES).bind(name))
                .map(this::parseItem)
                .first();
    }

    @Override
    protected Optional<SavedItem> getItem(UUID owner, int id) {
        return query(ITEMS + "WHERE owner_uuid = ? AND id = ?")
                .single(call().bind(owner, UUID_BYTES).bind(id))
                .map(this::parseItem)
                .first();
    }

    @Override
    protected boolean removeItem(UUID owner, String name) {
        @Language("mariadb")
        var query = """
                DELETE FROM saved_items WHERE owner_uuid = ? AND name = ?""";
        return query(query)
                .single(call().bind(owner, UUID_BYTES).bind(name))
                .delete()
                .changed();
    }

    private SavedItem parseItem(Row row) throws SQLException {
        return new SavedItem(
                row.getInt("id"),
                row.get("owner_uuid", UUID_BYTES),
                row.getString("name"),
                readStack(row.getString("stack")),
                readTime(row, "created"));
    }
}
