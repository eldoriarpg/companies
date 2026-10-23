/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.data.repository.AInboxData;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deletes notifications older than {@code notifications.retentionDays}, read or not. Runs hourly on the primary server
 * only.
 */
public final class InboxRetention {
    private final AInboxData inbox;
    private final Supplier<NotificationSettings> settings;
    private final Logger logger;

    /**
     * @param inbox    the stored notifications
     * @param settings current notification settings
     * @param logger   reports failed purges
     */
    public InboxRetention(AInboxData inbox, Supplier<NotificationSettings> settings, Logger logger) {
        this.inbox = inbox;
        this.settings = settings;
        this.logger = logger;
    }

    /**
     * Purges once a minute after the start and then every hour.
     *
     * @param pool runs the purges
     */
    public void start(ScheduledExecutorService pool) {
        pool.scheduleAtFixedRate(this::purgeSafely, 1L, 60L, TimeUnit.MINUTES);
    }

    private void purgeSafely() {
        try {
            purge(LocalDateTime.now());
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not delete old notifications", e);
        }
    }

    /**
     * Deletes the notifications sent before the retention period. Blocks.
     *
     * @param now current time
     * @return number of notifications deleted
     */
    public int purge(LocalDateTime now) {
        return inbox.submitPurge(now.minusDays(settings.get().retentionDays())).join();
    }
}
