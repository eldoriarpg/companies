/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.components.order.HistoryOutcome;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.menus.order.OrderFixtures;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryMenusTest {
    @BeforeEach
    void useServerZone() {
        Times.useZone(() -> ZoneOffset.UTC);
    }

    @AfterEach
    void useMachineZone() {
        Times.useZone(Times::machineZone);
    }

    private static final LocalDateTime PLACED = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final LocalDateTime FINISHED = LocalDateTime.of(2026, 3, 2, 18, 30);

    private final UUID worker = UUID.randomUUID();
    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock viewer;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
        viewer = server.addPlayer("Viewer");
        actions = new MenuActions(plugin, viewer, MenuActions.DEFAULT_LIFETIME);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    private static Economy economy() {
        return (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(), new Class<?>[] {Economy.class}, (proxy, method, args) -> {
                    if (method.getName().equals("format")) {
                        return String.format(Locale.ROOT, "$%.2f", (double) args[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private String plain(MenuText text) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messages.serializeMessage(
                        viewer, text.build(), text.replacements().toArray(TagResolver[]::new)));
    }

    private List<HistorySnapshot.Line> lines() {
        return List.of(new HistorySnapshot.Line(OrderFixtures.named(Material.STONE), false, 10, 10, 100.0));
    }

    private HistoryEntry ownerEntry() {
        var snapshot = new HistorySnapshot(lines(), 100.0, 5.0, 0, 0, 0, List.of());
        return new HistoryEntry(
                11, 3, viewer.getUniqueId(), null, HistoryOutcome.RECEIVED, "<red>Stone", PLACED, FINISHED, snapshot);
    }

    private HistoryEntry companyEntry() {
        var snapshot = new HistorySnapshot(
                lines(), 100.0, 0, 100.0, 10.0, 90.0, List.of(new HistorySnapshot.Worker(worker, 10, 100.0, 90.0)));
        return new HistoryEntry(12, 3, null, 7, HistoryOutcome.DELIVERED, "Stone", PLACED, FINISHED, snapshot);
    }

    private static HistoryNavigation noNavigation() {
        return new HistoryNavigation() {
            @Override
            public void page(Player viewer, int page) {}

            @Override
            public void entry(Player viewer, long entry, int returnPage) {}
        };
    }

    @Test
    void ownerEntryShowsPriceAndFeeAndNoWorkers() {
        var menu = new HistoryEntryMenu(messages, economy(), noNavigation());
        var model = new HistoryEntryMenu.Model(ownerEntry(), Map.of(worker, "Worker"), 1);

        var shown = plain(menu.chat(viewer, model, actions));

        assertTrue(shown.contains("<red>Stone"));
        assertTrue(shown.contains("$100.00"));
        assertTrue(shown.contains("$5.00"));
        assertTrue(shown.contains("02.03.26 18:30"));
        assertFalse(shown.contains("Worker"));
        assertFalse(shown.contains("history.gross"));
    }

    @Test
    void companyEntryShowsPayoutsAndMembers() {
        var menu = new HistoryEntryMenu(messages, economy(), noNavigation());
        var model = new HistoryEntryMenu.Model(companyEntry(), Map.of(worker, "Worker"), 1);

        var shown = plain(menu.chat(viewer, model, actions));

        assertTrue(shown.contains("Worker 10"));
        assertTrue(shown.contains("$90.00"));
        assertTrue(shown.contains("$10.00"));
        assertFalse(shown.contains(viewer.getName()));
    }

    @Test
    void listShowsOwnerPriceAndCompanyNet() {
        var menu = new HistoryListMenu(
                messages, economy(), noNavigation(), MenuText.create().text("History"));
        var model = new HistoryListMenu.Model(1, List.of(ownerEntry(), companyEntry()), false);

        var text = menu.chat(viewer, model, actions);
        var shown = plain(text);

        assertTrue(text.build().contains("<act_entry_0>"));
        assertTrue(text.build().contains("<act_entry_1>"));
        assertFalse(text.build().contains("run_command"));
        assertTrue(shown.contains("<red>Stone"));
        assertTrue(shown.contains("$100.00"));
        assertTrue(shown.contains("$90.00"));
    }

    @Test
    void fullPageMayHaveANextPage() {
        var entries = List.of(ownerEntry(), ownerEntry());

        assertTrue(HistoryListMenu.Model.of(1, entries, 2).hasNext());
        assertFalse(HistoryListMenu.Model.of(1, entries, 3).hasNext());
    }
}
