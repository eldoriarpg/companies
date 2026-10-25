/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.util.StyledMessageSender;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard login spells out the link and the code, so a player who cannot click types them on another device.
 */
class DashboardLoginMenuTest {
    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        StyledMessageSender.register(plugin, ILocalizer.DEFAULT, StyleSettings::new);
    }

    @AfterEach
    void tearDown() {
        MessageSender.unregister(plugin);
        MockBukkit.unmock();
    }

    @Test
    void theLinkIsSpelledOutWithoutItsSchemeAndTheCodeSpaced() {
        var login = new DashboardLoginMenu.Login("https://companies.eldoria.de/l/Ab3x", "kpmwqr");

        assertEquals("companies.eldoria.de/l/Ab3x", login.address());
        assertEquals("K P M W Q R", DashboardLoginMenu.spaced(login.code()));
    }

    @Test
    void theChatOpensTheLinkAndHoldsNothingBack() {
        var player = server.addPlayer();
        var menu = new DashboardLoginMenu(plugin);

        var text = menu.chat(
                player,
                new DashboardLoginMenu.Login("https://companies.eldoria.de/l/Ab3x", "kpmwqr"),
                new MenuActions(plugin, player, menu.lifetime()));

        assertTrue(text.build().contains("dashboard.link"));
        assertTrue(text.build().contains("dashboard.code"));
        assertFalse(menu.holdsChat());
    }
}
