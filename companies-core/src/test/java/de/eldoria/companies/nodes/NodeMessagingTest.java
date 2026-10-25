/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.BoardsChanged;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.nodes.NodeMessage.Heartbeat;
import de.eldoria.companies.nodes.NodeMessage.InboxNew;
import de.eldoria.companies.nodes.NodeMessage.NodeStopped;
import de.eldoria.companies.nodes.NodeMessage.PlayerPresence;
import de.eldoria.companies.nodes.NodeMessage.RankingChanged;
import de.eldoria.companies.nodes.NodeMessage.SettingsChanged;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeMessagingTest {
    private static final UUID SELF = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();

    private final LocalBus bus = new LocalBus(SELF);
    private final Logger logger = Logger.getLogger("NodeMessagingTest-" + UUID.randomUUID());
    private final NodeCaches caches = new NodeCaches(logger);
    private final NetworkPresence presence = new NetworkPresence(Clock.systemUTC());
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    private final List<UUID> delivered = new CopyOnWriteArrayList<>();
    private final List<String> dropped = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final List<SettingsChanged> revisions = new CopyOnWriteArrayList<>();
    private final AtomicInteger stateChanges = new AtomicInteger();
    private final AtomicInteger lookups = new AtomicInteger();

    @AfterEach
    void tearDown() {
        timer.shutdownNow();
    }

    private NodeMessaging start(boolean primary) {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        caches.register(NodeCaches.COMPANY, key -> dropped.add("company:" + key.orElse("*")));
        caches.register(NodeCaches.RANKING, key -> dropped.add("ranking"));
        caches.register(NodeCaches.BOARDS, key -> dropped.add("boards"));
        var messaging = new NodeMessaging(bus, caches, presence, logger);
        messaging.start(
                new NodeMessaging.Node(
                        primary,
                        delivered::add,
                        revisions::add,
                        stateChanges::incrementAndGet,
                        () -> List.of(PLAYER),
                        () -> {
                            lookups.incrementAndGet();
                            return List.of(SELF, OTHER);
                        }),
                timer);
        awaitFirstHeartbeat();
        return messaging;
    }

    /**
     * Waits until the heartbeat sent at start put the players of this node into the presence, so it can not interleave
     * with the messages a test sends.
     */
    private void awaitFirstHeartbeat() {
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!presence.nodeOf(PLAYER).equals(Optional.of(SELF))) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for the first heartbeat");
            Thread.onSpinWait();
        }
    }

    @Test
    void announcedEntriesGoToTheInbox() {
        start(false);

        bus.receive(OTHER, new InboxNew(4L, PLAYER));

        assertEquals(List.of(PLAYER), delivered);
    }

    @Test
    void everyNodeTakesANewRevision() {
        start(true);
        bus.receive(OTHER, new SettingsChanged("company.yml", 4));
        assertEquals(List.of(new SettingsChanged("company.yml", 4)), revisions);
    }

    @Test
    void cacheMessagesReachTheCaches() {
        start(false);

        bus.receive(OTHER, new CacheInvalidate(NodeCaches.COMPANY, "4"));
        bus.receive(OTHER, new CacheInvalidate(NodeCaches.COMPANY, null));
        bus.receive(OTHER, new RankingChanged());
        bus.receive(OTHER, new BoardsChanged());

        assertEquals(List.of("company:4", "company:*", "ranking", "boards"), dropped);
    }

    @Test
    void presenceFollowsTheMessages() {
        start(false);

        bus.receive(OTHER, new PlayerPresence(PLAYER, true));
        assertEquals(Optional.of(OTHER), presence.nodeOf(PLAYER));

        bus.receive(OTHER, new Heartbeat(List.of()));
        assertEquals(Optional.empty(), presence.nodeOf(PLAYER));

        bus.receive(OTHER, new Heartbeat(List.of(PLAYER)));
        bus.receive(OTHER, new NodeStopped());
        assertEquals(Optional.empty(), presence.nodeOf(PLAYER));
    }

    @Test
    void heartbeatsTellTheBusOfTheirNode() {
        start(false);

        bus.receive(OTHER, new Heartbeat(List.of(PLAYER), "lowLatency"));
        assertEquals(Optional.of(NodeBus.Transport.LOW_LATENCY), presence.transport(OTHER));

        bus.receive(OTHER, new Heartbeat(List.of(PLAYER), "carrierPigeon"));
        assertEquals(Optional.empty(), presence.transport(OTHER));
    }

    @Test
    void anUnknownNodeIsReportedOnceAndKnownNodesAreLookedUpOnce() {
        start(false);
        var stranger = UUID.randomUUID();

        bus.receive(stranger, new PlayerPresence(PLAYER, true));
        bus.receive(stranger, new Heartbeat(List.of(PLAYER)));
        bus.receive(OTHER, new Heartbeat(List.of()));
        bus.receive(OTHER, new Heartbeat(List.of()));

        assertEquals(
                1,
                warnings.stream().filter(w -> w.contains(stranger.toString())).count());
        assertTrue(lookups.get() <= 3, "lookups: " + lookups.get());
    }

    @Test
    void aReconnectDropsTheCachesAndALossForgetsThePresence() {
        start(false);
        bus.receive(OTHER, new Heartbeat(List.of(PLAYER)));

        bus.change(NodeBus.State.DISCONNECTED);

        assertEquals(1, stateChanges.get());
        assertTrue(dropped.isEmpty());
        assertEquals(Optional.empty(), presence.nodeOf(PLAYER));

        bus.change(NodeBus.State.CONNECTED);

        assertEquals(2, stateChanges.get());
        assertTrue(dropped.containsAll(List.of("company:*", "ranking", "boards")), dropped.toString());
        assertTrue(bus.published.contains(new Heartbeat(List.of(PLAYER), "database")));
    }

    @Test
    void stoppingTellsTheOthersAndClosesTheBus() {
        var messaging = start(false);

        messaging.stop();

        assertTrue(bus.published.contains(new NodeStopped()));
        assertTrue(bus.closed);
    }
}
