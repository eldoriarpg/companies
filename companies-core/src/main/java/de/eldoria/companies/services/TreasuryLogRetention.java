/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.data.repository.ATreasuryData;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deletes treasury log entries older than the configured number of days. Only the primary node runs it, once an hour.
 * Balances are not touched; a retention of 0 days keeps the log forever.
 */
public final class TreasuryLogRetention implements Runnable {
    private final ATreasuryData treasury;
    private final IntSupplier days;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param treasury treasury repository
     * @param days     days an entry is kept, 0 to keep it forever, read on every run so a reload applies
     * @param clock    clock giving the current time
     * @param logger   logs failed runs
     */
    public TreasuryLogRetention(ATreasuryData treasury, IntSupplier days, Clock clock, Logger logger) {
        this.treasury = treasury;
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
     * @return the oldest time that is kept, or empty when the log is kept forever
     */
    public Optional<LocalDateTime> cutoff() {
        var keep = days.getAsInt();
        if (keep <= 0) return Optional.empty();
        return Optional.of(LocalDateTime.now(clock).minusDays(keep));
    }

    @Override
    public void run() {
        try {
            var cutoff = cutoff();
            if (cutoff.isEmpty()) return;
            treasury.submitLogPurge(cutoff.get()).join();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not delete old treasury log entries", e);
        }
    }
}
