/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.AInboxData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * The inbox for MariaDB. The other dialects override the upsert of the mute mask, the JSON parameter and the binding
 * of times.
 */
public class MariaDbInboxData extends AInboxData {
    @Language("mariadb")
    private static final String ENTRIES = """
            SELECT id, user_uuid, type, args, created, delivered, read_at
            FROM company_inbox
            """;

    public MariaDbInboxData(ExecutorService executor) {
        super(executor);
    }

    /**
     * Binds a point in time in the form the time columns of this dialect compare correctly.
     *
     * @return adapter for times
     */
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.TIMESTAMP;
    }

    /**
     * Reads a time column.
     *
     * @param row    row
     * @param column name of the column
     * @return time
     * @throws SQLException when the column is missing
     */
    protected LocalDateTime readTime(Row row, String column) throws SQLException {
        return row.getTimestamp(column).toLocalDateTime();
    }

    /**
     * Reads a time column that may be null.
     *
     * @param row    row
     * @param column name of the column
     * @return time, or null when the column is null
     * @throws SQLException when the column is missing
     */
    protected @Nullable LocalDateTime readNullableTime(Row row, String column) throws SQLException {
        var timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * The parameter of a JSON value in an insert.
     *
     * @return parameter, possibly with a cast
     */
    protected String jsonParameter() {
        return "?";
    }

    @Override
    protected Optional<Long> save(
            UUID recipient, NotificationType type, NotificationArgs args, LocalDateTime created, InboxStatus status) {
        @Language("mariadb")
        var query = """
                INSERT INTO company_inbox(user_uuid, type, args, created, delivered, read_at)
                VALUES (?, ?, %s, ?, ?, ?)
                RETURNING id""";
        return query(query, jsonParameter())
                .single(call().bind(recipient, UUID_BYTES)
                        .bind(type.id())
                        .bind(args.toJson())
                        .bind(created, time())
                        .bind(status != InboxStatus.PENDING)
                        .bind(status == InboxStatus.READ ? created : null, time()))
                .map(row -> row.getLong(1))
                .first();
    }

    @Override
    protected List<InboxEntry> getPending(List<UUID> recipients, int limit) {
        List<InboxEntry> pending = new ArrayList<>();
        for (var start = 0; start < recipients.size(); start += PLAYER_CHUNK) {
            var chunk = recipients.subList(start, Math.min(recipients.size(), start + PLAYER_CHUNK));
            var call = call();
            for (var recipient : chunk) call = call.bind(recipient, UUID_BYTES);
            pending.addAll(query(ENTRIES
                            + "WHERE NOT delivered AND user_uuid IN (%s) ORDER BY created, id LIMIT ?"
                                    .formatted(placeholders(chunk.size())))
                    .single(call.bind(limit))
                    .map(this::parseEntry)
                    .all());
        }
        return pending.stream()
                .sorted(Comparator.comparing(InboxEntry::created).thenComparingLong(InboxEntry::id))
                .limit(limit)
                .toList();
    }

    @Override
    protected boolean claim(long id, LocalDateTime now) {
        @Language("mariadb")
        var query = """
                UPDATE company_inbox
                SET delivered = TRUE,
                    read_at   = ?
                WHERE id = ?
                  AND NOT delivered""";
        return query(query).single(call().bind(now, time()).bind(id)).update().changed();
    }

    @Override
    protected int markDelivered(UUID recipient) {
        @Language("mariadb")
        var query = """
                UPDATE company_inbox
                SET delivered = TRUE
                WHERE user_uuid = ?
                  AND NOT delivered""";
        return query(query).single(call().bind(recipient, UUID_BYTES)).update().rows();
    }

    @Override
    protected int countUnread(UUID recipient, long mutedMask) {
        var muted = Arrays.stream(NotificationType.values())
                .filter(type -> type.category().in(mutedMask))
                .toList();
        var call = call().bind(recipient, UUID_BYTES);
        for (var type : muted) call = call.bind(type.id());
        var filter = muted.isEmpty() ? "" : "AND type NOT IN (%s)".formatted(placeholders(muted.size()));
        return query("SELECT COUNT(1) AS count FROM company_inbox WHERE user_uuid = ? AND read_at IS NULL " + filter)
                .single(call)
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    @Override
    protected List<InboxEntry> getPage(UUID recipient, int page, int size) {
        return query(ENTRIES + "WHERE user_uuid = ? ORDER BY created DESC, id DESC LIMIT ? OFFSET ?")
                .single(call().bind(recipient, UUID_BYTES).bind(size).bind((page - 1) * size))
                .map(this::parseEntry)
                .all();
    }

    @Override
    protected int markRead(UUID recipient, List<Long> ids, LocalDateTime now) {
        var call = call().bind(now, time()).bind(recipient, UUID_BYTES);
        for (var id : ids) call = call.bind(id);
        return query("""
                        UPDATE company_inbox
                        SET delivered = TRUE,
                            read_at   = ?
                        WHERE user_uuid = ?
                          AND read_at IS NULL
                          AND id IN (%s)""".formatted(placeholders(ids.size())))
                .single(call)
                .update()
                .rows();
    }

    @Override
    protected int markAllRead(UUID recipient, LocalDateTime now) {
        @Language("mariadb")
        var query = """
                UPDATE company_inbox
                SET delivered = TRUE,
                    read_at   = ?
                WHERE user_uuid = ?
                  AND read_at IS NULL""";
        return query(query)
                .single(call().bind(now, time()).bind(recipient, UUID_BYTES))
                .update()
                .rows();
    }

    @Override
    protected int deleteRead(UUID recipient) {
        @Language("mariadb")
        var query = """
                DELETE
                FROM company_inbox
                WHERE user_uuid = ?
                  AND read_at IS NOT NULL""";
        return query(query).single(call().bind(recipient, UUID_BYTES)).delete().rows();
    }

    @Override
    protected int purgeBefore(LocalDateTime before) {
        @Language("mariadb")
        var query = """
                DELETE
                FROM company_inbox
                WHERE created < ?""";
        return query(query).single(call().bind(before, time())).delete().rows();
    }

    @Override
    protected long getMuted(UUID player) {
        @Language("mariadb")
        var query = """
                SELECT muted
                FROM notification_settings
                WHERE user_uuid = ?""";
        return query(query)
                .single(call().bind(player, UUID_BYTES))
                .map(row -> row.getLong("muted"))
                .first()
                .orElse(0L);
    }

    @Override
    protected boolean saveMuted(UUID player, long mask) {
        @Language("mariadb")
        var query = """
                INSERT INTO notification_settings(user_uuid, muted)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE muted = VALUES(muted)""";
        return upsertMuted(query, player, mask);
    }

    /**
     * Runs the dialect's upsert of a mute mask.
     *
     * @param query  upsert with the parameters player and mask
     * @param player player
     * @param mask   mute mask
     * @return true when the statement ran without error
     */
    protected boolean upsertMuted(String query, UUID player, long mask) {
        return !query(query)
                .single(call().bind(player, UUID_BYTES).bind(mask))
                .insert()
                .hasExceptions();
    }

    private InboxEntry parseEntry(Row row) throws SQLException {
        var raw = row.getString("args");
        var type = NotificationType.byId(row.getInt("type")).orElse(NotificationType.LEGACY);
        NotificationArgs args;
        try {
            args = NotificationArgs.fromJson(raw);
        } catch (IllegalArgumentException e) {
            type = NotificationType.LEGACY;
            args = NotificationArgs.create().text(NotificationArgs.LEGACY_MESSAGE, raw);
        }
        return new InboxEntry(
                row.getLong("id"),
                row.get("user_uuid", UUID_BYTES),
                type,
                args,
                readTime(row, "created"),
                row.getBoolean("delivered"),
                readNullableTime(row, "read_at"));
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}
