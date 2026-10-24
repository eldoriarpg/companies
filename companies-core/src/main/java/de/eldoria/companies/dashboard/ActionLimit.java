/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits the dashboard actions of each player to a number within a window, on top of the gateway's own limit.
 */
public final class ActionLimit {
    private final int actions;
    private final Duration window;
    private final InstantSource clock;
    private final Map<UUID, Deque<Instant>> recent = new ConcurrentHashMap<>();

    /**
     * @param actions the most actions within the window
     * @param window  the window
     * @param clock   the current time
     */
    public ActionLimit(int actions, Duration window, InstantSource clock) {
        this.actions = actions;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Counts an action of a player.
     *
     * @param player the player
     * @return false when the player used up the window and the action must be refused
     */
    public boolean take(UUID player) {
        var now = clock.instant();
        var times = recent.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().plus(window).isAfter(now)) times.pollFirst();
            if (times.size() >= actions) return false;
            times.addLast(now);
            return true;
        }
    }
}
