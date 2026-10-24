/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.boards;

import de.eldoria.companies.commands.company.order.search.SearchQuery;
import de.eldoria.companies.configuration.elements.BoardSettings;
import de.eldoria.companies.util.Permission;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.block.state.SignStateMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignBoardsTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private Plugin plugin;
    private WorldMock world;
    private Boards boards;
    private SignBoards signs;
    private SignBoardProtection protection;
    private final List<String> failures = new ArrayList<>();
    private final List<SearchQuery> opened = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.getPluginManager()
                .addPermission(new org.bukkit.permissions.Permission(Permission.Boards.USE, PermissionDefault.TRUE));
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        var settings = new BoardSettings();
        boards = new Boards(
                () -> settings,
                null,
                part -> Set.of("stone", "dirt").contains(part),
                new MutableClock(Instant.parse("2026-10-23T12:00:00Z")));
        boards.bind((player, query) -> opened.add(query));
        signs = new SignBoards(
                plugin,
                boards,
                () -> settings,
                key -> key.equals("boards.sign.open") ? "<count> open" : key,
                (sender, failure) -> failures.add(failure.localeKey()),
                new MutableClock(Instant.parse("2026-10-23T12:00:00Z")));
        protection = new SignBoardProtection(
                signs.data(),
                signs.index(),
                signs.refresher()::forget,
                (sender, failure) -> failures.add(failure.localeKey()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block sign(int x) {
        var block = world.getBlockAt(x, 64, 0);
        world.loadChunk(block.getChunk());
        block.setType(Material.OAK_SIGN);
        ((BlockMock) block).setState(new WaxableSign(block));
        return block;
    }

    private PlayerMock admin() {
        var player = server.addPlayer();
        player.addAttachment(plugin, Permission.Admin.BOARDS, true);
        return player;
    }

    private SignChangeEvent write(Block block, PlayerMock player, String header, String board) {
        var lines = new ArrayList<Component>(
                List.of(Component.text(header), Component.text(board), Component.empty(), Component.empty()));
        var event = new SignChangeEvent(block, player, lines, Side.FRONT);
        signs.onSignChange(event);
        server.getScheduler().performOneTick();
        return event;
    }

    private Block board(int x, String spec) {
        var block = sign(x);
        write(block, admin(), "[Companies]", spec);
        return block;
    }

    private static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    @Test
    void headerWithoutPermissionIsCleared() {
        var block = sign(0);

        var event = write(block, server.addPlayer(), "[companies]", "stone");

        assertEquals("", plain(event.line(0)));
        assertEquals(List.of("boards.error.create"), failures);
        assertTrue(signs.data().read(block).isEmpty());
        assertEquals(0, signs.index().size());
    }

    @Test
    void unknownBoardIsCleared() {
        var block = sign(0);

        var event = write(block, admin(), "[Companies]", "emerald");

        assertEquals("", plain(event.line(0)));
        assertEquals(List.of("boards.error.unknown"), failures);
        assertTrue(signs.data().read(block).isEmpty());
    }

    @Test
    void otherSignsAreLeftAlone() {
        var block = sign(0);

        var event = write(block, server.addPlayer(), "Shop", "stone");

        assertEquals("Shop", plain(event.line(0)));
        assertTrue(failures.isEmpty());
    }

    @Test
    void boardIsStoredOnTheWaxedSign() {
        var block = sign(0);

        var event = write(block, admin(), " [COMPANIES] ", "Iron");

        assertEquals("[Companies]", plain(event.line(0)));
        assertEquals("Iron", plain(event.line(1)));
        var sign = (WaxableSign) block.getState();
        assertTrue(sign.isWaxed());
        assertEquals("iron", signs.data().read(block).orElseThrow());
        for (var side : Side.values()) {
            assertEquals("[Companies]", plain(sign.getSide(side).line(0)));
            assertEquals("Iron", plain(sign.getSide(side).line(1)));
            assertEquals("boards.sign.counting", plain(sign.getSide(side).line(2)));
        }
        assertEquals("iron", signs.index().spec(BoardLocation.of(block)).orElseThrow());
        assertTrue(failures.isEmpty());
    }

    @Test
    void signDataRoundTrip() {
        var block = sign(0);
        var sign = (Sign) block.getState();

        signs.data().write(sign, "stone dirt");

        assertEquals("stone dirt", signs.data().stored(sign).orElseThrow());
        assertEquals("stone dirt", signs.data().read(block).orElseThrow());
        assertEquals(
                boards.resolve("stone dirt").orElseThrow(),
                boards.resolve(signs.data().read(block).orElseThrow()).orElseThrow());
        assertTrue(signs.data().read(world.getBlockAt(5, 64, 5)).isEmpty());
    }

    @Test
    void rightClickOpensTheBoard() {
        var block = board(0, "stone");
        var player = server.addPlayer();
        var event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.NORTH, EquipmentSlot.HAND);

        signs.onInteract(event);

        assertEquals(1, opened.size());
        assertEquals(List.of("stone"), opened.getFirst().materials());
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
    }

    @Test
    void rightClickWithoutPermissionIsRefused() {
        var block = board(0, "stone");
        var player = server.addPlayer();
        player.addAttachment(plugin, Permission.Boards.USE, false);

        signs.onInteract(new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.NORTH, EquipmentSlot.HAND));

        assertTrue(opened.isEmpty());
        assertEquals(List.of("boards.error.permission"), failures);
    }

    @Test
    void playersCannotBreakBoards() {
        var block = board(0, "stone");
        var event = new BlockBreakEvent(block, server.addPlayer());

        protection.onBreak(event);

        assertTrue(event.isCancelled());
        assertEquals(List.of("boards.error.protected"), failures);
        assertEquals(1, signs.index().size());
    }

    @Test
    void adminsBreakBoards() {
        var block = board(0, "stone");
        var event = new BlockBreakEvent(block, admin());

        protection.onBreak(event);

        assertFalse(event.isCancelled());
        assertEquals(0, signs.index().size());
    }

    @Test
    void signEditorStaysClosed() {
        var block = board(0, "stone");
        var event = new PlayerOpenSignEvent(
                server.addPlayer(), (Sign) block.getState(), Side.FRONT, PlayerOpenSignEvent.Cause.INTERACT);

        protection.onOpenSign(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void explosionsAndFireSpareBoards() {
        var board = board(0, "stone");
        var plainSign = sign(1);
        var stone = world.getBlockAt(2, 64, 0);
        stone.setType(Material.STONE);
        var blocks = new ArrayList<>(List.of(board, plainSign, stone));
        var explosion = new BlockExplodeEvent(stone, stone.getState(), blocks, 1.0F, ExplosionResult.DESTROY);
        var burn = new BlockBurnEvent(board, null);

        protection.onBlockExplode(explosion);
        protection.onBurn(burn);

        assertEquals(List.of(plainSign, stone), explosion.blockList());
        assertTrue(burn.isCancelled());
    }

    @Test
    void indexFollowsChunks() {
        var block = sign(0);
        var other = sign(40);
        signs.data().write((Sign) block.getState(), "stone");
        signs.data().write((Sign) other.getState(), "dirt");
        var index = new SignBoardIndex(signs.data());

        index.load(List.of(
                block.getState(), other.getState(), world.getBlockAt(3, 64, 0).getState()));
        assertEquals(Set.of("stone", "dirt"), index.specs());

        index.unload(block.getChunk());
        assertTrue(index.spec(BoardLocation.of(block)).isEmpty());
        assertEquals("dirt", index.spec(BoardLocation.of(other)).orElseThrow());
    }

    @Test
    void refreshWritesTheCount() {
        var block = board(0, "stone");
        var target = boards.resolve("stone").orElseThrow();

        signs.refresher().apply(Map.of("stone", target), Map.of("stone", 7));

        var sign = (Sign) block.getState();
        assertEquals("7 open", plain(sign.getSide(Side.FRONT).line(2)));
        assertEquals("7 open", plain(sign.getSide(Side.BACK).line(2)));
    }

    @Test
    void refreshDropsSignsThatAreGone() {
        var block = board(0, "stone");
        block.setType(Material.STONE);
        var target = boards.resolve("stone").orElseThrow();

        signs.refresher().apply(Map.of("stone", target), Map.of("stone", 7));

        assertEquals(0, signs.index().size());
    }

    /**
     * A sign that can be waxed, which the mocked sign cannot.
     */
    private static final class WaxableSign extends SignStateMock {
        private boolean waxed;

        WaxableSign(Block block) {
            super(block);
        }

        @Override
        public boolean isWaxed() {
            return waxed;
        }

        @Override
        public void setWaxed(boolean waxed) {
            this.waxed = waxed;
        }
    }
}
