/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MenuActionsTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void chatActionsStyleTheEnclosedText() {
        var plugin = MockBukkit.createMockPlugin();
        var actions = new MenuActions(plugin, server.addPlayer(), Duration.ofSeconds(30));

        var action = actions.chatActionOnce("act_done", player -> {});
        var rendered = MiniMessage.miniMessage().deserialize("<act_done>[Done]</act_done>", action);

        assertEquals("act_done", action.key());
        assertNotNull(rendered.clickEvent());
    }
}
