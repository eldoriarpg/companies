/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.configuration.elements.areas.AreaSettings;
import de.eldoria.companies.data.repository.AHomeData;
import de.eldoria.companies.data.wrapper.area.CompanyHome;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teleports home: at once or after a warm-up that moving cancels, the cooldown after, the cost from the player, a home
 * on another server reached through the proxy, and the teleport of a player who arrives there.
 */
class HomeTeleportsTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER_NODE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final CompanyProfile COMPANY =
            new CompanyProfile(7, "Builders", LocalDateTime.of(2026, 1, 1, 0, 0), 1, CompanyDetails.NONE, List.of());

    private final AreaSettings settings = new AreaSettings();
    private final Homes homes = new Homes();
    private final List<HomeTeleports.Notice> told = new ArrayList<>();
    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private HomeTeleports teleports;
    private boolean pays = true;
    private boolean member = true;
    private final List<Double> refunds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        Plugin plugin = MockBukkit.createMockPlugin();
        server.getMessenger().registerOutgoingPluginChannel(plugin, HomeTeleports.CHANNEL);
        player = server.addPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        teleports = new HomeTeleports(
                plugin,
                homes,
                () -> settings,
                NODE,
                (player, company) -> Optional.ofNullable(member ? homes.home : null),
                new HomeTeleports.Payments() {
                    @Override
                    public boolean charge(Player player, double amount) {
                        return pays;
                    }

                    @Override
                    public void refundPlayer(Player player, double amount) {
                        refunds.add(amount);
                    }

                    @Override
                    public CompletableFuture<Boolean> chargeTreasury(
                            CompanyProfile company, Player player, double amount) {
                        return CompletableFuture.completedFuture(pays);
                    }

                    @Override
                    public void refundTreasury(CompanyProfile company, Player player, double amount) {}
                },
                (player, notice, detail) -> told.add(notice),
                Runnable::run,
                Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC),
                Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void withoutWarmupThePlayerIsTeleportedAtOnceAndWaitsForTheCooldown() {
        settings.homeWarmupSeconds(0);
        homes.home = home(NODE, "");

        assertEquals(new HomeTeleports.Start.Started(0), teleports.start(player, COMPANY, home(NODE, "")));
        server.getScheduler().performTicks(2);

        assertEquals(100, player.getLocation().getBlockX());
        var again =
                assertInstanceOf(HomeTeleports.Start.Refused.class, teleports.start(player, COMPANY, home(NODE, "")));
        assertEquals(HomeTeleports.Refusal.COOLDOWN, again.refusal());
    }

    @Test
    void theWarmupEndsInATeleportUnlessThePlayerMoves() {
        homes.home = home(NODE, "");
        settings.homeWarmupSeconds(3);
        settings.homeCooldownSeconds(0);
        teleports.start(player, COMPANY, home(NODE, ""));
        server.getScheduler().performTicks(61);
        assertEquals(100, player.getLocation().getBlockX());

        player.teleport(new Location(world, 0, 64, 0));
        teleports.start(player, COMPANY, home(NODE, ""));
        teleports.onMove(new PlayerMoveEvent(player, player.getLocation(), new Location(world, 3, 64, 0)));
        server.getScheduler().performTicks(61);

        assertEquals(0, player.getLocation().getBlockX());
        assertTrue(told.contains(HomeTeleports.Notice.MOVED));
    }

    @Test
    void aPlayerWhoCanNotPayStaysAndHasNoCooldown() {
        homes.home = home(NODE, "");
        settings.homeWarmupSeconds(0);
        settings.homeCost(5);
        pays = false;

        teleports.start(player, COMPANY, home(NODE, ""));
        server.getScheduler().performTicks(2);

        assertEquals(0, player.getLocation().getBlockX());
        assertEquals(List.of(HomeTeleports.Notice.FUNDS), told);
        pays = true;
        assertEquals(new HomeTeleports.Start.Started(0), teleports.start(player, COMPANY, home(NODE, "")));
    }

    @Test
    void aHomeOnAnotherServerNeedsItsProxyName() {
        settings.homeWarmupSeconds(0);

        var refused = assertInstanceOf(
                HomeTeleports.Start.Refused.class, teleports.start(player, COMPANY, home(OTHER_NODE, "")));

        assertEquals(HomeTeleports.Refusal.UNREACHABLE, refused.refusal());
    }

    @Test
    void aPlayerArrivingOnTheHomesServerIsTeleportedThere() {
        homes.home = home(NODE, "survival");
        homes.travelling(player.getUniqueId(), 7, NODE, LocalDateTime.of(2026, 10, 31, 12, 1));

        teleports.onJoin(new PlayerJoinEvent(player, (Component) null));
        server.getScheduler().performTicks(6);

        assertEquals(100, player.getLocation().getBlockX());
        assertEquals(List.of(HomeTeleports.Notice.ARRIVED), told);
    }

    @Test
    void aSecondCommandWhileTheFirstRunsIsRefusedSoNothingIsPaidTwice() {
        settings.homeWarmupSeconds(3);
        homes.home = home(NODE, "");

        teleports.start(player, COMPANY, home(NODE, ""));
        var second =
                assertInstanceOf(HomeTeleports.Start.Refused.class, teleports.start(player, COMPANY, home(NODE, "")));

        assertEquals(HomeTeleports.Refusal.WAITING, second.refusal());
    }

    @Test
    void aMemberWhoLeftDuringTheWarmupIsNotTeleported() {
        settings.homeWarmupSeconds(3);
        homes.home = home(NODE, "");
        teleports.start(player, COMPANY, home(NODE, ""));
        member = false;

        server.getScheduler().performTicks(62);

        assertEquals(0, player.getLocation().getBlockX());
        assertTrue(told.contains(HomeTeleports.Notice.GONE));
    }

    @Test
    void aFailedNoteOfATeleportToAnotherServerGivesTheCostBack() {
        settings.homeWarmupSeconds(0);
        settings.homeCost(5);
        homes.home = home(OTHER_NODE, "lobby");
        homes.notes = false;

        teleports.start(player, COMPANY, home(OTHER_NODE, "lobby"));
        server.getScheduler().performTicks(3);

        assertEquals(List.of(5.0), refunds);
        assertTrue(told.contains(HomeTeleports.Notice.FAILED));
        assertEquals(new HomeTeleports.Start.Started(0), teleports.start(player, COMPANY, home(OTHER_NODE, "lobby")));
    }

    private static CompanyHome home(UUID node, String server) {
        return new CompanyHome(
                7, node, server, "world", 100.5, 70, 100.5, 0f, 0f, UUID.randomUUID(), LocalDateTime.now());
    }

    /**
     * Homes in memory.
     */
    private static final class Homes extends AHomeData {
        private CompanyHome home;
        private boolean notes = true;
        private final Map<UUID, Integer> travelling = new HashMap<>();

        @Override
        public Optional<CompanyHome> home(int company) {
            return Optional.ofNullable(home);
        }

        @Override
        public boolean set(CompanyHome home) {
            this.home = home;
            return true;
        }

        @Override
        public boolean remove(int company) {
            return home != null;
        }

        @Override
        public boolean travelling(UUID player, int company, UUID node, LocalDateTime expires) {
            if (!notes) return false;
            travelling.put(player, company);
            return true;
        }

        @Override
        public void server(UUID node, String server) {}

        @Override
        public OptionalInt arrived(UUID player, UUID node, LocalDateTime now) {
            var company = travelling.remove(player);
            return company == null ? OptionalInt.empty() : OptionalInt.of(company);
        }
    }
}
