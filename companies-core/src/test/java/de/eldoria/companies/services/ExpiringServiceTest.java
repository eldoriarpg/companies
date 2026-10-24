/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiringServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 10, 12, 0);
    private static final int DELIVERY_HOURS = 48;
    private static final int WARNING_HOURS = 12;

    private static boolean claimedHoursAgo(long hours) {
        return ExpiringService.expiresSoon(NOW.minusHours(hours), NOW, DELIVERY_HOURS, WARNING_HOURS);
    }

    @Test
    void freshOrderIsNotReported() {
        assertFalse(claimedHoursAgo(1));
    }

    @Test
    void orderJustBeforeTheWindowIsNotReported() {
        assertFalse(ExpiringService.expiresSoon(NOW.minusHours(36).plusMinutes(1), NOW, DELIVERY_HOURS, WARNING_HOURS));
    }

    @Test
    void orderEnteringTheWindowIsReported() {
        assertTrue(claimedHoursAgo(36));
    }

    @Test
    void orderInsideTheWindowIsReported() {
        assertTrue(claimedHoursAgo(47));
    }

    @Test
    void expiredOrderIsNotReported() {
        assertFalse(claimedHoursAgo(48));
        assertFalse(claimedHoursAgo(72));
    }

    @Test
    void orderWithoutClaimTimeIsNotReported() {
        assertFalse(ExpiringService.expiresSoon(null, NOW, DELIVERY_HOURS, WARNING_HOURS));
    }
}
