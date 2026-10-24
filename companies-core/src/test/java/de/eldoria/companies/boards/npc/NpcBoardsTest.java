/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards.npc;

import de.eldoria.companies.boards.Boards;
import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.BoardBindings;
import de.eldoria.companies.configuration.elements.BoardSettings;
import de.eldoria.companies.util.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcBoardsTest {
    private final BoardBindings bindings = new BoardBindings();
    private final List<SearchQuery> shown = new ArrayList<>();
    private final List<CommandFailure> failures = new ArrayList<>();
    private final FakeHook citizens = new FakeHook("Citizens", Set.of("4", "5"));

    private ServerMock server;
    private Plugin plugin;
    private NpcBoards npcBoards;
    private int saves;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.getPluginManager()
                .addPermission(new org.bukkit.permissions.Permission(Permission.Boards.USE, PermissionDefault.TRUE));
        plugin = MockBukkit.createMockPlugin();
        var settings = new BoardSettings();
        var boards = new Boards(
                () -> settings,
                null,
                part -> Set.of("stone", "dirt").contains(part),
                Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC));
        boards.bind((player, query) -> shown.add(query));
        npcBoards = new NpcBoards(
                plugin, boards, () -> bindings, () -> saves++, (sender, failure) -> failures.add(failure));
        npcBoards.start(new NpcHooks(
                name -> name.equals("Citizens"),
                className -> Map.<String, NpcHook>of(NpcHooks.KNOWN.get("Citizens"), citizens)
                        .get(className),
                Logger.getAnonymousLogger()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void startHandsTheClicksToTheBoards() {
        assertEquals(List.of(npcBoards), citizens.handlers());
        assertEquals(List.of("Citizens"), npcBoards.hookNames());
    }

    @Test
    void linkStoresTheResolvedBoard() {
        var binding = npcBoards.link(server.getConsoleSender(), "citizens", "4", " IRON ");

        assertEquals("Citizens", binding.hook());
        assertEquals("4", binding.npc());
        assertEquals("iron", binding.board());
        assertEquals(Optional.of("iron"), bindings.board("Citizens", "4"));
        assertEquals(1, saves);
    }

    @Test
    void linkRefusesUnknownHooksNpcsAndBoards() {
        var console = server.getConsoleSender();

        assertEquals(
                "boards.error.unknownHook",
                assertThrows(CommandFailure.class, () -> npcBoards.link(console, "FancyNpcs", "4", ""))
                        .localeKey());
        assertEquals(
                "boards.error.unknownNpc",
                assertThrows(CommandFailure.class, () -> npcBoards.link(console, "Citizens", "9", ""))
                        .localeKey());
        assertEquals(
                "boards.error.unknown",
                assertThrows(CommandFailure.class, () -> npcBoards.link(console, "Citizens", "4", "emerald"))
                        .localeKey());
        assertTrue(bindings.npcs().isEmpty());
        assertEquals(0, saves);
    }

    @Test
    void clickOnBoundNpcOpensItsBoard() {
        bindings.link("Citizens", "4", "stone");
        var player = server.addPlayer();

        npcBoards.clicked(player, "Citizens", "4");

        assertEquals(1, shown.size());
        assertEquals(List.of("stone"), shown.getFirst().materials());
    }

    @Test
    void clickOnOtherNpcIsIgnored() {
        bindings.link("Citizens", "4", "stone");
        var player = server.addPlayer();

        npcBoards.clicked(player, "Citizens", "5");
        npcBoards.clicked(player, "FancyNpcs", "4");

        assertTrue(shown.isEmpty());
        assertTrue(failures.isEmpty());
    }

    @Test
    void clicksAreRateLimited() {
        bindings.link("Citizens", "4", "");
        var player = server.addPlayer();

        npcBoards.clicked(player, "Citizens", "4");
        npcBoards.clicked(player, "Citizens", "4");

        assertEquals(1, shown.size());
    }

    @Test
    void clickWithoutPermissionIsReported() {
        bindings.link("Citizens", "4", "");
        var player = server.addPlayer();
        player.addAttachment(plugin, Permission.Boards.USE, false);

        npcBoards.clicked(player, "Citizens", "4");

        assertTrue(shown.isEmpty());
        assertEquals(
                List.of("boards.error.permission"),
                failures.stream().map(CommandFailure::localeKey).toList());
    }

    @Test
    void clickOffTheMainThreadOpensOnTheNextTick() throws InterruptedException {
        bindings.link("Citizens", "4", "");
        var player = server.addPlayer();

        var thread = new Thread(() -> npcBoards.clicked(player, "Citizens", "4"));
        thread.start();
        thread.join();
        assertTrue(shown.isEmpty());

        server.getScheduler().performOneTick();

        assertEquals(1, shown.size());
    }

    @Test
    void unlinkRemovesTheBindingOfAGoneNpc() {
        bindings.link("Citizens", "12", "iron");

        var removed = npcBoards.unlink(server.getConsoleSender(), "Citizens", "12");

        assertEquals("iron", removed.orElseThrow().board());
        assertTrue(bindings.npcs().isEmpty());
        assertEquals(1, saves);
        assertTrue(npcBoards.unlink(server.getConsoleSender(), "Citizens", "12").isEmpty());
        assertEquals(1, saves);
    }

    @Test
    void stopUnloadsTheHooks() {
        npcBoards.stop();

        assertEquals(1, citizens.disabled());
        assertTrue(npcBoards.hookNames().isEmpty());
        assertEquals(Optional.empty(), npcBoards.hook("Citizens"));
    }
}
