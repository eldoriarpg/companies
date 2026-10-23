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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTableBusTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final MessageTableBus.Timings FAST =
            new MessageTableBus.Timings(Duration.ofMillis(20), Duration.ofMinutes(1), Duration.ofMillis(20));
    private static final Duration WAIT = Duration.ofSeconds(5);

    private final MemoryMessageTable table = new MemoryMessageTable();
    private final NodeMessageCodec codec = new NodeMessageCodec();
    private final Logger logger = Logger.getLogger("MessageTableBusTest");
    private final List<MessageTableBus> buses = new ArrayList<>();

    @AfterEach
    void close() {
        buses.forEach(MessageTableBus::close);
    }

    private MessageTableBus start(MemoryMessageTable data, UUID node, boolean primary) {
        var bus = MessageTableBus.start(data, node, primary, Runnable::run, logger, FAST);
        buses.add(bus);
        return bus;
    }

    private static List<String> received(NodeBus bus) {
        List<String> keys = new CopyOnWriteArrayList<>();
        bus.subscribe(CacheInvalidate.class, (sender, message) -> keys.add(sender + "/" + message.key()));
        return keys;
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for " + what);
            Thread.sleep(5);
        }
    }

    private String encoded(String key) {
        return codec.encode(A, new CacheInvalidate("probe", key));
    }

    @Test
    void aMessageReachesEveryNodeIncludingTheSender() throws InterruptedException {
        var a = start(table, A, true);
        var b = start(table, B, false);
        var atA = received(a);
        var atB = received(b);
        await(() -> a.realtime() && b.realtime(), "both buses to read");

        a.publish(new CacheInvalidate("probe", "one"));

        await(() -> atA.size() == 1 && atB.size() == 1, "the message on both nodes");
        assertEquals(List.of(A + "/one"), atB);
        assertEquals(List.of(A + "/one"), atA);
        assertEquals("cache.invalidate", table.rows().getFirst().type());
        assertEquals(NodeBus.Transport.LOW_LATENCY, a.transport());
    }

    @Test
    void readingStartsAtTheHighestIdPresent() throws InterruptedException {
        table.insert(A, "cache.invalidate", encoded("old"), LocalDateTime.now());
        var b = start(table, B, false);
        var atB = received(b);
        await(b::realtime, "the bus to read");

        b.publish(new CacheInvalidate("probe", "new"));

        await(() -> atB.contains(B + "/new"), "the new message");
        assertEquals(List.of(B + "/new"), atB);
    }

    @Test
    void aRowCommittedLateIsDeliveredOnce() throws InterruptedException {
        var b = start(table, B, false);
        var atB = received(b);
        await(b::realtime, "the bus to read");
        var late = table.skipId();
        table.insert(A, "cache.invalidate", encoded("early"), LocalDateTime.now());
        await(() -> atB.contains(A + "/early"), "the early row");

        table.insertWithId(late, encoded("late"));

        await(() -> atB.contains(A + "/late"), "the late row");
        var reads = table.reads.get();
        await(() -> table.reads.get() > reads + 3, "a few more reads");
        assertEquals(List.of(A + "/early", A + "/late"), atB);
    }

    @Test
    void manyRowsAreReadInPages() throws InterruptedException {
        var b = start(table, B, false);
        var atB = received(b);
        await(b::realtime, "the bus to read");

        var count = MessageTableBus.PAGE * 2 + 3;
        for (var i = 0; i < count; i++) table.insert(A, "cache.invalidate", encoded("m" + i), LocalDateTime.now());

        await(() -> atB.size() == count, "every row");
        assertEquals(A + "/m" + (count - 1), atB.getLast());
    }

    @Test
    void aFailedReadFallsBackUntilTheTableCanBeReadAgain() throws InterruptedException {
        var b = start(table, B, false);
        List<NodeBus.State> states = new CopyOnWriteArrayList<>();
        b.onStateChange(states::add);
        var atB = received(b);
        await(b::realtime, "the bus to read");

        table.broken = true;
        await(() -> b.state() == NodeBus.State.DISCONNECTED, "the failure");
        assertFalse(b.realtime());
        table.broken = false;
        table.insert(A, "cache.invalidate", encoded("meanwhile"), LocalDateTime.now());

        await(b::realtime, "the recovery");
        await(() -> atB.contains(A + "/meanwhile"), "the row written meanwhile");
        assertEquals(List.of(NodeBus.State.DISCONNECTED, NodeBus.State.CONNECTED), states);
    }

    @Test
    void onlyThePrimaryDeletesOldRows() throws InterruptedException {
        var secondaryTable = new MemoryMessageTable();
        var old = LocalDateTime.now().minusMinutes(2);
        table.insert(A, "cache.invalidate", encoded("old"), old);
        table.insert(A, "cache.invalidate", encoded("recent"), LocalDateTime.now());
        secondaryTable.insert(A, "cache.invalidate", encoded("old"), old);
        start(table, A, true);
        start(secondaryTable, B, false);

        await(() -> table.rows().size() == 1, "the old row to be deleted");
        assertEquals(encoded("recent"), table.rows().getFirst().payload());
        await(() -> secondaryTable.reads.get() > 10, "the secondary to read a while");
        assertEquals(0, secondaryTable.purges.get());
        assertEquals(1, secondaryTable.rows().size());
    }

    @Test
    void aPausedBusResumesAtTheHighestIdPresent() throws InterruptedException {
        var a = start(table, A, true);
        var b = start(table, B, false);
        var atB = received(b);
        await(() -> a.realtime() && b.realtime(), "both buses to read");

        b.pause();
        a.publish(new CacheInvalidate("probe", "missed"));
        await(() -> table.rows().size() == 1, "the missed message to be stored");
        b.resume();
        a.publish(new CacheInvalidate("probe", "after"));

        await(() -> atB.contains(A + "/after"), "the message after resuming");
        assertEquals(List.of(A + "/after"), atB);
    }

    @Test
    void aClosedBusPublishesNothing() {
        var a = start(table, A, true);
        a.close();

        a.publish(new CacheInvalidate("probe", "late"));

        assertTrue(table.rows().isEmpty());
    }
}
