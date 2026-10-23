/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteCompanyData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteInboxData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.EventRecorder;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.events.player.ItemsReturnedEvent.ReturnedStack;
import de.eldoria.companies.orders.OrderTable;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemReturnServiceTest {
    @TempDir
    Path directory;

    private ExecutorService executor;
    private Plugin plugin;
    private OrderTable orders;
    private ItemReturnService service;
    private EventRecorder events;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(4);
        orders = OrderTable.create();
        service = new ItemReturnService(plugin, orders.orderData());
        events = EventRecorder.listen(plugin, ItemsReturnedEvent.class);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        MockBukkit.unmock();
    }

    private int waiting(UUID player, ReturnKind kind) {
        return orders.orderData().retrieveItemReturnCount(player, kind).join();
    }

    @Test
    void grantedItemsAreStoredAndFiredOnce() {
        var player = UUID.randomUUID();

        assertTrue(service.store(
                        player,
                        ReturnKind.OWNER,
                        new ItemStack(Material.DIAMOND, 3),
                        70,
                        ItemsReturnedEvent.Reason.ADMIN_GRANT,
                        null)
                .join());

        assertEquals(70, waiting(player, ReturnKind.OWNER));
        var event = events.single(ItemsReturnedEvent.class);
        assertEquals(player, event.player().getUniqueId());
        assertEquals(ItemsReturnedEvent.Reason.ADMIN_GRANT, event.reason());
        assertTrue(event.order().isEmpty());
        assertEquals(70, event.stacks().get(0).amount());
        assertEquals(1, event.stacks().get(0).stack().getAmount());
        assertTrue(event.isAsynchronous());
    }

    @Test
    void severalItemsFireOneEvent() {
        var player = UUID.randomUUID();
        var order = orders.place(0.0);
        var stacks = List.of(
                new ReturnedStack(new ItemStack(Material.STONE), 5),
                new ReturnedStack(new ItemStack(Material.DIRT), 2));

        service.storeAll(player, ReturnKind.OWNER, stacks, ItemsReturnedEvent.Reason.DELIVERY_OVERFLOW, order)
                .join();

        assertEquals(7, waiting(player, ReturnKind.OWNER));
        var event = events.single(ItemsReturnedEvent.class);
        assertEquals(2, event.stacks().size());
        assertEquals(order.id(), event.order().orElseThrow().id());
    }

    @Test
    void nothingToStoreFiresNothing() {
        assertTrue(service.storeAll(UUID.randomUUID(), ReturnKind.OWNER, List.of(), ItemsReturnedEvent.Reason.API, null)
                .join());

        assertTrue(events.of(ItemsReturnedEvent.class).isEmpty());
    }

    @Test
    void disbandingReturnsTheDeliveriesOfItsOrders() throws Exception {
        var worker = UUID.randomUUID();
        var companyData = new SqLiteCompanyData(executor);
        var company =
                companyData.submitCompanyCreation("Acme", JoinMode.INVITE_ONLY).join();
        var order = orders.place(0.0);
        orders.state(order, OrderState.CLAIMED, company);
        orders.deliver(order, 0, Material.STONE, worker, 6);
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        var notifications = new NotificationService(
                plugin,
                new SqLiteInboxData(executor),
                orders.orderData(),
                null,
                NotificationSettings::new,
                (player, type, args) -> {},
                Runnable::run);
        var disband = new DisbandService(plugin, companyData, orders.orderData(), service, notifications);
        var profile = companyData
                .retrieveCompanyProfile(
                        companyData.retrieveCompanyById(company).join().orElseThrow())
                .join()
                .orElseThrow();

        assertTrue(disband.disband(profile));

        var event = events.single(ItemsReturnedEvent.class);
        assertEquals(ItemsReturnedEvent.Reason.COMPANY_DISBANDED, event.reason());
        assertEquals(worker, event.player().getUniqueId());
        assertEquals(6, event.stacks().get(0).amount());
        assertEquals(order.id(), event.order().orElseThrow().id());
        assertEquals(6, waiting(worker, ReturnKind.WORKER));
    }
}
