/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.RedisSettings;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.nodes.NodeMessage.InboxNew;
import de.eldoria.companies.nodes.NodeMessage.RankingChanged;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.args.ClientType;
import redis.clients.jedis.params.ClientKillParams;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisBusTest {
    private static final String PASSWORD = "test-only-redis-password";
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final Logger LOGGER = Logger.getLogger("RedisBusTest");

    private static GenericContainer<?> redis;

    private final ExecutorService handlers = Executors.newVirtualThreadPerTaskExecutor();
    private final List<NodeBus> buses = new ArrayList<>();

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--requirepass", PASSWORD);
        redis.start();
    }

    @AfterAll
    static void stopRedis() {
        redis.stop();
    }

    @AfterEach
    void closeBuses() {
        buses.forEach(NodeBus::close);
        handlers.shutdownNow();
    }

    private static RedisBus.Connection connection(String password, String prefix) {
        return new RedisBus.Connection(redis.getHost(), redis.getMappedPort(6379), "", password, false, 0, prefix);
    }

    private RedisBus bus(UUID node, RedisBus.Connection connection) {
        var bus = RedisBus.start(connection, node, handlers, LOGGER, WAIT);
        buses.add(bus);
        return bus;
    }

    private RedisBus bus(UUID node) {
        return bus(node, connection(PASSWORD, "companies"));
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        var deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for " + what);
            Thread.sleep(20);
        }
    }

    private Jedis client() {
        var jedis = new Jedis(redis.getHost(), redis.getMappedPort(6379));
        jedis.auth(PASSWORD);
        return jedis;
    }

    @Test
    void messagesReachEveryNodeWithTheirSender() throws InterruptedException {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        record Received(UUID receiver, UUID sender, InboxNew message) {}
        List<Received> received = new CopyOnWriteArrayList<>();
        var sending = bus(first);
        var listening = bus(second);
        sending.subscribe(InboxNew.class, (node, message) -> received.add(new Received(first, node, message)));
        listening.subscribe(InboxNew.class, (node, message) -> received.add(new Received(second, node, message)));
        assertTrue(sending.realtime());
        assertTrue(listening.realtime());

        var message = new InboxNew(7L, UUID.randomUUID());
        sending.publish(message);

        await(() -> received.size() == 2, "both nodes");
        assertTrue(received.contains(new Received(first, first, message)));
        assertTrue(received.contains(new Received(second, first, message)));
    }

    @Test
    void handlersOnlyGetTheirType() throws InterruptedException {
        List<NodeMessage> received = new CopyOnWriteArrayList<>();
        var bus = bus(UUID.randomUUID());
        bus.subscribe(RankingChanged.class, (node, message) -> received.add(message));

        bus.publish(new CacheInvalidate(NodeCaches.COMPANY, "1"));
        bus.publish(new RankingChanged());

        await(() -> received.size() == 1, "the ranking message");
        Thread.sleep(200);
        assertEquals(List.of(new RankingChanged()), received);
    }

    @Test
    void foreignAndBrokenMessagesAreSkipped() throws InterruptedException {
        List<NodeMessage> received = new CopyOnWriteArrayList<>();
        var bus = bus(UUID.randomUUID());
        bus.subscribe(RankingChanged.class, (node, message) -> received.add(message));
        var sender = UUID.randomUUID();

        try (var jedis = client()) {
            jedis.publish("companies:ranking.changed", "not json");
            jedis.publish("companies:ranking.changed", """
                    {"v":2,"type":"ranking.changed","node":"%s"}""".formatted(sender));
            jedis.publish("companies:event.order", """
                    {"v":1,"type":"event.order","node":"%s"}""".formatted(sender));
            jedis.publish("companies:ranking.changed", """
                    {"v":1,"type":"ranking.changed","node":"%s","extra":true}""".formatted(sender));
        }

        await(() -> received.size() == 1, "the readable message");
        Thread.sleep(200);
        assertEquals(List.of(new RankingChanged()), received);
    }

    @Test
    void networksWithAnotherPrefixDoNotHearEachOther() throws InterruptedException {
        List<NodeMessage> other = new CopyOnWriteArrayList<>();
        List<NodeMessage> own = new CopyOnWriteArrayList<>();
        var otherNetwork = bus(UUID.randomUUID(), connection(PASSWORD, "other"));
        otherNetwork.subscribe(RankingChanged.class, (node, message) -> other.add(message));
        var bus = bus(UUID.randomUUID());
        bus.subscribe(RankingChanged.class, (node, message) -> own.add(message));

        bus.publish(new RankingChanged());

        await(() -> own.size() == 1, "the own network");
        Thread.sleep(200);
        assertTrue(other.isEmpty());
    }

    @Test
    void aWrongPasswordFallsBackWithoutFailing() {
        var bus = bus(UUID.randomUUID(), connection("wrong", "companies"));

        assertEquals(NodeBus.State.DISCONNECTED, bus.state());
        assertFalse(bus.realtime());
        bus.publish(new RankingChanged());
    }

    @Test
    void anUnreachableServerFallsBackWithoutFailing() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var bus = bus(UUID.randomUUID(), new RedisBus.Connection("127.0.0.1", port, "", "", false, 0, "companies"));

        assertEquals(NodeBus.State.DISCONNECTED, bus.state());
        assertFalse(bus.realtime());
        bus.publish(new RankingChanged());
    }

    @Test
    void aLostConnectionIsReportedAndRestored() throws InterruptedException {
        List<NodeBus.State> states = new CopyOnWriteArrayList<>();
        List<NodeMessage> received = new CopyOnWriteArrayList<>();
        var bus = bus(UUID.randomUUID());
        bus.onStateChange(states::add);
        bus.subscribe(RankingChanged.class, (node, message) -> received.add(message));
        assertTrue(bus.realtime());

        try (var jedis = client()) {
            jedis.clientKill(ClientKillParams.clientKillParams().type(ClientType.PUBSUB));
        }

        await(() -> states.equals(List.of(NodeBus.State.DISCONNECTED, NodeBus.State.CONNECTED)), "the reconnect");
        bus.publish(new RankingChanged());
        await(() -> received.size() == 1, "a message after the reconnect");
    }

    @Test
    void theFactoryUsesTheDatabaseBusWithoutRedisOrPassword() {
        var disabled = NodeBuses.create(new RedisSettings(), UUID.randomUUID(), handlers, name -> null, LOGGER);
        var missing = NodeBuses.create(
                settings("enabled: true\npassword: env:UNSET_VARIABLE"),
                UUID.randomUUID(),
                handlers,
                name -> null,
                LOGGER);

        assertInstanceOf(DatabaseBus.class, disabled);
        assertInstanceOf(DatabaseBus.class, missing);
    }

    @Test
    void theFactoryConnectsWithAnEnvironmentPassword() {
        var yaml = """
                enabled: true
                host: %s
                port: %d
                password: env:COMPANIES_REDIS_PASSWORD
                """.formatted(redis.getHost(), redis.getMappedPort(6379));
        var bus = NodeBuses.create(
                settings(yaml),
                UUID.randomUUID(),
                handlers,
                name -> name.equals("COMPANIES_REDIS_PASSWORD") ? PASSWORD : null,
                LOGGER);
        buses.add(bus);

        assertInstanceOf(RedisBus.class, bus);
        assertTrue(bus.realtime());
    }

    @Test
    void theConnectionNeverShowsThePassword() {
        assertFalse(connection(PASSWORD, "companies").toString().contains(PASSWORD));
    }

    private static RedisSettings settings(String yaml) {
        return YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                .build()
                .readValue(yaml, RedisSettings.class);
    }
}
