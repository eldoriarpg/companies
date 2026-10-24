/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.link.Ping;
import de.eldoria.companies.web.api.link.Pong;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.session.SessionCreated;
import de.eldoria.companies.web.api.snapshot.SnapshotRequest;
import de.eldoria.companies.web.api.update.Unsubscribe;
import de.eldoria.companies.web.gateway.policy.VersionPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A connected node whose version reaches its discontinuation date while it stays connected.
 */
class PolicyDatesTest {
    private static final String POLICY = """
            {"windowMonths": 6, "deprecations": [],
             "versions": [{"from": "2.0.0", "before": "2.1.0", "status": "DEPRECATED", "discontinuedAt": "2027-06-01",
                           "updateTo": "2.2.0", "message": "Update Companies to 2.2."}]}""";
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2027-05-31T23:58:00Z"));
    private static TestGateway gateway;
    private static String token;

    @BeforeAll
    static void start() {
        Map<String, String> env = TestDatabase.env("policy_dates");
        env.put("GATEWAY_MODE", "self-hosted");
        gateway = TestGateway.start(
                env, VersionPolicy.read(new ByteArrayInputStream(POLICY.getBytes(StandardCharsets.UTF_8))), CLOCK);
        Matcher matcher = Pattern.compile("cst_\\S+").matcher(gateway.printed());
        assertTrue(matcher.find());
        token = matcher.group();
    }

    @AfterAll
    static void stop() {
        gateway.close();
    }

    @Test
    void aVersionIsDiscontinuedWhileConnected() {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.SERVER_TOKEN, token, UUID.randomUUID(), "2.0.4"));
            assertEquals(
                    VersionState.DEPRECATED,
                    node.expect(Welcome.class).version().status());
            gateway.app().sweep();
            node.assertQuiet();

            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.50");
            node.send(new CreateSession("c", 1, new PlayerRef(UUID.randomUUID(), "Steve"), UUID.randomUUID()));
            SessionCreated created = node.expect(SessionCreated.class);
            HostedGatewayTest.login(node, browser, created);
            CompletableFuture<HttpResponse<String>> loading = browser.getAsync("/api/v1/me/snapshot");
            SnapshotRequest request = node.expect(SnapshotRequest.class);
            node.sendRaw(Recorded.snapshot(request.id(), request.session(), UUID.randomUUID(), 1));
            assertEquals(200, loading.join().statusCode());

            CLOCK.advance(Duration.ofMinutes(3));
            node.send(new Ping("p", 1, CLOCK.instant()));
            node.expect(Pong.class);
            gateway.app().sweep();
            assertEquals(created.session(), node.expect(Unsubscribe.class).session());
            Welcome again = node.expect(Welcome.class);
            assertNull(again.id());
            assertEquals(VersionState.DISCONTINUED, again.version().status());

            node.send(new CreateSession("c", 1, new PlayerRef(UUID.randomUUID(), "Steve"), UUID.randomUUID()));
            assertEquals(ErrorCode.DISCONTINUED, node.expect(ErrorMessage.class).code());
        }
    }
}
