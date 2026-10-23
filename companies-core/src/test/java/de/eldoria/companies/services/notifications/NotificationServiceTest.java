/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.order.OrderRemovedEvent;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTest {
    private ServerMock server;
    private Plugin plugin;
    private ExecutorService executor;
    private MemoryInboxData data;
    private NotificationService service;
    private PlayerMock online;
    private final List<Shown> shown = new CopyOnWriteArrayList<>();

    record Shown(UUID player, NotificationType type) {}

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        executor = Executors.newSingleThreadExecutor();
        data = new MemoryInboxData(executor);
        service = new NotificationService(
                plugin,
                data,
                null,
                null,
                NotificationSettings::new,
                (player, type, args) -> shown.add(new Shown(player.getUniqueId(), type)),
                Runnable::run);
        online = server.addPlayer("Online");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private static NotificationArgs args() {
        return NotificationArgs.create().text("order_name", "Wood").number("order_id", 4);
    }

    @Test
    void onlyPurgedOrdersTellTheOwnerAboutTheRemoval() throws InterruptedException {
        var owner = UUID.randomUUID();
        for (var reason : List.of(OrderRemovedEvent.Reason.CANCELLED, OrderRemovedEvent.Reason.ADMIN)) {
            service.onOrderRemoved(new OrderRemovedEvent(new SimpleOrder(UUID.randomUUID(), "Wood", 0), reason));
        }
        service.onOrderRemoved(
                new OrderRemovedEvent(new SimpleOrder(owner, "Wood", 0), OrderRemovedEvent.Reason.PURGED));

        var deadline = System.currentTimeMillis() + 5_000;
        while (data.all().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        var entry = data.only();
        assertEquals(owner, entry.recipient());
        assertEquals(NotificationType.MY_ORDER_REMOVED, entry.type());
    }

    @Test
    void onlineHereIsShownAndStoredRead() {
        service.send(online.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                .join();

        assertEquals(List.of(new Shown(online.getUniqueId(), NotificationType.ORDER_DELIVERED)), shown);
        var entry = data.only();
        assertTrue(entry.delivered());
        assertTrue(entry.read());
    }

    @Test
    void offlineIsStoredPending() {
        var offline = UUID.randomUUID();

        service.send(offline, NotificationType.ORDER_DELIVERED, args()).join();

        assertTrue(shown.isEmpty());
        var entry = data.only();
        assertEquals(offline, entry.recipient());
        assertFalse(entry.delivered());
        assertNull(entry.readAt());
    }

    @Test
    void mutedIsStoredDeliveredUnreadAndNotShown() {
        data.submitMuted(online.getUniqueId(), NotificationCategory.ORDERS.bit())
                .join();

        service.send(online.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                .join();

        assertTrue(shown.isEmpty());
        var entry = data.only();
        assertTrue(entry.delivered());
        assertFalse(entry.read());
    }

    @Test
    void categoriesThatCannotBeMutedIgnoreTheirBit() {
        data.submitMuted(online.getUniqueId(), NotificationCategory.MY_ORDERS.bit())
                .join();

        service.send(online.getUniqueId(), NotificationType.MY_ORDER_DONE, args())
                .join();

        assertEquals(1, shown.size());
        assertTrue(data.only().read());
    }

    @Test
    void summaryCountsUnreadLeavesOutMutedAndDeliversPending() {
        var player = online.getUniqueId();
        var now = LocalDateTime.now();
        data.submit(player, NotificationType.ORDER_DELIVERED, args(), now, InboxStatus.PENDING)
                .join();
        data.submit(player, NotificationType.LEGACY, NotificationArgs.create(), now, InboxStatus.PENDING)
                .join();
        data.submit(player, NotificationType.PAYMENT, args(), now, InboxStatus.DELIVERED)
                .join();
        data.submit(player, NotificationType.MEMBER_JOINED, args(), now, InboxStatus.READ)
                .join();
        data.submitMuted(player, NotificationCategory.PAYMENTS.bit()).join();

        var counted = service.summarize(online).join();

        assertEquals(2, counted);
        assertTrue(data.all().stream().allMatch(entry -> entry.delivered()));
        assertNotNull(online.nextComponentMessage());
    }

    @Test
    void noSummaryWithoutUnread() {
        assertEquals(0, service.summarize(online).join());
        assertNull(online.nextComponentMessage());
    }

    @Test
    void summaryOpensTheInbox() {
        var summary = NotificationService.summary(3).build();

        assertTrue(summary.contains("inbox.summary"));
        assertTrue(summary.contains("<inbox_open>"));
    }
}
