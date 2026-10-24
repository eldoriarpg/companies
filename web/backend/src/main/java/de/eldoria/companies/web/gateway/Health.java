/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Whether the gateway can serve: the database answers. A check is reused for {@value #REUSE_SECONDS} seconds, so
 * frequent health requests cost one database round trip at most that often. A check runs on its own virtual thread
 * and counts as down when it takes longer than the timeout, so a database that hangs, such as a pool waiting for a
 * connection, does not hold health requests; the hanging check is waited for again by the next request instead of
 * starting another. Only a change between up and down is logged.
 */
public final class Health {
    private static final Logger log = LoggerFactory.getLogger(Health.class);
    private static final long REUSE_SECONDS = 5;
    private final BooleanSupplier check;
    private final Clock clock;
    private final Duration timeout;
    private final Executor executor;
    private @Nullable Instant checkedAt;
    private @Nullable CompletableFuture<Boolean> running;
    private boolean up = true;

    /**
     * @param check asks the database
     * @param clock the time
     * @param timeout how long a health request waits for a check
     * @param executor where checks run
     */
    public Health(BooleanSupplier check, Clock clock, Duration timeout, Executor executor) {
        this.check = check;
        this.clock = clock;
        this.timeout = timeout;
        this.executor = executor;
    }

    /**
     * @param check asks the database
     * @param clock the time
     * @return a health check that waits two seconds and runs checks on virtual threads
     */
    public static Health of(BooleanSupplier check, Clock clock) {
        return new Health(check, clock, Duration.ofSeconds(2), Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * @return whether the database answered at the last check, checking again when that is older than the reuse time
     */
    public synchronized boolean up() {
        Instant now = clock.instant();
        Instant last = checkedAt;
        if (last != null && last.plus(Duration.ofSeconds(REUSE_SECONDS)).isAfter(now)) return up;
        CompletableFuture<Boolean> check = running;
        if (check == null) {
            check = CompletableFuture.supplyAsync(this.check::getAsBoolean, executor);
            running = check;
        }
        boolean answered;
        try {
            answered = check.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            running = null;
        } catch (TimeoutException e) {
            answered = false;
        } catch (ExecutionException e) {
            answered = false;
            running = null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return up;
        }
        if (answered != up) {
            if (answered) log.info("The database answers again");
            else log.warn("The database does not answer");
        }
        up = answered;
        checkedAt = now;
        return up;
    }
}
