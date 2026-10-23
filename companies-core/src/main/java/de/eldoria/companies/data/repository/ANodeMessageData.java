/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.chojo.sadu.queries.api.configuration.QueryConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The message table of low latency mode: node messages stored as the same JSON text Redis carries, read by every node
 * in the order of their ids.
 * <p>
 * The methods block and throw a {@link RuntimeException} when the database cannot be reached, without logging it, so
 * the caller decides how loud a failure is. Times are bound from Java, never taken from the database clock.
 */
public abstract class ANodeMessageData {
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
     * Stores a message.
     *
     * @param origin  uid of the sending node
     * @param type    type of the message, for example {@code inbox.new}
     * @param payload the message as JSON text
     * @param created current time
     */
    public abstract void insert(UUID origin, String type, String payload, LocalDateTime created);

    /**
     * @return the highest id stored, 0 when the table is empty
     */
    public abstract long maxId();

    /**
     * The messages stored after an id, oldest first.
     *
     * @param id    messages with a higher id are returned
     * @param limit most messages to return
     * @return the messages
     */
    public abstract List<StoredMessage> after(long id, int limit);

    /**
     * Deletes the messages stored before a point in time.
     *
     * @param before messages older than this are deleted
     * @return number of messages deleted
     */
    public abstract int purgeBefore(LocalDateTime before);

    /**
     * A message read from the table.
     *
     * @param id      id of the row
     * @param payload the message as JSON text
     */
    public record StoredMessage(long id, String payload) {}
}
