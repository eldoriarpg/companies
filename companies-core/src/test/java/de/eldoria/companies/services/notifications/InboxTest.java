/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.data.wrapper.inbox.InboxEntry;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0);

    private final UUID player = UUID.randomUUID();
    private ExecutorService executor;
    private MemoryInboxData data;
    private Inbox inbox;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        data = new MemoryInboxData(executor);
        inbox = new Inbox(data, NotificationSettings::new);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private void entries(int count) {
        for (var i = 0; i < count; i++) {
            data.submit(
                            player,
                            NotificationType.PAYMENT,
                            NotificationArgs.create(),
                            NOW.minusMinutes(i),
                            InboxStatus.PENDING)
                    .join();
        }
    }

    @Test
    void aPageIsShownUnreadAndMarkedReadAfterwards() {
        entries(12);

        var page = inbox.page(player, 1, NOW).join();

        assertEquals(10, page.entries().size());
        assertTrue(page.hasNext());
        assertTrue(page.entries().stream().noneMatch(InboxEntry::read));
        assertEquals(NOW, page.entries().getFirst().created());
        assertEquals(10, data.all().stream().filter(InboxEntry::read).count());
    }

    @Test
    void theLastPageHasNoNext() {
        entries(12);

        var page = inbox.page(player, 2, NOW).join();

        assertEquals(2, page.entries().size());
        assertFalse(page.hasNext());
        assertEquals(2, page.number());
    }

    @Test
    void anExactlyFullPageHasNoNext() {
        entries(10);

        assertFalse(inbox.page(player, 1, NOW).join().hasNext());
    }

    @Test
    void readAllAndClearRead() {
        entries(3);

        assertEquals(3, inbox.readAll(player, NOW).join());
        assertEquals(3, inbox.clearRead(player).join());
        assertTrue(data.all().isEmpty());
    }

    @Test
    void muteAndUnmuteKeepOtherCategories() {
        assertEquals(
                NotificationCategory.ORDERS.bit(),
                inbox.mute(player, NotificationCategory.ORDERS, true).join());
        var both = inbox.mute(player, NotificationCategory.LEVEL, true).join();
        assertTrue(NotificationCategory.ORDERS.mutedBy(both));
        assertTrue(NotificationCategory.LEVEL.mutedBy(both));

        var left = inbox.mute(player, NotificationCategory.ORDERS, false).join();

        assertEquals(NotificationCategory.LEVEL.bit(), left);
        assertEquals(left, inbox.muted(player).join());
    }

    @Test
    void categoriesThatCannotBeMutedAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> inbox.mute(player, NotificationCategory.INVITES, true));
    }
}
