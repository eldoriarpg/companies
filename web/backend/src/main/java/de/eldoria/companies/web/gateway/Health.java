/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * Whether the gateway can serve: the database answers. A check is reused for {@value #REUSE_SECONDS} seconds, so
 * frequent health requests cost one database round trip at most that often. Only a change between up and down is
 * logged.
 */
public final class Health {
    private static final Logger log = LoggerFactory.getLogger(Health.class);
    private static final long REUSE_SECONDS = 5;
    private final BooleanSupplier check;
    private final Clock clock;
    private @Nullable Instant checkedAt;
    private boolean up = true;

    /**
     * @param check asks the database
     * @param clock the time
     */
    public Health(BooleanSupplier check, Clock clock) {
        this.check = check;
        this.clock = clock;
    }

    /**
     * @return whether the database answered at the last check, checking again when that is older than the reuse time
     */
    public synchronized boolean up() {
        Instant now = clock.instant();
        Instant last = checkedAt;
        if (last != null && last.plus(Duration.ofSeconds(REUSE_SECONDS)).isAfter(now)) return up;
        boolean answered = check.getAsBoolean();
        if (answered != up) {
            if (answered) log.info("The database answers again");
            else log.warn("The database does not answer");
        }
        up = answered;
        checkedAt = now;
        return up;
    }
}
