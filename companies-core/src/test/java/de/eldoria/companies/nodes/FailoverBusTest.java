/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailoverBusTest {
    private static final UUID SELF = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final MessageTableBus.Timings FAST =
            new MessageTableBus.Timings(Duration.ofMillis(20), Duration.ofMinutes(1), Duration.ofMinutes(1));
    private static final Duration WAIT = Duration.ofSeconds(5);

    private final MemoryMessageTable table = new MemoryMessageTable();
    private final LocalBus redis = new LocalBus(SELF);
    private final MessageTableBus tableBus =
            MessageTableBus.create(table, SELF, false, Runnable::run, Logger.getLogger("FailoverBusTest"), FAST);
    private final List<NodeBus.State> states = new CopyOnWriteArrayList<>();
    private final List<String> received = new CopyOnWriteArrayList<>();

    @AfterEach
    void close() {
        tableBus.close();
    }

    private FailoverBus start() {
        var bus = FailoverBus.start(redis, tableBus);
        bus.onStateChange(states::add);
        bus.subscribe(CacheInvalidate.class, (sender, message) -> received.add(message.key()));
        return bus;
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for " + what);
            Thread.sleep(5);
        }
    }

    @Test
    void whileRedisIsConnectedMessagesTravelOverRedis() {
        var bus = start();

        bus.publish(new CacheInvalidate("probe", "redis"));

        assertFalse(bus.fallingBack());
        assertEquals(List.of(new CacheInvalidate("probe", "redis")), redis.published);
        assertEquals(List.of("redis"), received);
        assertTrue(table.rows().isEmpty());
    }

    @Test
    void aLostRedisSwitchesToTheTableUntilItIsBack() throws InterruptedException {
        var bus = start();

        redis.change(NodeBus.State.DISCONNECTED);
        assertTrue(bus.fallingBack());
        assertEquals(NodeBus.Transport.LOW_LATENCY, bus.transport());
        await(bus::realtime, "the table to be read");
        table.insert(
                OTHER,
                "cache.invalidate",
                new NodeMessageCodec().encode(OTHER, new CacheInvalidate("p", "x")),
                LocalDateTime.now());
        await(() -> received.contains("x"), "a message of another node through the table");
        bus.publish(new CacheInvalidate("probe", "table"));
        await(() -> received.contains("table"), "the own message through the table");
        assertTrue(redis.published.isEmpty());
        assertEquals(NodeBus.State.CONNECTED, states.getLast());
        var reports = states.size();

        redis.change(NodeBus.State.CONNECTED);
        assertFalse(bus.fallingBack());
        bus.publish(new CacheInvalidate("probe", "redis"));

        assertEquals(List.of(new CacheInvalidate("probe", "redis")), redis.published);
        assertEquals(reports + 1, states.size());
        assertEquals(NodeBus.State.CONNECTED, states.getLast());
    }

    @Test
    void redisAwayAtStartReadsTheTableAtOnce() throws InterruptedException {
        redis.change(NodeBus.State.DISCONNECTED);

        var bus = start();

        assertTrue(bus.fallingBack());
        await(bus::realtime, "the table to be read");
        assertEquals(NodeBus.Transport.LOW_LATENCY, bus.transport());
    }
}
