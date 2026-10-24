/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Allows each key, such as a client address or a session, a number of actions per sliding window. Each key has its
 * own bucket, so a check touches only that key; {@link #sweep()} drops the buckets of keys that went quiet.
 */
public final class RateLimiter {
    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> actions = new ConcurrentHashMap<>();

    /**
     * @param limit actions per window
     * @param window the window
     * @param clock the time
     */
    public RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Counts an action of a key if it is still allowed.
     *
     * @param key the address or session
     * @return whether the action is allowed
     */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        AtomicBoolean allowed = new AtomicBoolean();
        actions.compute(key, (ignored, recent) -> {
            Deque<Instant> bucket = recent == null ? new ArrayDeque<>() : recent;
            prune(bucket, now);
            if (bucket.size() < limit) {
                bucket.addLast(now);
                allowed.set(true);
            }
            return bucket;
        });
        return allowed.get();
    }

    /**
     * Drops the buckets of keys without an action in the window. Called every few seconds.
     */
    public void sweep() {
        Instant now = clock.instant();
        for (String key : List.copyOf(actions.keySet())) {
            actions.computeIfPresent(key, (ignored, bucket) -> {
                prune(bucket, now);
                return bucket.isEmpty() ? null : bucket;
            });
        }
    }

    /**
     * @return how many keys have a bucket
     */
    int keys() {
        return actions.size();
    }

    private void prune(Deque<Instant> bucket, Instant now) {
        Instant start = now.minus(window);
        while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(start)) bucket.pollFirst();
    }
}
