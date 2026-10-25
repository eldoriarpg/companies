/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ANodeMessageData;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.company.CompanyRenameEvent;
import de.eldoria.companies.events.order.OrderRemovedEvent;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.services.notifications.NotificationArgs;
import de.eldoria.companies.services.notifications.NotificationType;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs low latency mode end to end: two nodes in one JVM, each with its own settings, repositories, notification
 * service, inbox poller and message table bus, sharing one database. The subclasses choose the database. The inbox
 * poll interval is long, so a notification arriving within two seconds came through the message table.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class LowLatencyIntegrationTest {
    private static final UUID PRIMARY = UUID.fromString("00000000-0000-0000-0000-00000000001a");
    private static final UUID SECONDARY = UUID.fromString("00000000-0000-0000-0000-00000000001b");
    private static final int POLL_SECONDS = 600;
    private static final Duration DELIVERY = Duration.ofMillis(2500);
    private static final Duration ARRIVAL = Duration.ofSeconds(10);
    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final MessageTableBus.Timings TIMINGS =
            new MessageTableBus.Timings(Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMillis(200));

    private ServerMock server;
    private final List<NetworkNode> nodes = new ArrayList<>();
    private final NodeMessageCodec codec = new NodeMessageCodec();

    @TempDir
    Path folder;

    private Path root;

    /**
     * Starts the database container and returns a migrated data source on it.
     *
     * @return the data source
     * @throws Exception when the database can not be started or migrated
     */
    abstract DataSource startDatabase() throws Exception;

    /**
     * Stops the database container.
     */
    abstract void stopDatabase();

    /**
     * @return the repositories for the database
     */
    abstract NetworkNode.Dialect dialect();

    @BeforeAll
    void startInfrastructure() throws Exception {
        server = MockBukkit.mock();
        QueryConfiguration.setDefault(
                QueryConfiguration.builder(startDatabase()).build());
    }

    @AfterAll
    void stopInfrastructure() {
        stopDatabase();
        MockBukkit.unmock();
    }

    @BeforeEach
    void chooseFolder() {
        root = folder.resolve(UUID.randomUUID().toString());
    }

    @AfterEach
    void stopNodes() {
        nodes.forEach(NetworkNode::close);
    }

    private NetworkNode node(String name, UUID uid, boolean primary, NetworkNode.Buses bus) {
        var node = NetworkNode.start(new NetworkNode.Setup(
                name, server, root.resolve(name), uid, primary, dialect(), bus, POLL_SECONDS, Clock.systemUTC()));
        nodes.add(node);
        return node;
    }

    private NetworkNode primary() {
        return node("A", PRIMARY, true, NetworkNode.Buses.lowLatency(TIMINGS));
    }

    private NetworkNode secondary() {
        return node("B", SECONDARY, false, NetworkNode.Buses.lowLatency(TIMINGS));
    }

    private static void await(BooleanSupplier condition, Duration limit, String what) throws InterruptedException {
        var deadline = System.nanoTime() + limit.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline)
                throw new AssertionError("Timed out after " + limit + " waiting for " + what);
            Thread.sleep(10);
        }
    }

    private static void awaitReading(NetworkNode... nodes) throws InterruptedException {
        for (var node : nodes) await(node.bus::realtime, WAIT, "node " + node.name + " to read the message table");
    }

    private static NotificationArgs args() {
        return NotificationArgs.create().text("order_name", "Wood").number("order_id", 4);
    }

    private List<String> payloads(NetworkNode node) {
        return node.messages.after(0, 10_000).stream()
                .map(ANodeMessageData.StoredMessage::payload)
                .toList();
    }

    @Test
    void aNotificationReachesThePlayerOnTheOtherNodeWithinTwoSeconds() throws InterruptedException {
        var a = primary();
        var b = secondary();
        awaitReading(a, b);
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
        assertEquals(NodeBus.Transport.LOW_LATENCY, a.bus.transport());
    }

    @Test
    void companyRankingAndBoardChangesReachTheCachesOfTheOtherNode() throws InterruptedException {
        var a = primary();
        var b = secondary();
        awaitReading(a, b);
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
                ARRIVAL,
                "the caches of B to drop");
    }

    @Test
    void aSyncOnThePrimaryReachesTheSecondary() throws Exception {
        var a = primary();
        var b = secondary();
        awaitReading(a, b);
        var file = root.resolve("A").resolve("config.yml");
        a.configuration.save(Configuration.CONFIG_YML);
        var content = Files.readString(file);
        var before = b.configuration.notificationSettings().retentionDays();
        var changed = before + 15;
        Files.writeString(file, content.replace("retentionDays: " + before, "retentionDays: " + changed));
        a.configuration.reload();

        a.configuration.syncConfigurations(a.nodeData).join();

        await(
                () -> b.configuration.notificationSettings().retentionDays() == changed && b.reloads.get() > 0,
                WAIT,
                "the secondary to load and apply the configuration");
        assertEquals(0, a.reloads.get());
    }

    @Test
    void presenceAndTheBusOfANodeTravelThroughTheTable() throws InterruptedException {
        var a = primary();
        var b = secondary();
        awaitReading(a, b);
        var alice = server.addPlayer();

        a.connection.signals().onJoin(new PlayerJoinEvent(alice, Component.empty()));
        await(() -> b.presence.nodeOf(alice.getUniqueId()).equals(Optional.of(PRIMARY)), ARRIVAL, "the join");

        a.beat();
        await(
                () -> b.presence.transport(PRIMARY).equals(Optional.of(NodeBus.Transport.LOW_LATENCY)),
                ARRIVAL,
                "the heartbeat naming the bus");
        assertTrue(b.presence.alive(PRIMARY));
    }

    @Test
    void thePrimaryDeletesRowsOlderThanAMinute() throws InterruptedException {
        var a = primary();
        var b = secondary();
        awaitReading(a, b);
        var old = codec.encode(SECONDARY, new CacheInvalidate("probe", "old"));
        var recent = codec.encode(SECONDARY, new CacheInvalidate("probe", "recent"));
        b.messages.insert(SECONDARY, "cache.invalidate", old, Times.now().minusMinutes(2));
        b.messages.insert(SECONDARY, "cache.invalidate", recent, Times.now());

        await(() -> !payloads(a).contains(old), WAIT, "the primary to delete the old row");
        assertTrue(payloads(a).contains(recent));
    }

    @Test
    void aStartingNodeSkipsTheMessagesAlreadyInTheTable() throws InterruptedException {
        var a = primary();
        awaitReading(a);
        a.messages.insert(
                PRIMARY,
                "cache.invalidate",
                codec.encode(PRIMARY, new CacheInvalidate("probe", "before")),
                Times.now());
        var b = secondary();
        awaitReading(b);

        a.bus.publish(new CacheInvalidate("probe", "after"));

        await(() -> b.probes.contains("after"), ARRIVAL, "the message sent after the start");
        assertFalse(b.probes.contains("before"));
        assertTrue(a.probes.containsAll(List.of("before", "after")), a.probes.toString());
    }
}
