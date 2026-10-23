/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.data.wrapper.treasury.Treasury;
import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasuryMenusTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 5, 1, 12, 30);

    private final UUID treasurer = UUID.randomUUID();

    private Plugin plugin;
    private MessageSender messages;
    private PlayerMock viewer;
    private MenuActions actions;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
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

    private static List<TreasuryLogEntry> entries(UUID actor) {
        return List.of(
                new TreasuryLogEntry(3, 7, TreasuryKind.WITHDRAW, -1250, 8750, actor, null, null, TIME),
                new TreasuryLogEntry(2, 7, TreasuryKind.PAYOUT_SHARE, 1000, 10000, null, 42, null, TIME),
                new TreasuryLogEntry(1, 7, TreasuryKind.SHARE_CHANGED, 0, 9000, actor, null, "15", TIME));
    }

    private TreasuryMenu.Model model(boolean manage) {
        return new TreasuryMenu.Model(
                "Miners",
                new Treasury(7, 8750, 30),
                20,
                manage,
                entries(treasurer),
                Map.of(treasurer, "Tess"),
                Optional.empty());
    }

    @Test
    void membersWithoutPermissionOnlyDepositAndReadTheLog() {
        var text = new TreasuryMenu(messages, economy(), new NoHandlers()).chat(viewer, model(false), actions);

        assertTrue(text.build().contains("<act_deposit>"));
        assertTrue(text.build().contains("<act_log>"));
        assertFalse(text.build().contains("<act_withdraw>"));
        assertFalse(text.build().contains("<act_share>"));
    }

    @Test
    void treasurersAlsoWithdrawAndSetTheShareCappedByTheConfiguration() {
        var text = new TreasuryMenu(messages, economy(), new NoHandlers()).chat(viewer, model(true), actions);

        assertTrue(text.build().contains("<act_withdraw>"));
        assertTrue(text.build().contains("<act_share>"));
        var shown = plain(text);
        assertTrue(shown.contains("$87.50"), shown);
        assertTrue(shown.contains("20 %"), shown);
    }

    @Test
    void logLinesNameTheActorAndTheOwnOrderOnly() {
        var shown = plain(TreasuryTexts.entries("entry", entries(treasurer), Map.of(treasurer, "Tess"), economy()));

        var lines = shown.split("\n");
        assertTrue(lines[0].contains("$-12.50") && lines[0].contains("Tess"), lines[0]);
        assertTrue(lines[1].contains("+$10.00") && lines[1].contains("#42"), lines[1]);
        assertTrue(lines[2].contains("Tess: 15 %"), lines[2]);
    }

    @Test
    void logShowsPagingAndBack() {
        var menu = new TreasuryLogMenu(messages, economy(), new NoLogHandlers());
        var model = TreasuryLogMenu.Model.of("Miners", 2, entries(treasurer), Map.of(treasurer, "Tess"), 3);

        var text = menu.chat(viewer, model, actions);

        assertTrue(text.build().contains("<act_previous>"));
        assertTrue(text.build().contains("<act_next>"));
        assertTrue(text.build().contains("<act_back>"));
    }

    private static final class NoHandlers implements TreasuryMenu.Handlers {
        @Override
        public void deposit(Player player, String amount) {}

        @Override
        public void withdraw(Player player, String amount) {}

        @Override
        public void share(Player player, int percent) {}

        @Override
        public void log(Player player, int page) {}
    }

    private static final class NoLogHandlers implements TreasuryLogMenu.Handlers {
        @Override
        public void page(Player player, int page) {}

        @Override
        public void back(Player player) {}
    }
}
