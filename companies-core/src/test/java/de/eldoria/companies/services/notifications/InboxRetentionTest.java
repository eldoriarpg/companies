/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InboxRetentionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0);

    private ExecutorService executor;
    private MemoryInboxData data;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        data = new MemoryInboxData(executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private void sent(LocalDateTime created, InboxStatus status) {
        data.submit(UUID.randomUUID(), NotificationType.PAYMENT, NotificationArgs.create(), created, status)
                .join();
    }

    @Test
    void entriesOlderThanTheRetentionAreDeletedReadOrNot() {
        sent(NOW.minusDays(31), InboxStatus.PENDING);
        sent(NOW.minusDays(40), InboxStatus.READ);
        sent(NOW.minusDays(29), InboxStatus.PENDING);

        var deleted = new InboxRetention(data, NotificationSettings::new, Logger.getAnonymousLogger()).purge(NOW);

        assertEquals(2, deleted);
        assertEquals(NOW.minusDays(29), data.only().created());
    }
}
