/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.keys;

import java.io.IOException;
import java.time.Instant;

/**
 * Trades a Lyna dashboard key for the current one, {@code POST /api/v1/dashboard/key/refresh}.
 * <p>
 * Lyna answers 200 with the same key until its refresh time and with its successor after it. It refuses an unknown,
 * expired or retired key with 401, a key without access with 403 and a refresh asked too soon with 429.
 */
@FunctionalInterface
public interface LynaKeys {
    /**
     * Asks Lyna for the current key.
     *
     * @param key the key the network has now
     * @return Lyna's answer
     * @throws IOException          when Lyna could not be reached or answered something else
     * @throws InterruptedException when the thread is interrupted while waiting
     */
    Answer refresh(String key) throws IOException, InterruptedException;

    /**
     * What Lyna answered.
     */
    sealed interface Answer {}

    /**
     * The current key: the same one until {@code refreshAt}, then its successor. Never log {@code key}.
     *
     * @param key       the key to use from now on
     * @param refreshAt when Lyna hands out the successor
     */
    record Issued(String key, Instant refreshAt) implements Answer {
        @Override
        public String toString() {
            return "Issued[refreshAt=" + refreshAt + "]";
        }
    }

    /**
     * Lyna refused the key.
     *
     * @param status the HTTP status: 401, 403 or 429
     * @param reason the reason Lyna named, e.g. {@code EXPIRED}
     */
    record Refused(int status, String reason) implements Answer {
        /**
         * @return true when the key will not work again and a new one is needed
         */
        public boolean keyLost() {
            return status == 401 || status == 403;
        }
    }
}
