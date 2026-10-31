/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts the errors of database queries. A failed read returns an empty result, which looks the same as nothing being
 * there; work that removes things for what it did not find compares the count before and after its reads and stops
 * when it grew.
 */
public final class QueryErrors {
    private final AtomicLong count = new AtomicLong();

    /**
     * Notes one more failed query.
     */
    public void record() {
        count.incrementAndGet();
    }

    /**
     * The errors so far.
     *
     * @return how many queries failed since the plugin started
     */
    public long count() {
        return count.get();
    }
}
