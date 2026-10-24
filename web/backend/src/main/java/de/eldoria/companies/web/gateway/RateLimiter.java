/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows each key, such as a client address or a session, a number of actions per sliding window.
 */
public final class RateLimiter {
    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> actions = new HashMap<>();

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
    public synchronized boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Instant start = now.minus(window);
        Deque<Instant> recent = actions.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(start)) recent.pollFirst();
        actions.values().removeIf(Deque::isEmpty);
        if (recent.size() >= limit) return false;
        recent.addLast(now);
        actions.put(key, recent);
        return true;
    }
}
