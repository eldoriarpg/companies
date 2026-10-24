/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.data.repository.AHistoryData;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deletes history entries of orders that ended longer ago than the configured number of days. Only the primary node
 * runs it, once an hour. The orders themselves stay, since the company statistics and levels are computed from them.
 */
public final class HistoryRetention implements Runnable {
    private final AHistoryData history;
    private final IntSupplier days;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param history history repository
     * @param days    days an entry is kept, read on every run so a reload applies
     * @param clock   clock giving the current time
     * @param logger  logs failed runs
     */
    public HistoryRetention(AHistoryData history, IntSupplier days, Clock clock, Logger logger) {
        this.history = history;
        this.days = days;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * Starts deleting old entries every hour.
     *
     * @param executor executor running the deletion
     */
    public void start(ScheduledExecutorService executor) {
        executor.scheduleAtFixedRate(this, 5L, 60L, TimeUnit.MINUTES);
    }

    /**
     * The point in time before which entries are deleted.
     *
     * @return the oldest end time that is kept
     */
    public LocalDateTime cutoff() {
        return LocalDateTime.now(clock).minusDays(days.getAsInt());
    }

    @Override
    public void run() {
        try {
            history.submitPurge(cutoff()).join();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not delete old history entries", e);
        }
    }
}
