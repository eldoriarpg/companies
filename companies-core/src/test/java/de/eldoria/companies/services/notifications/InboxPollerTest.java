/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxPollerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0);

    private Plugin plugin;
    private ExecutorService executor;
    private MemoryInboxData data;
    private PlayerMock online;
    private final List<UUID> shown = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        executor = Executors.newSingleThreadExecutor();
        data = new MemoryInboxData(executor);
        online = server.addPlayer("Online");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private InboxPoller poller() {
        var service = new NotificationService(
                plugin,
                data,
                null,
                null,
                NotificationSettings::new,
                (player, type, args) -> shown.add(player.getUniqueId()),
                Runnable::run);
        return new InboxPoller(data, service, plugin.getLogger());
    }

    private void pending(UUID player, NotificationType type) {
        data.submit(player, type, NotificationArgs.create(), NOW, InboxStatus.PENDING)
                .join();
    }

    @Test
    void aReloadedIntervalReschedulesThePolls() {
        var pool = new ScheduledThreadPoolExecutor(1);
        pool.setRemoveOnCancelPolicy(true);
        var current = new AtomicReference<>(settings("pollSeconds: 100"));
        var poller = poller();

        try {
            poller.start(pool, current::get);
            poller.reschedule();
            assertEquals(100, poller.interval());
            assertEquals(1, pool.getQueue().size());

            current.set(settings("pollSeconds: 200"));
            poller.reschedule();

            assertEquals(200, poller.interval());
            assertEquals(1, pool.getQueue().size());
            var next = (ScheduledFuture<?>) pool.getQueue().peek();
            assertTrue(next.getDelay(TimeUnit.SECONDS) > 100);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void whileRealtimeThePollsOnlyRunInTheSafetyInterval() {
        var pool = new ScheduledThreadPoolExecutor(1);
        pool.setRemoveOnCancelPolicy(true);
        var realtime = new AtomicBoolean(true);
        var poller = poller();

        try {
            poller.start(pool, () -> settings("pollSeconds: 10"), realtime::get);
            assertEquals(InboxPoller.SAFETY_SECONDS, poller.interval());

            realtime.set(false);
            poller.reschedule();

            assertEquals(10, poller.interval());
            assertEquals(1, pool.getQueue().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aLongerPollIntervalIsKeptWhileRealtime() {
        assertEquals(900, InboxPoller.interval(settings("pollSeconds: 900"), true));
        assertEquals(InboxPoller.SAFETY_SECONDS, InboxPoller.interval(settings("pollSeconds: 10"), true));
        assertEquals(10, InboxPoller.interval(settings("pollSeconds: 10"), false));
    }

    @Test
    void anAnnouncedEntryIsShownOnceToThePlayerOnlineHere() {
        pending(online.getUniqueId(), NotificationType.ORDER_DELIVERED);
        var first = poller();
        var second = poller();

        assertEquals(1, first.deliver(online.getUniqueId(), NOW));
        assertEquals(0, second.deliver(online.getUniqueId(), NOW));
        assertEquals(0, second.poll(NOW));

        assertEquals(List.of(online.getUniqueId()), shown);
    }

    @Test
    void anAnnouncedEntryOfAPlayerElsewhereStaysPending() {
        var elsewhere = UUID.randomUUID();
        pending(elsewhere, NotificationType.ORDER_DELIVERED);
        pending(online.getUniqueId(), NotificationType.ORDER_DELIVERED);

        assertEquals(0, poller().deliver(elsewhere, NOW));

        assertTrue(shown.isEmpty());
    }

    private static NotificationSettings settings(String yaml) {
        return YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build()
                .readValue(yaml, NotificationSettings.class);
    }

    @Test
    void aClaimedEntryIsShownOnce() {
        pending(online.getUniqueId(), NotificationType.ORDER_DELIVERED);
        var first = poller();
        var second = poller();

        assertEquals(1, first.poll(NOW));
        assertEquals(0, second.poll(NOW));
        assertEquals(0, first.poll(NOW));

        assertEquals(List.of(online.getUniqueId()), shown);
        var entry = data.only();
        assertTrue(entry.delivered());
        assertTrue(entry.read());
    }

    @Test
    void mutedEntriesAreNotShown() {
        pending(online.getUniqueId(), NotificationType.PAYMENT);
        data.submitMuted(online.getUniqueId(), NotificationCategory.PAYMENTS.bit())
                .join();

        assertEquals(0, poller().poll(NOW));

        assertTrue(shown.isEmpty());
        assertFalse(data.only().read());
    }

    @Test
    void entriesOfPlayersOnlineElsewhereStayPending() {
        pending(UUID.randomUUID(), NotificationType.ORDER_DELIVERED);

        assertEquals(0, poller().poll(NOW));

        assertTrue(shown.isEmpty());
        assertFalse(data.only().delivered());
    }
}
