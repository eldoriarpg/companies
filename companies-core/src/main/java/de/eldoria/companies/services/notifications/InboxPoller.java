/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.data.repository.AInboxData;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
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
 * <p>
 * While the node bus is realtime, the node a player is on is told about a new entry at once and claims it through
 * {@link #deliver(UUID, LocalDateTime)}. The polls then only catch what was missed and run every
 * {@link #SAFETY_SECONDS} seconds, or less often when {@code notifications.pollSeconds} is longer.
 */
public final class InboxPoller {
    /**
     * The most entries one poll shows.
     */
    static final int LIMIT = 200;
    /**
     * The shortest interval between two polls while the node bus delivers new entries at once.
     */
    static final int SAFETY_SECONDS = 300;

    private final AInboxData inbox;
    private final NotificationService notifications;
    private final Logger logger;
    private ScheduledExecutorService pool;
    private Supplier<NotificationSettings> settings;
    private BooleanSupplier realtime = () -> false;
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
    public void start(ScheduledExecutorService pool, Supplier<NotificationSettings> settings) {
        start(pool, settings, () -> false);
    }

    /**
     * Polls in a fixed interval, which is long while new entries are announced at once.
     *
     * @param pool     runs the polls
     * @param settings notification settings, read for the interval now and on {@link #reschedule()}
     * @param realtime whether new entries are announced at once, read now and on {@link #reschedule()}
     */
    public synchronized void start(
            ScheduledExecutorService pool, Supplier<NotificationSettings> settings, BooleanSupplier realtime) {
        this.pool = pool;
        this.settings = settings;
        this.realtime = realtime;
        schedule();
    }

    /**
     * Polls in the interval of the current settings and bus state from now on. Does nothing before {@link #start} or
     * while the interval stays the same.
     */
    public synchronized void reschedule() {
        if (pool == null || currentInterval() == interval) return;
        task.cancel(false);
        schedule();
    }

    /**
     * The seconds between two polls.
     *
     * @param settings notification settings
     * @param realtime whether new entries are announced at once
     * @return {@code pollSeconds}, at least {@link #SAFETY_SECONDS} while realtime
     */
    static int interval(NotificationSettings settings, boolean realtime) {
        var seconds = settings.pollSeconds();
        return realtime ? Math.max(seconds, SAFETY_SECONDS) : seconds;
    }

    private int currentInterval() {
        return interval(settings.get(), realtime.getAsBoolean());
    }

    /**
     * Polls once on the pool right away, for example after announcements may have been missed. Does nothing before
     * {@link #start}.
     */
    public synchronized void pollNow() {
        if (pool != null) pool.execute(this::pollSafely);
    }

    /**
     * @return seconds between two polls, 0 before the polls started
     */
    synchronized int interval() {
        return interval;
    }

    private void schedule() {
        interval = currentInterval();
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
        return deliver(notifications.onlineHere().join(), now);
    }

    /**
     * Claims and shows the pending entries of one player, when the player is online here. Called when another node
     * announced a new entry. Blocks, so it must not run on the main thread.
     *
     * @param player recipient of the new entry
     * @param now    current time
     * @return number of entries shown
     */
    public int deliver(UUID player, LocalDateTime now) {
        var online = notifications.onlineHere().join();
        return online.contains(player) ? deliver(List.of(player), now) : 0;
    }

    private int deliver(List<UUID> online, LocalDateTime now) {
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
