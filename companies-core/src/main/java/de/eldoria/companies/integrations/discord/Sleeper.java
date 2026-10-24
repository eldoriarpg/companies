/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.time.Duration;

/**
 * Waits on the sending thread.
 */
@FunctionalInterface
public interface Sleeper {
    /**
     * Waits with {@link Thread#sleep(Duration)}.
     */
    Sleeper THREAD = Thread::sleep;

    /**
     * Waits for the duration.
     *
     * @param duration how long to wait
     * @throws InterruptedException when the thread is interrupted while waiting
     */
    void sleep(Duration duration) throws InterruptedException;
}
