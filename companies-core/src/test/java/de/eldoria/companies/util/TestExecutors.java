/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Shuts down executors of tests so that no queued database work outlives the test and writes into a temporary
 * directory while it is being deleted.
 */
public final class TestExecutors {
    private TestExecutors() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Lets the executor finish its queued work, waiting up to ten seconds, and stops it.
     *
     * @param executor executor of the test
     */
    public static void drain(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
