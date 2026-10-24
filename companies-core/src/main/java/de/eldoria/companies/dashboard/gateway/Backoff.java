/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.gateway;

import java.time.Duration;
import java.util.function.DoubleSupplier;

/**
 * The waits between reconnects: 2 seconds, doubling up to 60 seconds, each shortened by up to a fifth at random so
 * nodes that lost the gateway together do not return together. A connection the gateway welcomed starts over at 2
 * seconds.
 */
public final class Backoff {
    /**
     * The first wait.
     */
    public static final Duration FIRST = Duration.ofSeconds(2);

    /**
     * The longest wait.
     */
    public static final Duration MAX = Duration.ofSeconds(60);

    private static final double JITTER = 0.2;

    private final DoubleSupplier random;
    private Duration next = FIRST;

    /**
     * @param random a random number from 0 inclusive to 1 exclusive, for the jitter
     */
    public Backoff(DoubleSupplier random) {
        this.random = random;
    }

    /**
     * The wait before the next attempt; the one after it doubles.
     *
     * @return the wait
     */
    public synchronized Duration next() {
        var base = next;
        next = base.multipliedBy(2).compareTo(MAX) > 0 ? MAX : base.multipliedBy(2);
        var jitter = (long) (base.toMillis() * JITTER * random.getAsDouble());
        return base.minusMillis(jitter);
    }

    /**
     * Starts over at the first wait.
     */
    public synchronized void reset() {
        next = FIRST;
    }
}
