/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.function.Supplier;

/**
 * Keeps a value for a while and reads it again once it is older, for values that are costly to read and may be a bit
 * old, such as the market statistics.
 *
 * @param <T> the value type
 */
public final class Expiring<T> implements Supplier<T> {
    private final Supplier<T> source;
    private final Duration lifetime;
    private final InstantSource clock;
    private @Nullable T value;
    private Instant read = Instant.MIN;

    /**
     * @param source   reads the value
     * @param lifetime how long a value is kept
     * @param clock    the current time
     */
    public Expiring(Supplier<T> source, Duration lifetime, InstantSource clock) {
        this.source = source;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    @Override
    public synchronized T get() {
        var now = clock.instant();
        var current = value;
        if (current != null && read.plus(lifetime).isAfter(now)) return current;
        current = source.get();
        value = current;
        read = now;
        return current;
    }
}
