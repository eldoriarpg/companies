/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.company.CompanyRenameEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs PostgreSQL notify end to end: two nodes in one JVM, each with its own settings, repositories, notification
 * service, inbox poller and notify bus, sharing one PostgreSQL database and no Redis. The nodes listen as their own
 * database role, so a test can lock that role out to keep the listening connections away while the pooled
 * connections still work.
 */
class PostgresNotifyIntegrationTest {
    private static final UUID PRIMARY = UUID.fromString("00000000-0000-0000-0000-00000000002a");
    private static final UUID SECONDARY = UUID.fromString("00000000-0000-0000-0000-00000000002b");
    private static final UUID OTHER_NETWORK = UUID.fromString("00000000-0000-0000-0000-00000000002c");
    private static final String LISTENER = "companies_listener";
    private static final String LISTENER_PASSWORD = "integration-only-password";
    private static final int LONG_POLL_SECONDS = 600;
    private static final Duration DELIVERY = Duration.ofMillis(2500);
    private static final Duration WAIT = Duration.ofSeconds(20);

    private static PostgreSQLContainer postgres;
    private static PGSimpleDataSource dataSource;
    private static ServerMock server;

    private final List<NetworkNode> nodes = new ArrayList<>();
    private final List<String> logs = new CopyOnWriteArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) logs.add(record.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @TempDir
    Path folder;

    private Path root;

    @BeforeAll
    static void startInfrastructure() throws SQLException, IOException {
        server = MockBukkit.mock();
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        postgres.start();
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource).build());
        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setVersionTable("companies_db_version")
                .withClassLoader(PostgresNotifyIntegrationTest.class.getClassLoader())
                .setReplacements(new QueryReplacement("companies_schema", "public"))
                .execute();
        execute("CREATE ROLE " + LISTENER + " LOGIN PASSWORD '" + LISTENER_PASSWORD + "'");
    }

    @AfterAll
    static void stopInfrastructure() {
        postgres.stop();
        MockBukkit.unmock();
    }

    @BeforeEach
    void prepare() {
        root = folder.resolve(UUID.randomUUID().toString());
        Logger.getLogger("node-A").addHandler(capture);
    }

    @AfterEach
    void stopNodes() throws SQLException {
        Logger.getLogger("node-A").removeHandler(capture);
        nodes.forEach(NetworkNode::close);
        execute("ALTER ROLE " + LISTENER + " LOGIN");
    }

    private static void execute(String statement) throws SQLException {
        try (var connection = dataSource.getConnection();
                var prepared = connection.createStatement()) {
            prepared.execute(statement);
        }
    }

    private static PostgresNotifyBus.Server notifyServer(String channel) {
        return new PostgresNotifyBus.Server(
                postgres.getHost(),
                postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                postgres.getDatabaseName(),
                LISTENER,
                LISTENER_PASSWORD,
                channel);
    }

    private NetworkNode node(String name, UUID uid, boolean primary, String channel, int pollSeconds) {
        var node = NetworkNode.start(new NetworkNode.Setup(
                name,
                server,
                root.resolve(name),
                uid,
                primary,
                NetworkNode.Dialect.POSTGRES,
                NetworkNode.Buses.postgresNotify(notifyServer(channel)),
                pollSeconds,
                Clock.systemUTC()));
        nodes.add(node);
        return node;
    }

    private NetworkNode primary() {
        return node("A", PRIMARY, true, "companies", LONG_POLL_SECONDS);
    }

    private NetworkNode secondary() {
        return node("B", SECONDARY, false, "companies", LONG_POLL_SECONDS);
    }

    private static void await(BooleanSupplier condition, Duration limit, String what) throws InterruptedException {
        var deadline = System.nanoTime() + limit.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline)
                throw new AssertionError("Timed out after " + limit + " waiting for " + what);
            Thread.sleep(10);
        }
    }

    private static void awaitListening(NetworkNode... nodes) throws InterruptedException {
        for (var node : nodes) await(node.bus::realtime, WAIT, "node " + node.name + " to listen");
    }

    private static NotificationArgs args() {
        return NotificationArgs.create().text("order_name", "Wood").number("order_id", 4);
    }

    @Test
    void theBusIsListeningWhenItsStartReturns() {
        var a = primary();

        assertTrue(a.bus.realtime());
        assertEquals(NodeBus.Transport.POSTGRES_NOTIFY, a.bus.transport());
    }

    @Test
    void aNotificationReachesThePlayerOnTheOtherNodeWithinTwoSeconds() throws InterruptedException {
        var a = primary();
        var b = secondary();
        awaitListening(a, b);
        var bob = server.addPlayer();
        b.join(bob);

        a.notifications
                .send(bob.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                .join();

        await(() -> b.shownTo(bob) == 1, DELIVERY, "the notification on B");
        assertEquals(0, a.shownTo(bob));
        var entries = b.inbox.retrievePage(bob.getUniqueId(), 1, 10).join();
        assertEquals(1, entries.size());
        assertTrue(entries.getFirst().delivered());
    }

    @Test
    void companyRankingAndBoardChangesReachTheCachesOfTheOtherNode() throws InterruptedException {
        var a = primary();
        var b = secondary();
        awaitListening(a, b);
        List<String> dropped = new CopyOnWriteArrayList<>();
        b.caches.register(NodeCaches.COMPANY, key -> dropped.add("company:" + key.orElse("*")));
        b.caches.register(NodeCaches.RANKING, key -> dropped.add("ranking"));
        b.caches.register(NodeCaches.BOARDS, key -> dropped.add("boards"));
        var company = new CompanyProfile(77, "Renamed", LocalDateTime.now(), 2, CompanyDetails.NONE, List.of());

        a.connection.signals().onCompanyRename(new CompanyRenameEvent(company, "Old", null, false));
        a.connection
                .signals()
                .onOrderRemoved(new OrderRemovedEvent(
                        new SimpleOrder(UUID.randomUUID(), "Wood", 0), OrderRemovedEvent.Reason.PURGED));

        await(() -> dropped.containsAll(List.of("company:77", "ranking", "boards")), WAIT, "the caches of B to drop");
    }

    @Test
    void theSenderReceivesItsOwnMessages() throws InterruptedException {
        var a = primary();
        awaitListening(a);

        a.bus.publish(new CacheInvalidate("probe", "own"));

        await(() -> a.probes.contains("own"), WAIT, "the own message");
    }

    @Test
    void aSyncOnThePrimaryReachesTheSecondary() throws Exception {
        var a = primary();
        var b = secondary();
        awaitListening(a, b);
        var file = root.resolve("A").resolve("config.yml");
        a.configuration.save(Configuration.CONFIG_YML);
        var content = Files.readString(file);
        var before = b.configuration.notificationSettings().retentionDays();
        var changed = before + 15;
        Files.writeString(file, content.replace("retentionDays: " + before, "retentionDays: " + changed));
        a.configuration.reload();

        a.configuration.syncConfigurations(a.nodeData).join();
        a.bus.publish(new NodeMessage.ConfigSynced());

        await(
                () -> b.configuration.notificationSettings().retentionDays() == changed && b.reloads.get() > 0,
                WAIT,
                "the secondary to load and apply the configuration");
        assertEquals(0, a.reloads.get());
    }

    @Test
    void presenceAndTheBusOfANodeTravelAsNotifications() throws InterruptedException {
        var a = primary();
        var b = secondary();
        awaitListening(a, b);
        var alice = server.addPlayer();

        a.connection.signals().onJoin(new PlayerJoinEvent(alice, Component.empty()));
        await(() -> b.presence.nodeOf(alice.getUniqueId()).equals(Optional.of(PRIMARY)), WAIT, "the join");

        a.beat();
        await(
                () -> b.presence.transport(PRIMARY).equals(Optional.of(NodeBus.Transport.POSTGRES_NOTIFY)),
                WAIT,
                "the heartbeat naming the bus");

        a.connection
                .signals()
                .onQuit(new PlayerQuitEvent(alice, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        await(() -> b.presence.nodeOf(alice.getUniqueId()).isEmpty(), WAIT, "the quit");
    }

    @Test
    void aMessageTooLongForNotifyIsNotSentAndReportedOnce() throws InterruptedException {
        var a = primary();
        var b = secondary();
        awaitListening(a, b);
        var huge = "x".repeat(PostgresNotifyBus.MAX_PAYLOAD_BYTES);

        a.bus.publish(new CacheInvalidate("probe", huge));
        a.bus.publish(new CacheInvalidate("probe", huge + "y"));
        a.bus.publish(new CacheInvalidate("probe", "small"));

        await(() -> b.probes.contains("small"), WAIT, "the small message after the long ones");
        assertEquals(List.of("small"), b.probes);
        var refused =
                logs.stream().filter(log -> log.contains("cache.invalidate")).toList();
        assertEquals(1, refused.size(), logs.toString());
        assertTrue(
                refused.getFirst().contains(String.valueOf(PostgresNotifyBus.MAX_PAYLOAD_BYTES)), refused.getFirst());
    }

    @Test
    void networksWithDifferentChannelsDoNotHearEachOther() throws InterruptedException {
        var a = primary();
        var b = secondary();
        var c = node("C", OTHER_NETWORK, false, "other", LONG_POLL_SECONDS);
        awaitListening(a, b, c);

        a.bus.publish(new CacheInvalidate("probe", "companies"));
        c.bus.publish(new CacheInvalidate("probe", "other"));

        await(() -> b.probes.contains("companies") && c.probes.contains("other"), WAIT, "both messages");
        assertEquals(List.of("companies"), b.probes);
        assertEquals(List.of("other"), c.probes);
    }

    @Test
    void withoutTheListenerTheSafetyPollDeliversAndTheReconnectDropsTheCachesOnce() throws Exception {
        var a = node("A", PRIMARY, true, "outage", 1);
        var b = node("B", SECONDARY, false, "outage", 1);
        awaitListening(a, b);
        var bob = server.addPlayer();
        b.join(bob);

        execute("ALTER ROLE " + LISTENER + " NOLOGIN");
        execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = '" + LISTENER + "'");
        await(() -> !a.bus.realtime() && !b.bus.realtime(), WAIT, "both nodes to notice the lost connection");
        a.notifications
                .send(bob.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                .join();

        await(() -> b.shownTo(bob) == 1, WAIT, "the notification through the poll");
        assertEquals(0, a.shownTo(bob));
        assertEquals(0, a.cacheDrops.get());
        assertEquals(0, b.cacheDrops.get());

        execute("ALTER ROLE " + LISTENER + " LOGIN");
        await(() -> a.bus.realtime() && b.bus.realtime(), WAIT, "both nodes to listen again");
        await(() -> a.cacheDrops.get() == 1 && b.cacheDrops.get() == 1, WAIT, "the caches to drop");
        assertEquals(1, b.shownTo(bob));

        var carol = server.addPlayer();
        b.join(carol);
        a.notifications
                .send(carol.getUniqueId(), NotificationType.ORDER_DELIVERED, args())
                .join();
        await(() -> b.shownTo(carol) == 1, WAIT, "a notification after the reconnect");
        assertEquals(1, a.cacheDrops.get());
        assertEquals(1, b.cacheDrops.get());
    }
}
