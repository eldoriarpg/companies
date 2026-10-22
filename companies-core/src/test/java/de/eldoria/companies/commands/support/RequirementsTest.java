/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import de.eldoria.companies.util.Permission;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementsTest {
    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onlyPlayersArePlayers() {
        assertTrue(Requirements.isPlayer(new SourceStack(server.addPlayer())));
        assertFalse(Requirements.isPlayer(new SourceStack(server.getConsoleSender())));
    }

    @Test
    void sendersNarrowToPlayers() {
        var player = server.addPlayer();
        assertSame(player, Senders.player(player));
        var failure = assertThrows(CommandFailure.class, () -> Senders.player(server.getConsoleSender()));
        assertTrue(failure.localeKey().equals("error.onlyPlayer"));
    }

    @Test
    void debugNeedsTheAdminOrTheEldoUtilDebugPermission() {
        var plain = server.addPlayer();
        var admin = server.addPlayer();
        admin.addAttachment(plugin, Permission.Admin.ADMIN, true);
        var debugger = server.addPlayer();
        debugger.addAttachment(plugin, Permission.Admin.DEBUG, true);

        assertFalse(Requirements.mayDebug(new SourceStack(plain)));
        assertTrue(Requirements.mayDebug(new SourceStack(admin)));
        assertTrue(Requirements.mayDebug(new SourceStack(debugger)));
    }

    @Test
    void anyAdminPermissionShowsTheAdminCommand() {
        var plain = server.addPlayer();
        var leveler = server.addPlayer();
        leveler.addAttachment(plugin, Permission.Admin.LEVEL, true);

        assertFalse(Requirements.isAdmin(new SourceStack(plain)));
        assertTrue(Requirements.isAdmin(new SourceStack(leveler)));
    }
}
