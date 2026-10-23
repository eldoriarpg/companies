/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.data.repository.ANodeMessageData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A message table in memory for tests. Rows can be inserted with chosen ids to imitate late commits, reads can be made
 * to fail, and every query is counted.
 */
final class MemoryMessageTable extends ANodeMessageData {
    private final List<Row> rows = new ArrayList<>();
    final AtomicInteger reads = new AtomicInteger();
    final AtomicInteger purges = new AtomicInteger();
    volatile boolean broken;
    private long nextId = 1;

    /**
     * A stored row.
     *
     * @param id      id
     * @param created when it was stored
     * @param type    message type
     * @param payload JSON text
     */
    record Row(long id, LocalDateTime created, String type, String payload) {}

    @Override
    public synchronized void insert(UUID origin, String type, String payload, LocalDateTime created) {
        if (broken) throw new IllegalStateException("database away");
        rows.add(new Row(nextId++, created, type, payload));
    }

    /**
     * Stores a row with a chosen id, as if its transaction committed late.
     *
     * @param id      id of the row
     * @param payload JSON text
     */
    synchronized void insertWithId(long id, String payload) {
        rows.add(new Row(id, LocalDateTime.now(), "test", payload));
        nextId = Math.max(nextId, id + 1);
    }

    /**
     * Takes an id without storing a row, as an insert that is not committed yet or was rolled back.
     *
     * @return the id taken
     */
    synchronized long skipId() {
        return nextId++;
    }

    synchronized List<Row> rows() {
        return List.copyOf(rows);
    }

    @Override
    public synchronized long maxId() {
        if (broken) throw new IllegalStateException("database away");
        return rows.stream().mapToLong(Row::id).max().orElse(0);
    }

    @Override
    public synchronized List<StoredMessage> after(long id, int limit) {
        reads.incrementAndGet();
        if (broken) throw new IllegalStateException("database away");
        return rows.stream()
                .filter(row -> row.id() > id)
                .sorted((left, right) -> Long.compare(left.id(), right.id()))
                .limit(limit)
                .map(row -> new StoredMessage(row.id(), row.payload()))
                .toList();
    }

    @Override
    public synchronized int purgeBefore(LocalDateTime before) {
        purges.incrementAndGet();
        var count = (int)
                rows.stream().filter(row -> row.created().isBefore(before)).count();
        rows.removeIf(row -> row.created().isBefore(before));
        return count;
    }
}
