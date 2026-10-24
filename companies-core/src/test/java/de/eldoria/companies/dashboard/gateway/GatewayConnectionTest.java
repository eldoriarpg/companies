/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.gateway;

import de.eldoria.companies.data.wrapper.dashboard.StoredKey;
import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.Branding;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.NodeInfo;
import de.eldoria.companies.web.api.handshake.NodeRole;
import de.eldoria.companies.web.api.link.Ping;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.session.SessionCreated;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayConnectionTest {
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private final BlockingQueue<Message> inbound = new LinkedBlockingQueue<>();
    private final AtomicReference<Optional<StoredKey>> key =
            new AtomicReference<>(Optional.of(StoredKey.entered("lyd_test-key", null)));
    private final AtomicBoolean discontinued = new AtomicBoolean();
    private final List<GatewayConnection.Closed> closed = new CopyOnWriteArrayList<>();
    private FakeGateway gateway;
    private GatewayConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        gateway = FakeGateway.start();
        var handshake = new Handshake(
                new Handshake.Identity(
                        "2.1.0-test", "1.11", new NodeInfo(NODE, NodeRole.PRIMARY), "Companies/2.1.0-test", 50),
                key::get,
                () -> List.of("orders.browse", "inbox"),
                () -> Optional.of(new Branding("Test Server", "aWNvbg==", "abc123")));
        connection = new GatewayConnection(
                new GatewayConnection.Endpoint(gateway.uri(), "Companies/2.1.0-test"),
                new GatewayConnection.Timing(
                        Duration.ofMillis(300),
                        Duration.ofSeconds(10),
                        Duration.ofMillis(200),
                        Duration.ofHours(6),
                        Duration.ofHours(1),
                        Duration.ofMinutes(5),
                        new Backoff(() -> 0.5)),
                handshake::hello,
                inbound::add,
                discontinued::get,
                closed::add,
                InstantSource.system(),
                Logger.getAnonymousLogger());
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.stop();
        gateway.close();
    }

    @Test
    void theHelloIsSentFirstWithTheUserAgentKeyFeaturesAndIconHash() throws Exception {
        connection.start();

        var opened = gateway.nextConnection(WAIT);
        var hello = gateway.next(Hello.class, WAIT);

        assertEquals("Companies/2.1.0-test", opened.header("User-Agent"));
        assertEquals(Protocol.VERSION, hello.v());
        assertEquals("2.1.0-test", hello.plugin());
        assertEquals("1.11", hello.schema());
        assertEquals(new NodeInfo(NODE, NodeRole.PRIMARY), hello.node());
        assertEquals(List.of("orders.browse", "inbox"), hello.features());
        assertEquals(50, hello.limits().maxPageSize());
        assertEquals(KeyKind.DASHBOARD_KEY, hello.credentials().kind());
        assertEquals("lyd_test-key", hello.credentials().key());
        assertEquals("Companies/2.1.0-test", hello.userAgent());
        assertEquals("Test Server", hello.branding().name());
        assertEquals("abc123", hello.branding().iconHash());
        assertNull(hello.branding().icon());
        assertEquals(GatewayConnection.State.CONNECTED, connection.state());
    }

    @Test
    void pingsAreSentWhileConnected() throws Exception {
        connection.start();
        gateway.nextConnection(WAIT);

        var ping = gateway.next(Ping.class, WAIT);

        assertEquals(Protocol.VERSION, ping.v());
    }

    @Test
    void theNodeConnectsAgainAfterTheGatewayClosed() throws Exception {
        connection.start();
        var first = gateway.nextConnection(WAIT);
        gateway.next(Hello.class, WAIT);

        first.close();

        gateway.nextConnection(WAIT);
        gateway.next(Hello.class, WAIT);
        assertEquals(2, gateway.connections().size());
    }

    @Test
    void withoutAKeyTheNodeWaitsAndConnectsOnceOneIsStored() throws Exception {
        key.set(Optional.empty());
        connection.start();
        TimeUnit.MILLISECONDS.sleep(500);
        assertEquals(GatewayConnection.State.NO_KEY, connection.state());
        assertTrue(gateway.connections().isEmpty());

        key.set(Optional.of(StoredKey.entered("self-hosted-token", null)));
        connection.wake();

        gateway.nextConnection(WAIT);
        assertEquals(
                KeyKind.SERVER_TOKEN,
                gateway.next(Hello.class, WAIT).credentials().kind());
    }

    @Test
    void aDiscontinuedVersionWaitsHoursBeforeTheNextAttempt() throws Exception {
        connection.start();
        var first = gateway.nextConnection(WAIT);
        gateway.next(Hello.class, WAIT);
        discontinued.set(true);

        first.close();
        TimeUnit.SECONDS.sleep(3);

        assertEquals(1, gateway.connections().size());
        assertEquals(GatewayConnection.State.RETRYING, connection.state());
    }

    @Test
    void aRefusedKeyWaitsUntilANewKeyWakesTheNode() throws Exception {
        connection.start();
        var first = gateway.nextConnection(WAIT);
        gateway.next(Hello.class, WAIT);

        first.close(GatewayConnection.CLOSE_UNAUTHORIZED, "EXPIRED");
        TimeUnit.SECONDS.sleep(3);

        assertEquals(1, gateway.connections().size());
        assertEquals(List.of(new GatewayConnection.Closed(GatewayConnection.CLOSE_UNAUTHORIZED, "EXPIRED")), closed);
        connection.wake();
        gateway.nextConnection(WAIT);
    }

    @Test
    void aReplacedConnectionDoesNotFightBack() throws Exception {
        connection.start();
        var first = gateway.nextConnection(WAIT);
        gateway.next(Hello.class, WAIT);

        first.close(GatewayConnection.CLOSE_REPLACED, "replaced");
        TimeUnit.SECONDS.sleep(3);

        assertEquals(1, gateway.connections().size());
        assertEquals(GatewayConnection.CLOSE_REPLACED, closed.getFirst().code());
    }

    @Test
    void anAnswerCompletesItsRequestAndOtherMessagesArriveInbound() throws Exception {
        connection.start();
        gateway.nextConnection(WAIT);
        gateway.next(Hello.class, WAIT);
        var player = new PlayerRef(UUID.randomUUID(), "Kestrel_");

        var answer = connection.request(
                new CreateSession("request-1", Protocol.VERSION, player, NODE), Duration.ofSeconds(5));
        var create = gateway.next(CreateSession.class, WAIT);
        gateway.send(new SessionCreated(
                create.id(),
                Protocol.VERSION,
                "session-1",
                "AB12CD",
                "KPMWQR",
                "https://dash.example/AB12CD",
                Instant.parse("2026-10-24T10:05:00Z")));
        gateway.send(new Ping("gateway-ping", Protocol.VERSION, Instant.parse("2026-10-24T10:00:00Z")));

        var created = assertInstanceOf(SessionCreated.class, answer.get(5, TimeUnit.SECONDS));
        assertEquals("session-1", created.session());
        assertInstanceOf(Ping.class, inbound.poll(5, TimeUnit.SECONDS));
    }
}
