/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/**
 * A token bucket of requests per minute, which Discord's rate limit answers can pause further.
 * <p>
 * The bucket starts full and refills evenly over a minute. A pause holds every request until it ends.
 */
final class RequestBudget {
    private static final double NANOS_PER_MINUTE = Duration.ofMinutes(1).toNanos();

    private final int capacity;
    private final InstantSource clock;
    private double tokens;
    private Instant refilled;
    private Instant pausedUntil;

    /**
     * @param perMinute requests allowed per minute, at least one
     * @param clock     source of the current time
     */
    RequestBudget(int perMinute, InstantSource clock) {
        this.capacity = Math.max(1, perMinute);
        this.clock = clock;
        this.tokens = capacity;
        this.refilled = clock.instant();
        this.pausedUntil = refilled;
    }

    /**
     * How long to wait before the next request may be sent.
     *
     * @return zero when a request may be sent now
     */
    Duration waitTime() {
        refill();
        var now = clock.instant();
        var pause = now.isBefore(pausedUntil) ? Duration.between(now, pausedUntil) : Duration.ZERO;
        if (tokens >= 1) return pause;
        var refill = Duration.ofNanos((long) Math.ceil((1 - tokens) * NANOS_PER_MINUTE / capacity));
        return pause.compareTo(refill) > 0 ? pause : refill;
    }

    /**
     * Uses one request.
     */
    void take() {
        refill();
        tokens -= 1;
    }

    /**
     * Holds all requests for the duration from now.
     *
     * @param duration how long to hold them
     */
    void pause(Duration duration) {
        var until = clock.instant().plus(duration);
        if (until.isAfter(pausedUntil)) pausedUntil = until;
    }

    private void refill() {
        var now = clock.instant();
        var elapsed = Duration.between(refilled, now).toNanos();
        if (elapsed <= 0) return;
        tokens = Math.min(capacity, tokens + elapsed * capacity / NANOS_PER_MINUTE);
        refilled = now;
    }
}
