/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.data.wrapper.dashboard.MarketDay;
import de.eldoria.companies.data.wrapper.dashboard.MarketRow;
import de.eldoria.companies.data.wrapper.dashboard.StoredKey;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The dashboard's state shared by every node of a network: the key the network authenticates with, and the sessions
 * the nodes created, so any node can check a session the gateway routes to it.
 * <p>
 * The methods block and throw a {@link RuntimeException} when the database cannot be reached, without logging it, so
 * the caller decides how loud a failure is. Times are UTC and bound from Java.
 */
public abstract class ADashboardData {
    private volatile QueryConfiguration queries;

    /**
     * The default query configuration, set to throw failures instead of logging them. Created on first use.
     *
     * @return the configuration to run the queries with
     */
    protected QueryConfiguration queries() {
        var current = queries;
        if (current != null) return current;
        current = QueryConfiguration.getDefault()
                .edit()
                .setExceptionHandler(error -> {})
                .setThrowExceptions(true)
                .build();
        queries = current;
        return current;
    }

    /**
     * The key of the network.
     *
     * @return the key, empty when none was entered
     */
    public abstract Optional<StoredKey> key();

    /**
     * Stores the key of the network, replacing the previous one.
     *
     * @param key the key
     * @param now current time
     */
    public abstract void saveKey(StoredKey key, LocalDateTime now);

    /**
     * Records that the primary asked Lyna about the key.
     *
     * @param checked when it asked
     */
    public abstract void keyChecked(LocalDateTime checked);

    /**
     * Stores a new session.
     *
     * @param session the session
     */
    public abstract void saveSession(StoredSession session);

    /**
     * One session.
     *
     * @param id the session id
     * @return the session, empty when it does not exist
     */
    public abstract Optional<StoredSession> session(String id);

    /**
     * The sessions of a player, oldest first.
     *
     * @param player the player
     * @return the sessions
     */
    public abstract List<StoredSession> sessionsOf(UUID player);

    /**
     * Every session of the network, oldest first.
     *
     * @return the sessions
     */
    public abstract List<StoredSession> sessions();

    /**
     * The players that have a session.
     *
     * @return the players
     */
    public abstract Set<UUID> playersWithSessions();

    /**
     * Marks a pending session as opened by a browser.
     *
     * @param id     the session id
     * @param opened when the browser logged in
     * @param device the browser, null when unknown
     * @return true when a pending session was opened
     */
    public abstract boolean open(String id, LocalDateTime opened, @Nullable String device);

    /**
     * Records that a session was used.
     *
     * @param id     the session id
     * @param active when it was used
     */
    public abstract void touch(String id, LocalDateTime active);

    /**
     * Replaces the remembered permissions of every session of a player.
     *
     * @param player      the player
     * @param permissions the permissions
     */
    public abstract void permissions(UUID player, Set<String> permissions);

    /**
     * Deletes a session.
     *
     * @param id the session id
     * @return true when this call deleted it
     */
    public abstract boolean removeSession(String id);

    /**
     * The sessions that ended: idle since before {@code idleBefore}, created before {@code createdBefore}, or never
     * opened and their code expired before {@code now}.
     *
     * @param idleBefore    sessions last used before this are idle
     * @param createdBefore sessions created before this are too old
     * @param now           current time
     * @return the ended sessions, still stored
     */
    public abstract List<StoredSession> ended(LocalDateTime idleBefore, LocalDateTime createdBefore, LocalDateTime now);

    /**
     * Counts the sessions of the network.
     *
     * @return the number of sessions, pending ones included
     */
    public abstract int sessionCount();

    /**
     * What the delivered and received orders add up to per material, without exact lines.
     *
     * @return one row per material
     */
    public abstract List<MarketRow> market();

    /**
     * What the recorded market history adds up to per material, over the days it keeps.
     *
     * @return one row per material, its average weighted by the items of each day
     */
    public abstract List<MarketRow> marketHistoryTotals();

    /**
     * The market as the dashboard lists it: the delivered and received orders of a material, or what its recorded
     * history adds up to where it has fewer orders than the market shows a price for, such as after its orders were
     * removed.
     *
     * @param minOrders the fewest orders a material needs for its price to be shown
     * @return one row per material
     */
    public List<MarketRow> marketWithHistory(int minOrders) {
        return merged(market(), marketHistoryTotals(), minOrders);
    }

    /**
     * @param live      the delivered and received orders per material
     * @param history   what the recorded history adds up to per material
     * @param minOrders the fewest orders a material needs for its price to be shown
     * @return the live row of each material, or its history where the live row has too few orders to show a price
     *     and the history has enough
     */
    static List<MarketRow> merged(List<MarketRow> live, List<MarketRow> history, int minOrders) {
        Map<String, MarketRow> rows = new LinkedHashMap<>();
        for (var row : history) rows.put(row.material(), row);
        for (var row : live) {
            var recorded = rows.get(row.material());
            if (recorded == null || row.orders() >= minOrders || recorded.orders() < minOrders) {
                rows.put(row.material(), row);
            }
        }
        return List.copyOf(rows.values());
    }

    /**
     * Replaces what is stored for one day of the market history.
     *
     * @param day  the day
     * @param rows one row per material completed on that day
     * @return true when the day was stored
     */
    public abstract boolean saveMarketDay(LocalDate day, List<MarketDay> rows);

    /**
     * The market history of one material, oldest day first.
     *
     * @param material the material name
     * @param from     the first day
     * @return one row per day with completed orders
     */
    public abstract List<MarketDay> marketHistory(String material, LocalDate from);

    /**
     * The market history of every material, for comparing periods.
     *
     * @param from the first day
     * @return one row per material and day with completed orders
     */
    public abstract List<MarketDay> marketDays(LocalDate from);

    /**
     * Removes the market history before a day.
     *
     * @param before the first day kept
     * @return the number of rows removed
     */
    public abstract int purgeMarketHistory(LocalDate before);

    /**
     * The last day the market history was recorded through, rows or not.
     *
     * @return the day, empty before the first recording
     */
    public abstract Optional<LocalDate> marketRecordedThrough();

    /**
     * Notes the last day the market history was recorded through.
     *
     * @param day the day
     */
    public abstract void saveMarketRecordedThrough(LocalDate day);
}
