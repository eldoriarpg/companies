/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import java.time.Duration;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Where a node is in the message table: the highest id it has read, and the lower ids it has not seen yet.
 * <p>
 * Ids are handed out when a row is inserted, but rows become visible when their transaction commits, which is not
 * always in the order of their ids. A row with a lower id can therefore show up after a higher one was read. Every id
 * skipped over is kept as a gap for a while, and reads start below the oldest gap until it is filled or given up, so
 * such a late row is still delivered once. Ids that never come, from rolled back inserts, are given up after the gap
 * wait. Not thread safe; one reader uses it.
 */
final class MessageCursor {
    private static final int MAX_GAPS = 1000;

    private final Duration gapWait;
    private final NavigableMap<Long, Long> gaps = new TreeMap<>();
    private long last;

    /**
     * @param start   the highest id already present, which is not delivered
     * @param gapWait how long a skipped id is waited for
     */
    MessageCursor(long start, Duration gapWait) {
        this.last = start;
        this.gapWait = gapWait;
    }

    /**
     * @return rows with a higher id than this are to be read
     */
    long readFrom() {
        return gaps.isEmpty() ? last : Math.min(last, gaps.firstKey() - 1);
    }

    /**
     * Records a row that was read.
     *
     * @param id  id of the row
     * @param now current {@link System#nanoTime()}
     * @return true when the row is new and must be delivered, false when it was delivered before
     */
    boolean accept(long id, long now) {
        if (id > last) {
            for (var missing = last + 1; missing < id && gaps.size() < MAX_GAPS; missing++) gaps.put(missing, now);
            last = id;
            return true;
        }
        return gaps.remove(id) != null;
    }

    /**
     * Gives up the gaps waited for longer than the gap wait.
     *
     * @param now current {@link System#nanoTime()}
     */
    void expire(long now) {
        var limit = gapWait.toNanos();
        gaps.values().removeIf(noticed -> now - noticed > limit);
    }

    /**
     * @return the highest id read
     */
    long last() {
        return last;
    }
}
