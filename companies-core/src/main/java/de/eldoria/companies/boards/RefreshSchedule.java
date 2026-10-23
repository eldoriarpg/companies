/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Decides when the board counts are refreshed: every interval, sooner when an order changed but not more often than
 * the minimum gap, and at once when forced.
 * <p>
 * Order events may fire off the main thread, so marking is thread safe.
 */
public final class RefreshSchedule {
    private final Clock clock;
    private final Supplier<Duration> interval;
    private final Duration minimumGap;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean forced = new AtomicBoolean(true);
    private volatile Instant lastStart = Instant.MIN;

    /**
     * @param clock      clock of the schedule
     * @param interval   the current time between two regular refreshes
     * @param minimumGap shortest time between two refreshes caused by changed orders
     */
    public RefreshSchedule(Clock clock, Supplier<Duration> interval, Duration minimumGap) {
        this.clock = clock;
        this.interval = interval;
        this.minimumGap = minimumGap;
    }

    /**
     * Notes that an order changed, so the counts are refreshed once the minimum gap has passed.
     */
    public void markDirty() {
        dirty.set(true);
    }

    /**
     * Refreshes on the next check, for a new sign or changed presets.
     */
    public void force() {
        forced.set(true);
    }

    /**
     * @return whether a refresh is due now
     */
    public boolean due() {
        if (forced.get()) return true;
        var now = clock.instant();
        if (dirty.get() && !now.isBefore(lastStart.plus(minimumGap))) return true;
        return !now.isBefore(lastStart.plus(interval.get()));
    }

    /**
     * Notes that a refresh starts now. Changes marked after this call cause the next refresh.
     */
    public void started() {
        lastStart = clock.instant();
        dirty.set(false);
        forced.set(false);
    }
}
