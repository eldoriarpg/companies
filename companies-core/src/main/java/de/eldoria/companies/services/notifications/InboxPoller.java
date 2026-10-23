/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.data.repository.AInboxData;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows the players online on this server what was sent to them while the event happened on another server. Runs on
 * every server.
 * <p>
 * A pending entry is shown only after this server claimed it, and a claim succeeds once across all servers, so an
 * entry shows exactly once even when its player switches servers between the poll and the display. Entries of
 * categories muted after they were sent are left pending; the next join summary marks them delivered.
 */
public final class InboxPoller {
    /**
     * The most entries one poll shows.
     */
    static final int LIMIT = 200;

    private final AInboxData inbox;
    private final NotificationService notifications;
    private final Logger logger;
    private ScheduledExecutorService pool;
    private Supplier<NotificationSettings> settings;
    private ScheduledFuture<?> task;
    private int interval;

    /**
     * @param inbox         the stored notifications
     * @param notifications knows who is online here and shows notifications
     * @param logger        reports failed polls
     */
    public InboxPoller(AInboxData inbox, NotificationService notifications, Logger logger) {
        this.inbox = inbox;
        this.notifications = notifications;
        this.logger = logger;
    }

    /**
     * Polls in a fixed interval.
     *
     * @param pool     runs the polls
     * @param settings notification settings, read for the interval now and on {@link #reschedule()}
     */
    public synchronized void start(ScheduledExecutorService pool, Supplier<NotificationSettings> settings) {
        this.pool = pool;
        this.settings = settings;
        schedule();
    }

    /**
     * Polls in the interval of the current settings from now on. Does nothing before {@link #start} or while the
     * interval stays the same.
     */
    public synchronized void reschedule() {
        if (pool == null || settings.get().pollSeconds() == interval) return;
        task.cancel(false);
        schedule();
    }

    /**
     * @return seconds between two polls, 0 before the polls started
     */
    synchronized int interval() {
        return interval;
    }

    private void schedule() {
        interval = settings.get().pollSeconds();
        task = pool.scheduleWithFixedDelay(this::pollSafely, interval, interval, TimeUnit.SECONDS);
    }

    private void pollSafely() {
        try {
            poll(LocalDateTime.now());
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not deliver notifications from other servers", e);
        }
    }

    /**
     * Claims and shows the pending entries of the players online here. Blocks, so it must not run on the main thread.
     *
     * @param now current time
     * @return number of entries shown
     */
    public int poll(LocalDateTime now) {
        var online = notifications.onlineHere().join();
        if (online.isEmpty()) return 0;
        var masks = new HashMap<UUID, Long>();
        var shown = 0;
        for (var entry : inbox.retrievePending(online, LIMIT).join()) {
            var mask = masks.computeIfAbsent(
                    entry.recipient(), player -> inbox.retrieveMuted(player).join());
            if (entry.type().category().mutedBy(mask)) continue;
            if (!Boolean.TRUE.equals(inbox.submitClaim(entry.id(), now).join())) continue;
            notifications.showLater(entry.recipient(), entry.type(), entry.args());
            shown++;
        }
        return shown;
    }
}
