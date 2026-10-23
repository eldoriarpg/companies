/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.ANodeMessageData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import org.intellij.lang.annotations.Language;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * The message table for MariaDB. The other dialects override the binding of times.
 */
public class MariaDbNodeMessageData extends ANodeMessageData {
    /**
     * Binds a point in time in the form the time columns of this dialect compare correctly.
     *
     * @return adapter for times
     */
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.TIMESTAMP;
    }

    @Override
    public void insert(UUID origin, String type, String payload, LocalDateTime created) {
        @Language("mariadb")
        var query = """
                INSERT INTO node_messages(created, origin_node, type, payload)
                VALUES (?, ?, ?, ?)""";
        queries()
                .query(query)
                .single(call().bind(created, time())
                        .bind(origin, UUID_BYTES)
                        .bind(type)
                        .bind(payload))
                .insert();
    }

    @Override
    public long maxId() {
        @Language("mariadb")
        var query = "SELECT COALESCE(MAX(id), 0) AS max_id FROM node_messages";
        return queries()
                .query(query)
                .single()
                .map(row -> row.getLong("max_id"))
                .first()
                .orElse(0L);
    }

    @Override
    public List<StoredMessage> after(long id, int limit) {
        @Language("mariadb")
        var query = """
                SELECT id, payload
                FROM node_messages
                WHERE id > ?
                ORDER BY id
                LIMIT ?""";
        return queries()
                .query(query)
                .single(call().bind(id).bind(limit))
                .map(row -> new StoredMessage(row.getLong("id"), row.getString("payload")))
                .all();
    }

    @Override
    public int purgeBefore(LocalDateTime before) {
        @Language("mariadb")
        var query = "DELETE FROM node_messages WHERE created < ?";
        return queries()
                .query(query)
                .single(call().bind(before, time()))
                .delete()
                .rows();
    }
}
