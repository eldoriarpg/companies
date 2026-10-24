/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.mariadb;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.adapter.Adapter;
import de.eldoria.companies.data.repository.ADashboardData;
import de.eldoria.companies.data.repository.impl.TimeColumns;
import de.eldoria.companies.data.wrapper.dashboard.MarketDay;
import de.eldoria.companies.data.wrapper.dashboard.MarketRow;
import de.eldoria.companies.data.wrapper.dashboard.StoredKey;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.web.api.handshake.KeyKind;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;

/**
 * The dashboard tables for MariaDB. The other dialects override the upsert of the key and the binding of times.
 */
public class MariaDbDashboardData extends ADashboardData {
    /**
     * The id of the only row of the key table.
     */
    protected static final int KEY_ROW = 1;

    private static final int MARKET_STATE_ROW = 1;

    @Language("mariadb")
    private static final String MARKET_DAYS = """
            SELECT day, material, orders, items, avg_price, min_price, max_price
            FROM market_history
            """;

    @Language("mariadb")
    private static final String SESSIONS = """
            SELECT id, player_uuid, player_name, node_uuid, created, code_expires, last_active, opened, device,
                   permissions
            FROM dashboard_sessions
            """;

    /**
     * Binds a point in time in the form the time columns of this dialect compare correctly.
     *
     * @return adapter for times
     */
    protected Adapter<LocalDateTime> time() {
        return TimeColumns.TIMESTAMP;
    }

    /**
     * Reads a time column that may be null.
     *
     * @param row    row
     * @param column name of the column
     * @return time, or null when the column is null
     * @throws SQLException when the column is missing
     */
    protected @Nullable LocalDateTime readTime(Row row, String column) throws SQLException {
        var timestamp = row.getObject(column, LocalDateTime.class);
        return timestamp == null ? null : timestamp;
    }

    /**
     * The statement that inserts the key row or replaces it, with the parameters id, secret, kind, refresh at,
     * checked at, seed hash and updated.
     *
     * @return the statement
     */
    @Language("mariadb")
    protected String upsertKey() {
        return """
                INSERT INTO dashboard_key(id, secret, kind, refresh_at, checked_at, seed_hash, updated)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE secret     = VALUES(secret),
                                        kind       = VALUES(kind),
                                        refresh_at = VALUES(refresh_at),
                                        checked_at = VALUES(checked_at),
                                        seed_hash  = VALUES(seed_hash),
                                        updated    = VALUES(updated)""";
    }

    @Override
    public Optional<StoredKey> key() {
        @Language("mariadb")
        var query = "SELECT secret, kind, refresh_at, checked_at, seed_hash FROM dashboard_key WHERE id = ?";
        return queries()
                .query(query)
                .single(call().bind(KEY_ROW))
                .map(row -> new StoredKey(
                        row.getString("secret"),
                        kind(row.getString("kind")),
                        readTime(row, "refresh_at"),
                        readTime(row, "checked_at"),
                        row.getString("seed_hash")))
                .first();
    }

    private static KeyKind kind(String stored) {
        try {
            return KeyKind.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return KeyKind.UNKNOWN;
        }
    }

    @Override
    public void saveKey(StoredKey key, LocalDateTime now) {
        queries()
                .query(upsertKey())
                .single(call().bind(KEY_ROW)
                        .bind(key.secret())
                        .bind(key.kind().name())
                        .bind(key.refreshAt(), time())
                        .bind(key.checkedAt(), time())
                        .bind(key.seedHash())
                        .bind(now, time()))
                .insert();
    }

    @Override
    public void keyChecked(LocalDateTime checked) {
        @Language("mariadb")
        var query = "UPDATE dashboard_key SET checked_at = ? WHERE id = ?";
        queries()
                .query(query)
                .single(call().bind(checked, time()).bind(KEY_ROW))
                .update();
    }

    @Override
    public void saveSession(StoredSession session) {
        @Language("mariadb")
        var query = """
                INSERT INTO dashboard_sessions(id, player_uuid, player_name, node_uuid, created, code_expires,
                                               last_active, opened, device, permissions)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        queries()
                .query(query)
                .single(call().bind(session.id())
                        .bind(session.player(), UUID_BYTES)
                        .bind(session.playerName())
                        .bind(session.node(), UUID_BYTES)
                        .bind(session.created(), time())
                        .bind(session.codeExpires(), time())
                        .bind(session.lastActive(), time())
                        .bind(session.opened(), time())
                        .bind(session.device())
                        .bind(session.permissionText()))
                .insert();
    }

    @Override
    public Optional<StoredSession> session(String id) {
        return queries()
                .query(SESSIONS + "WHERE id = ?")
                .single(call().bind(id))
                .map(this::parseSession)
                .first();
    }

    @Override
    public List<StoredSession> sessionsOf(UUID player) {
        return queries()
                .query(SESSIONS + "WHERE player_uuid = ? ORDER BY created, id")
                .single(call().bind(player, UUID_BYTES))
                .map(this::parseSession)
                .all();
    }

    @Override
    public Set<UUID> playersWithSessions() {
        @Language("mariadb")
        var query = "SELECT DISTINCT player_uuid FROM dashboard_sessions";
        return new HashSet<>(queries()
                .query(query)
                .single()
                .map(row -> row.get("player_uuid", UUID_BYTES))
                .all());
    }

    @Override
    public boolean open(String id, LocalDateTime opened, @Nullable String device) {
        @Language("mariadb")
        var query = """
                UPDATE dashboard_sessions
                SET opened      = ?,
                    last_active = ?,
                    device      = ?
                WHERE id = ?
                  AND opened IS NULL""";
        return queries()
                .query(query)
                .single(call().bind(opened, time())
                        .bind(opened, time())
                        .bind(device)
                        .bind(id))
                .update()
                .changed();
    }

    @Override
    public void touch(String id, LocalDateTime active) {
        @Language("mariadb")
        var query = "UPDATE dashboard_sessions SET last_active = ? WHERE id = ?";
        queries().query(query).single(call().bind(active, time()).bind(id)).update();
    }

    @Override
    public void permissions(UUID player, Set<String> permissions) {
        @Language("mariadb")
        var query = "UPDATE dashboard_sessions SET permissions = ? WHERE player_uuid = ?";
        queries()
                .query(query)
                .single(call().bind(String.join("\n", permissions)).bind(player, UUID_BYTES))
                .update();
    }

    @Override
    public boolean removeSession(String id) {
        @Language("mariadb")
        var query = "DELETE FROM dashboard_sessions WHERE id = ?";
        return queries().query(query).single(call().bind(id)).delete().changed();
    }

    @Override
    public List<StoredSession> ended(LocalDateTime idleBefore, LocalDateTime createdBefore, LocalDateTime now) {
        return queries()
                .query(SESSIONS + """
                        WHERE last_active < ?
                           OR created < ?
                           OR (opened IS NULL AND code_expires < ?)""")
                .single(call().bind(idleBefore, time())
                        .bind(createdBefore, time())
                        .bind(now, time()))
                .map(this::parseSession)
                .all();
    }

    @Override
    public int sessionCount() {
        @Language("mariadb")
        var query = "SELECT COUNT(1) AS count FROM dashboard_sessions";
        return queries()
                .query(query)
                .single()
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    @Override
    public List<MarketRow> market() {
        @Language("mariadb")
        var query = """
                SELECT c.material,
                       COUNT(DISTINCT c.id)   AS orders,
                       SUM(c.amount)          AS volume,
                       AVG(c.price / c.amount) AS avg_price,
                       MIN(c.price / c.amount) AS min_price,
                       MAX(c.price / c.amount) AS max_price
                FROM order_content c
                         JOIN order_states s ON c.id = s.id
                WHERE s.state >= 200
                  AND NOT c.exact
                  AND c.amount > 0
                GROUP BY c.material""";
        return queries()
                .query(query)
                .single()
                .map(row -> new MarketRow(
                        row.getString("material"),
                        row.getInt("orders"),
                        row.getLong("volume"),
                        row.getDouble("avg_price"),
                        row.getDouble("min_price"),
                        row.getDouble("max_price")))
                .all();
    }

    @Override
    public boolean saveMarketDay(LocalDate day, List<MarketDay> rows) {
        @Language("mariadb")
        var clear = "DELETE FROM market_history WHERE day = ?";
        @Language("mariadb")
        var insert = """
                INSERT INTO market_history(day, material, orders, items, avg_price, min_price, max_price)
                VALUES (?, ?, ?, ?, ?, ?, ?)""";
        var conf = queries().withSingleTransaction();
        try (conf) {
            conf.query(clear).single(call().bind(day.toEpochDay())).delete();
            if (!rows.isEmpty()) {
                conf.query(insert)
                        .batch(rows.stream().map(row -> call().bind(day.toEpochDay())
                                .bind(row.material())
                                .bind(row.orders())
                                .bind(row.items())
                                .bind(row.avg())
                                .bind(row.min())
                                .bind(row.max())))
                        .insert();
            }
        }
        return conf.context().exceptions().isEmpty();
    }

    @Override
    public List<MarketDay> marketHistory(String material, LocalDate from) {
        @Language("mariadb")
        var query = MARKET_DAYS + "WHERE material = ? AND day >= ? ORDER BY day";
        return queries()
                .query(query)
                .single(call().bind(material).bind(from.toEpochDay()))
                .map(MariaDbDashboardData::parseMarketDay)
                .all();
    }

    @Override
    public List<MarketDay> marketDays(LocalDate from) {
        @Language("mariadb")
        var query = MARKET_DAYS + "WHERE day >= ? ORDER BY material, day";
        return queries()
                .query(query)
                .single(call().bind(from.toEpochDay()))
                .map(MariaDbDashboardData::parseMarketDay)
                .all();
    }

    @Override
    public int purgeMarketHistory(LocalDate before) {
        @Language("mariadb")
        var query = "DELETE FROM market_history WHERE day < ?";
        return queries()
                .query(query)
                .single(call().bind(before.toEpochDay()))
                .delete()
                .rows();
    }

    @Override
    public Optional<LocalDate> marketRecordedThrough() {
        @Language("mariadb")
        var query = "SELECT recorded_through FROM market_history_state WHERE id = ?";
        return queries()
                .query(query)
                .single(call().bind(MARKET_STATE_ROW))
                .map(row -> LocalDate.ofEpochDay(row.getLong("recorded_through")))
                .first();
    }

    @Override
    public void saveMarketRecordedThrough(LocalDate day) {
        @Language("mariadb")
        var clear = "DELETE FROM market_history_state WHERE id = ?";
        @Language("mariadb")
        var insert = "INSERT INTO market_history_state(id, recorded_through) VALUES (?, ?)";
        var conf = queries().withSingleTransaction();
        try (conf) {
            conf.query(clear).single(call().bind(MARKET_STATE_ROW)).delete();
            conf.query(insert)
                    .single(call().bind(MARKET_STATE_ROW).bind(day.toEpochDay()))
                    .insert();
        }
    }

    private static MarketDay parseMarketDay(Row row) throws SQLException {
        return new MarketDay(
                LocalDate.ofEpochDay(row.getLong("day")),
                row.getString("material"),
                row.getInt("orders"),
                row.getLong("items"),
                row.getDouble("avg_price"),
                row.getDouble("min_price"),
                row.getDouble("max_price"));
    }

    private StoredSession parseSession(Row row) throws SQLException {
        return new StoredSession(
                row.getString("id"),
                row.get("player_uuid", UUID_BYTES),
                row.getString("player_name"),
                row.get("node_uuid", UUID_BYTES),
                required(readTime(row, "created")),
                required(readTime(row, "code_expires")),
                required(readTime(row, "last_active")),
                readTime(row, "opened"),
                row.getString("device"),
                StoredSession.permissions(row.getString("permissions")));
    }

    private static LocalDateTime required(@Nullable LocalDateTime time) throws SQLException {
        if (time == null) throw new SQLException("A required time column is null.");
        return time;
    }
}
