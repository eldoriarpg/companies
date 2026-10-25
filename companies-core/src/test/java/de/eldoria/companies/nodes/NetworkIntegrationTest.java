/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.eldoria.companies.commands.suggest.SuggestionCache;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.inbox.InboxStatus;
import de.eldoria.companies.data.wrapper.order.OrderContent;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.company.CompanyRenameEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.events.order.SoloOrderAcceptEvent;
import de.eldoria.companies.nodes.NodeMessage.InboxNew;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the node features end to end: two or three nodes in one JVM, each with its own settings, repositories,
 * notification service, inbox poller and Redis bus, sharing one PostgreSQL database and one Redis server.
 */
class NetworkIntegrationTest {
    private static final UUID PRIMARY = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SECONDARY = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID OTHER_NETWORK = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final String PASSWORD = "integration-only-password";
    private static final Duration DELIVERY = Duration.ofSeconds(5);
    private static final Duration WAIT = Duration.ofSeconds(20);

    private static GenericContainer<?> redis;
    private static PostgreSQLContainer postgres;
    private static ServerMock server;

    private final List<NetworkNode> nodes = new ArrayList<>();
    private final MovableClock clock = new MovableClock();

    @TempDir
    Path folder;

    @BeforeAll
    static void startInfrastructure() throws SQLException, IOException {
        server = MockBukkit.mock();
        redis = redisContainer();
        redis.start();
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        postgres.start();
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setVersionTable("companies_db_version")
                .withClassLoader(NetworkIntegrationTest.class.getClassLoader())
                .setReplacements(new QueryReplacement("companies_schema", "public"))
                .execute();
    }

    @AfterAll
    static void stopInfrastructure() {
        redis.stop();
        postgres.stop();
        MockBukkit.unmock();
    }

    @AfterEach
    void stopNodes() {
        nodes.forEach(NetworkNode::close);
    }

    private static GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--requirepass", PASSWORD);
    }

    private static RedisBus.Server redisServer(GenericContainer<?> container, String prefix) {
        return new RedisBus.Server(container.getHost(), container.getMappedPort(6379), "", PASSWORD, false, 0, prefix);
    }

    private NetworkNode node(String name, UUID uid, boolean primary, RedisBus.Server redisServer, int pollSeconds) {
        var node = NetworkNode.start(new NetworkNode.Setup(
                name,
                server,
                folder.resolve(name),
                uid,
                primary,
                NetworkNode.Dialect.POSTGRES,
                NetworkNode.Buses.redis(redisServer),
                pollSeconds,
                clock));
        nodes.add(node);
        return node;
    }

    private NetworkNode primary() {
        return node("A", PRIMARY, true, redisServer(redis, "companies"), 1);
    }

    private NetworkNode secondary() {
        return node("B", SECONDARY, false, redisServer(redis, "companies"), 1);
    }

    private static boolean contains(Path file, String text) {
        try {
            return Files.exists(file) && Files.readString(file).contains(text);
        } catch (IOException e) {
            return false;
        }
    }

    private static void await(BooleanSupplier condition, Duration limit, String what) throws InterruptedException {
        var deadline = System.nanoTime() + limit.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline)
                throw new AssertionError("Timed out after " + limit + " waiting for " + what);
            Thread.sleep(10);
        }
    }

    private static NotificationArgs args() {
        return NotificationArgs.create().text("order_name", "Wood").number("order_id", 4);
    }

    private static void assertDeliveredOnce(NetworkNode node, PlayerMock player) {
        var entries = node.inbox.retrievePage(player.getUniqueId(), 1, 10).join();
        assertEquals(1, entries.size());
        assertTrue(entries.getFirst().delivered());
        assertTrue(entries.getFirst().read());
    }

    @Test
    void aNotificationReachesThePlayerOnTheOtherNodeOnce() throws InterruptedException {
        var a = primary();
        var b = secondary();
        var bob = server.addPlayer();
        b.join(bob);

        a.notifications
                .send(bob.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                .join();

        await(() -> b.shownTo(bob) == 1, DELIVERY, "the notification on B");
        assertEquals(0, a.poller.poll(Times.now()));
        assertEquals(0, b.poller.poll(Times.now()));
        assertEquals(1, b.shownTo(bob));
        assertEquals(0, a.shownTo(bob));
        assertDeliveredOnce(b, bob);
    }

    @Test
    void aPlayerMovingBetweenNodesSeesTheNotificationOnce() throws InterruptedException {
        var a = primary();
        var b = secondary();
        var bob = server.addPlayer();
        b.join(bob);
        var entry = a.inbox
                .submit(bob.getUniqueId(), NotificationType.ORDER_DELIVERED, args(), Times.now(), InboxStatus.PENDING)
                .join()
                .orElseThrow();
        b.quit(bob);
        a.join(bob);

        a.bus.publish(new InboxNew(entry, bob.getUniqueId()));
        var polls = List.of(
                CompletableFuture.supplyAsync(() -> a.poller.poll(Times.now())),
                CompletableFuture.supplyAsync(() -> b.poller.poll(Times.now())));
        polls.forEach(CompletableFuture::join);

        await(() -> a.shownTo(bob) == 1, DELIVERY, "the notification on A");
        assertEquals(0, a.poller.poll(Times.now()));
        assertEquals(1, a.shownTo(bob));
        assertEquals(0, b.shownTo(bob));
        assertDeliveredOnce(a, bob);
    }

    @Test
    void withoutRedisTheSafetyPollDeliversAndTheReconnectDropsTheCachesOnce() throws InterruptedException {
        var outage = redisContainer();
        outage.start();
        try {
            var a = node("A", PRIMARY, true, redisServer(outage, "outage"), 1);
            var b = node("B", SECONDARY, false, redisServer(outage, "outage"), 1);
            var bob = server.addPlayer();
            b.join(bob);
            var docker = DockerClientFactory.instance().client();

            docker.pauseContainerCmd(outage.getContainerId()).exec();
            await(() -> !a.bus.realtime() && !b.bus.realtime(), WAIT, "both nodes to notice the outage");
            a.notifications
                    .send(bob.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                    .join();

            await(() -> b.shownTo(bob) == 1, WAIT, "the notification through the poll");
            assertEquals(0, a.shownTo(bob));
            assertEquals(0, a.cacheDrops.get());
            assertEquals(0, b.cacheDrops.get());

            docker.unpauseContainerCmd(outage.getContainerId()).exec();
            await(() -> a.bus.realtime() && b.bus.realtime(), WAIT, "both nodes to reconnect");
            await(() -> a.cacheDrops.get() == 1 && b.cacheDrops.get() == 1, WAIT, "the caches to drop");
            assertEquals(1, b.shownTo(bob));
            assertDeliveredOnce(b, bob);

            var carol = server.addPlayer();
            b.join(carol);
            a.notifications
                    .send(carol.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                    .join();
            await(() -> b.shownTo(carol) == 1, DELIVERY, "a notification after the reconnect");
            assertEquals(1, a.cacheDrops.get());
            assertEquals(1, b.cacheDrops.get());
        } finally {
            outage.stop();
        }
    }

    @Test
    void anOrderChangedOnOneNodeIsReadFreshOnTheOther() throws InterruptedException {
        var a = primary();
        var b = secondary();
        var owner = SqLiteTestDatabase.player(UUID.randomUUID());
        var contents = List.of(new OrderContent(0, new ItemStack(Material.STONE), false, 10, 100.0));
        a.orders
                .submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "stone", 0.0).toFullOrder(contents))
                .join();
        var order = a.orders
                .retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .join()
                .getFirst();
        assertEquals(
                OrderState.UNCLAIMED, b.orders.retrieveFullOrder(order).join().state());
        var loads = new AtomicInteger();
        var suggestions = new SuggestionCache<String, Integer>(Duration.ofMinutes(5), Runnable::run, key -> {
            loads.incrementAndGet();
            return 1;
        });
        suggestions.load("orders").join();

        assertTrue(a.orders
                .submitOrderStateUpdate(order, OrderState.UNCLAIMED, OrderState.CLAIMED)
                .join());

        await(
                () -> b.orders
                                .retrieveFullOrder(b.orders
                                        .retrieveOrderById(order.id())
                                        .join()
                                        .orElseThrow())
                                .join()
                                .state()
                        == OrderState.CLAIMED,
                DELIVERY,
                "B to read the claimed order");
        await(() -> suggestions.cached("orders").isEmpty(), DELIVERY, "the suggestions to drop");
    }

    @Test
    void aSoloClaimOnOneNodeIsReadFreshOnTheOther() throws InterruptedException {
        var a = primary();
        var b = secondary();
        List<String> dropped = new CopyOnWriteArrayList<>();
        b.caches.register(NodeCaches.PLAYER, key -> dropped.add("player:" + key.orElse("*")));
        b.caches.register(NodeCaches.BOARDS, key -> dropped.add("boards"));
        var owner = SqLiteTestDatabase.player(UUID.randomUUID());
        var worker = server.addPlayer();
        var contents = List.of(new OrderContent(0, new ItemStack(Material.OAK_LOG), false, 16, 32.0));
        a.orders
                .submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "logs", 0.0).toFullOrder(contents))
                .join();
        var order = a.orders
                .retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .join()
                .getFirst();
        assertEquals(
                OrderState.UNCLAIMED, b.orders.retrieveFullOrder(order).join().state());

        assertTrue(a.orders.submitSoloClaim(worker.getUniqueId(), order, 3).join());
        a.connection.signals().onSoloOrderAccept(new SoloOrderAcceptEvent(order, worker));

        await(
                () -> b.orders
                                .retrieveFullOrder(b.orders
                                        .retrieveOrderById(order.id())
                                        .join()
                                        .orElseThrow())
                                .join()
                                .state()
                        == OrderState.CLAIMED,
                DELIVERY,
                "B to read the solo claim");
        await(
                () -> dropped.containsAll(
                        List.of("player:" + owner.getUniqueId(), "player:" + worker.getUniqueId(), "boards")),
                DELIVERY,
                "the player and board caches of B to drop");
        assertEquals(
                List.of(order.id()),
                b
                        .orders
                        .retrieveOrdersBySoloWorker(worker.getUniqueId(), OrderState.CLAIMED, OrderState.CLAIMED)
                        .join()
                        .stream()
                        .map(SimpleOrder::id)
                        .toList());
    }

    @Test
    void refreshedPricesAreReadFreshOnTheOtherNode() throws InterruptedException {
        var a = primary();
        var b = secondary();
        var owner = SqLiteTestDatabase.player(UUID.randomUUID());
        var contents = List.of(new OrderContent(0, new ItemStack(Material.GOLD_BLOCK), false, 4, 400.0));
        a.orders
                .submitOrder(owner, new SimpleOrder(owner.getUniqueId(), "gold", 0.0).toFullOrder(contents))
                .join();
        var order = a.orders
                .retrieveOrdersByPlayer(owner, OrderState.UNCLAIMED, OrderState.UNCLAIMED)
                .join()
                .getFirst();
        assertEquals(0.0, b.orders.retrieveMaterialPrice("gold_block").join().avgPrice());

        a.orders
                .submitOrderStateUpdate(order, OrderState.UNCLAIMED, OrderState.DELIVERED)
                .join();
        a.orders.refreshMaterialPrices();
        a.orders.invalidateMaterialPriceCache();
        a.connection.pricesRefreshed();

        await(() -> b.orders.getMaterialPrice("gold_block").avgPrice() == 100.0, DELIVERY, "B to read the new price");
    }

    @Test
    void companyRankingAndBoardChangesReachTheCachesOfTheOtherNode() throws InterruptedException {
        var a = primary();
        var b = secondary();
        List<String> dropped = new CopyOnWriteArrayList<>();
        b.caches.register(NodeCaches.COMPANY, key -> dropped.add("company:" + key.orElse("*")));
        b.caches.register(NodeCaches.RANKING, key -> dropped.add("ranking"));
        b.caches.register(NodeCaches.BOARDS, key -> dropped.add("boards"));
        var company = new CompanyProfile(77, "Renamed", Times.now(), 2, CompanyDetails.NONE, List.of());

        a.connection.signals().onCompanyRename(new CompanyRenameEvent(company, "Old", null, false));
        a.connection
                .signals()
                .onOrderRemoved(new OrderRemovedEvent(
                        new SimpleOrder(UUID.randomUUID(), "Wood", 0), OrderRemovedEvent.Reason.PURGED));

        await(
                () -> dropped.containsAll(List.of("company:77", "ranking", "boards")),
                DELIVERY,
                "the caches of B to drop");
    }

    @Test
    void aSyncOnThePrimaryReachesTheSecondary() throws Exception {
        var a = primary();
        var b = secondary();
        assertEquals(30, b.configuration.notificationSettings().retentionDays());
        var file = folder.resolve("A").resolve("config.yml");
        a.configuration.save(Configuration.CONFIG_YML);
        var content = Files.readString(file);
        assertTrue(content.contains("retentionDays: 30"), content);
        Files.writeString(file, content.replace("retentionDays: 30", "retentionDays: 45"));
        a.configuration.reload();

        a.configuration.syncConfigurations(a.nodeData).join();

        await(
                () -> b.configuration.notificationSettings().retentionDays() == 45 && b.reloads.get() > 0,
                WAIT,
                "the secondary to load and apply the configuration");
        assertTrue(Files.readString(folder.resolve("B").resolve("config.yml")).contains("retentionDays: 45"));
        assertEquals(0, a.reloads.get());
    }

    @Test
    void aSecondaryKeepsItsOwnEditAsBackupAndAnUnchangedSyncChangesNothing() throws Exception {
        var a = primary();
        var b = secondary();
        assertFalse(b.configuration.syncConfigurations(b.nodeData).join());

        var file = folder.resolve("B").resolve("user.yml");
        Files.writeString(file, Files.readString(file) + "edited: here\n");
        assertTrue(b.configuration.syncConfigurations(b.nodeData).join());
        try (var files = Files.list(folder.resolve("B"))) {
            var backups = files.filter(path -> path.getFileName().toString().matches("backup_.*_user\\.yml"))
                    .toList();
            assertEquals(1, backups.size());
            assertTrue(Files.readString(backups.getFirst()).contains("edited: here"));
        }
        assertFalse(Files.readString(file).contains("edited: here"));
        assertFalse(b.configuration.syncConfigurations(b.nodeData).join());
        assertEquals(0, a.reloads.get());
    }

    @Test
    void aPrimaryStartingWithABrokenFileDoesNotShareItsDefaults() throws Exception {
        var a = primary();
        a.configuration.companySettings().level(1).orElseThrow().levelName("CustomOne");
        a.configuration.save(Configuration.COMPANY_SETTINGS);
        a.configuration.syncConfigurations(a.nodeData).join();
        assertTrue(a.nodeData
                .primaryContent(Configuration.COMPANY_SETTINGS)
                .orElseThrow()
                .contains("CustomOne"));

        Files.writeString(folder.resolve("A").resolve("company.yml"), "level: [ {levelName: Broken");
        var restarted = primary();

        assertTrue(restarted.configuration.unreadable(Configuration.COMPANY_SETTINGS));
        assertTrue(restarted
                .nodeData
                .primaryContent(Configuration.COMPANY_SETTINGS)
                .orElseThrow()
                .contains("CustomOne"));
        assertTrue(Files.readString(folder.resolve("A").resolve("company.yml")).contains("Broken"));
    }

    @Test
    void aSecondaryKeepsNoBackupWhenOnlyThePrimaryChanged() throws Exception {
        var a = primary();
        var b = secondary();
        a.configuration.companySettings().level(1).orElseThrow().levelName("Renamed");
        a.configuration.save(Configuration.COMPANY_SETTINGS);
        a.configuration.syncConfigurations(a.nodeData).join();

        await(
                () -> "Renamed"
                        .equals(b.configuration
                                .companySettings()
                                .level(1)
                                .orElseThrow()
                                .levelName()),
                WAIT,
                "the secondary to take the new revision");
        try (var files = Files.list(folder.resolve("B"))) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("backup_")));
        }
    }

    @Test
    void aSecondaryFindsAnUnannouncedRevisionByPollingAndIgnoresOlderOnes() throws Exception {
        var a = primary();
        var b = secondary();
        a.configuration.companySettings().level(1).orElseThrow().levelName("Polled");
        var copy = a.nodeData.primaryCopy(Configuration.COMPANY_SETTINGS, a.configuration);
        var revision = a.nodeData.publishSettings("company.yml", copy, "Ada").orElseThrow();
        var reloads = new AtomicInteger();

        new SettingsSync(
                        b.configuration,
                        b.nodeData,
                        Runnable::run,
                        Runnable::run,
                        reloads::incrementAndGet,
                        () -> false,
                        Logger.getAnonymousLogger())
                .poll();

        assertEquals(
                "Polled",
                b.configuration.companySettings().level(1).orElseThrow().levelName());
        assertEquals(revision, b.configuration.appliedRevisions().get("company.yml"));
        assertEquals(0, reloads.get());
        assertFalse(b.configuration.takeIfNewer(b.nodeData, "company.yml", revision - 1));
    }

    @Test
    void anEditOnASecondaryBecomesTheNextRevisionEverywhere() throws Exception {
        var a = primary();
        var b = secondary();
        var file = folder.resolve("B").resolve("user.yml");
        Files.writeString(file, Files.readString(file).replaceFirst("maxOrders: \\d+", "maxOrders: 42"));
        b.configuration.reload();

        var edits = b.configuration.edits(b.nodeData);
        assertEquals(1, edits.size());
        assertEquals(List.of("maxOrders"), edits.getFirst().changes());
        var outcome = b.configuration.publishEdits(b.nodeData, edits, "Ada").getFirst();

        assertTrue(outcome.revision().isPresent());
        await(() -> a.configuration.userSettings().maxOrders() == 42, WAIT, "the primary to take the edit");
        assertTrue(Files.readString(folder.resolve("A").resolve("user.yml")).contains("maxOrders: 42"));
        assertTrue(b.configuration.edits(b.nodeData).isEmpty());
    }

    @Test
    void anEditOnTopOfAnOutdatedRevisionIsKeptAsConflictAndTheNewerOneIsUsed() throws Exception {
        var a = primary();
        var b = secondary();
        var fileB = folder.resolve("B").resolve("user.yml");
        Files.writeString(fileB, Files.readString(fileB).replaceFirst("maxOrders: \\d+", "maxOrders: 8"));
        b.configuration.reload();
        var edits = b.configuration.edits(b.nodeData);

        a.configuration.userSettings().maxOrders(9);
        a.configuration.save(Configuration.USER_SETTINGS);
        a.configuration.syncConfigurations(a.nodeData).join();
        var outcome = b.configuration.publishEdits(b.nodeData, edits, "Bo").getFirst();

        assertTrue(outcome.revision().isEmpty());
        assertTrue(outcome.conflictFile().startsWith("conflict_"));
        assertTrue(Files.readString(folder.resolve("B").resolve(outcome.conflictFile()))
                .contains("maxOrders: 8"));
        assertEquals(9, b.configuration.userSettings().maxOrders());
    }

    @Test
    void changedLevelsReachTheSecondaryWithoutAReload() throws Exception {
        var a = primary();
        var b = secondary();
        a.configuration.companySettings().level(1).orElseThrow().levelName("Workshop");
        var copy = a.nodeData.primaryCopy(Configuration.COMPANY_SETTINGS, a.configuration);
        a.configuration.companySettings().level(1).orElseThrow().levelName("Later");

        a.configuration.publish(a.nodeData, Configuration.COMPANY_SETTINGS, copy, "Ada");

        var file = folder.resolve("B").resolve("company.yml");
        await(
                () -> "Workshop"
                                .equals(b.configuration
                                        .companySettings()
                                        .level(1)
                                        .orElseThrow()
                                        .levelName())
                        && contains(file, "Workshop"),
                WAIT,
                "the secondary to take and save the levels");
        assertEquals(0, b.reloads.get());
    }

    @Test
    void presenceFollowsJoinsQuitsAndSilentNodes() throws InterruptedException {
        var a = primary();
        var b = secondary();
        var alice = server.addPlayer();

        a.connection.signals().onJoin(new PlayerJoinEvent(alice, Component.empty()));
        await(() -> b.presence.nodeOf(alice.getUniqueId()).equals(Optional.of(PRIMARY)), DELIVERY, "the join");

        a.connection
                .signals()
                .onQuit(new PlayerQuitEvent(alice, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        await(() -> b.presence.nodeOf(alice.getUniqueId()).isEmpty(), DELIVERY, "the quit");

        a.connection.signals().onJoin(new PlayerJoinEvent(alice, Component.empty()));
        await(() -> b.presence.nodeOf(alice.getUniqueId()).isPresent(), DELIVERY, "the second join");
        a.crash();
        clock.advance(NetworkPresence.TIMEOUT.plusSeconds(1));

        assertFalse(b.presence.alive(PRIMARY));
        assertEquals(Optional.empty(), b.presence.nodeOf(alice.getUniqueId()));
    }

    @Test
    void networksWithDifferentPrefixesDoNotHearEachOther() throws InterruptedException {
        var a = primary();
        var b = secondary();
        var c = node("C", OTHER_NETWORK, false, redisServer(redis, "other"), 1);
        var bob = server.addPlayer();
        var carol = server.addPlayer();
        c.join(bob);
        b.join(carol);

        a.notifications
                .send(bob.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                .join();
        a.notifications
                .send(carol.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                .join();

        await(() -> b.shownTo(carol) == 1, DELIVERY, "the notification in the own network");
        assertEquals(0, c.shownTo(bob));
        assertEquals(
                1,
                a.inbox.retrievePending(List.of(bob.getUniqueId()), 10).join().size());
        assertFalse(c.presence.alive(PRIMARY));
        assertFalse(b.presence.alive(OTHER_NETWORK));
    }

    private static final class MovableClock extends Clock {
        private volatile Instant now = Instant.now();

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
