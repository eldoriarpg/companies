/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.Features;
import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.NodeRole;
import de.eldoria.companies.web.api.handshake.PartKind;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.link.Ping;
import de.eldoria.companies.web.api.link.Pong;
import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.session.SessionClosed;
import de.eldoria.companies.web.api.session.SessionCreated;
import de.eldoria.companies.web.api.session.SessionOpened;
import de.eldoria.companies.web.api.session.SessionsOpen;
import de.eldoria.companies.web.api.update.Unsubscribe;
import de.eldoria.companies.web.gateway.policy.VersionPolicy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version policy at the handshake, and the end of sessions over time: expired codes, idle sessions and servers
 * gone past the grace period.
 */
class LifecycleTest {
    private static final String POLICY = """
            {
              "windowMonths": 6,
              "versions": [
                {"before": "2.0.0", "status": "DISCONTINUED", "since": "2026-12-01", "updateTo": "2.2.0",
                 "message": "Companies 1 is no longer supported on the dashboard. Update to 2.2 or newer."},
                {"from": "2.0.0", "before": "2.1.0", "status": "DEPRECATED", "discontinuedAt": "2027-06-01",
                 "updateTo": "2.2.0", "message": "Update Companies to 2.2 before June."}
              ],
              "deprecations": [
                {"kind": "FEATURE", "name": "market", "since": "2026-12-01", "replacement": "market.v2",
                 "message": "Update Companies, or market statistics stop working on the dashboard."},
                {"kind": "FEATURE", "name": "admin.config", "since": "2026-01-01", "removedAt": "2026-06-01"}
              ]
            }""";
    private static final UUID PLAYER = UUID.randomUUID();
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2027-01-10T12:00:00Z"));
    private static TestGateway gateway;
    private static String token;

    @BeforeAll
    static void start() {
        Map<String, String> env = TestDatabase.env("lifecycle");
        env.put("GATEWAY_MODE", "self-hosted");
        VersionPolicy policy = VersionPolicy.read(new ByteArrayInputStream(POLICY.getBytes(StandardCharsets.UTF_8)));
        gateway = TestGateway.start(env, policy, CLOCK);
        Matcher matcher = Pattern.compile("cst_\\S+").matcher(gateway.printed());
        assertTrue(matcher.find());
        token = matcher.group();
    }

    @AfterAll
    static void stop() {
        gateway.close();
    }

    private static FakeNode connect(String plugin, List<String> features) {
        FakeNode node = FakeNode.connect(gateway);
        node.send(FakeNode.hello(
                KeyKind.SERVER_TOKEN, token, UUID.randomUUID(), NodeRole.PRIMARY, plugin, features, null));
        return node;
    }

    @Test
    void aDiscontinuedVersionGetsItsWelcomeAndNothingElse() {
        try (FakeNode node = connect("1.9.3", FakeNode.FEATURES)) {
            Welcome welcome = node.expect(Welcome.class);
            assertEquals(VersionState.DISCONTINUED, welcome.version().status());
            assertEquals(LocalDate.parse("2026-12-01"), welcome.version().since());
            assertEquals("2.2.0", welcome.version().updateTo());
            assertTrue(welcome.version().message().startsWith("Companies 1"));

            node.send(new CreateSession("c", 1, new PlayerRef(PLAYER, "Steve"), UUID.randomUUID()));
            assertEquals(ErrorCode.DISCONTINUED, node.expect(ErrorMessage.class).code());
            node.send(new Ping("p", 1, CLOCK.instant()));
            assertEquals("p", node.expect(Pong.class).id());
        }
    }

    @Test
    void aDeprecatedVersionIsWarnedWithTheDeprecatedPartsItUses() {
        try (FakeNode node = connect("2.0.4", List.of(Features.MARKET, Features.ORDERS_BROWSE))) {
            Welcome welcome = node.expect(Welcome.class);
            assertEquals(VersionState.DEPRECATED, welcome.version().status());
            assertEquals(LocalDate.parse("2027-06-01"), welcome.version().discontinuedAt());
            assertEquals(1, welcome.deprecated().size());
            assertEquals(PartKind.FEATURE, welcome.deprecated().getFirst().kind());
            assertEquals("market", welcome.deprecated().getFirst().name());
            assertEquals(
                    LocalDate.parse("2027-06-01"),
                    welcome.deprecated().getFirst().removedAt());
            assertFalse(welcome.features().contains(Features.ADMIN_CONFIG));
            assertTrue(welcome.features().contains(Features.MARKET));
        }
    }

    @Test
    void onlyAdminsSeeTheDeprecatedParts() {
        try (FakeNode node = connect("2.0.4", List.of(Features.MARKET, Features.ORDERS_BROWSE))) {
            node.expect(Welcome.class);
            FakeBrowser admin = new FakeBrowser(gateway, "203.0.113.42");
            FakeBrowser player = new FakeBrowser(gateway, "203.0.113.43");
            confirm(node, admin, create(node), "true");
            confirm(node, player, create(node), null);
            JsonNode forAdmin = FakeBrowser.json(admin.get("/api/v1/me/server"));
            assertEquals("DEPRECATED", forAdmin.at("/version/status").asString());
            assertEquals("market", forAdmin.at("/deprecated/0/name").asString());
            JsonNode forPlayer = FakeBrowser.json(player.get("/api/v1/me/server"));
            assertEquals("DEPRECATED", forPlayer.at("/version/status").asString());
            assertEquals(0, forPlayer.path("deprecated").size());
        }
    }

    private static void confirm(FakeNode node, FakeBrowser browser, SessionCreated created, @Nullable String admin) {
        CompletableFuture<HttpResponse<String>> response =
                browser.postAsync("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"" + created.code() + "\"}");
        SessionOpened opened = node.expect(SessionOpened.class);
        node.sendRaw("{\"type\":\"session.confirmed\",\"id\":\"" + opened.id() + "\",\"v\":1,\"session\":\""
                + opened.session() + "\"" + (admin == null ? "" : ",\"admin\":" + admin) + "}");
        assertEquals(204, response.join().statusCode());
    }

    @Test
    void codesExpireAfterFiveMinutes() {
        try (FakeNode node = connect("2.2.0", FakeNode.FEATURES)) {
            node.expect(Welcome.class);
            SessionCreated created = create(node);
            CLOCK.advance(Duration.ofMinutes(6));
            keepAlive(node);
            gateway.app().sweep();
            SessionClosed closed = node.expect(SessionClosed.class);
            assertEquals(created.session(), closed.session());
            assertEquals(CloseReason.EXPIRED, closed.reason());
        }
    }

    @Test
    void idleSessionsEnd() {
        try (FakeNode node = connect("2.2.0", FakeNode.FEATURES)) {
            node.expect(Welcome.class);
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.40");
            login(node, browser, create(node));
            CLOCK.advance(Duration.ofMinutes(59));
            assertEquals(200, browser.get("/api/v1/me/server").statusCode());
            CLOCK.advance(Duration.ofMinutes(61));
            keepAlive(node);
            gateway.app().sweep();
            assertEquals(CloseReason.IDLE, node.expect(SessionClosed.class).reason());
            assertEquals(401, browser.get("/api/v1/me/server").statusCode());
        }
    }

    @Test
    void theTimeTheGatewayWasDownDoesNotCountAsIdle() {
        try (FakeNode node = connect("2.2.0", FakeNode.FEATURES)) {
            node.expect(Welcome.class);
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.44");
            login(node, browser, create(node));
            gateway.app().sweep();
            CLOCK.advance(Duration.ofMinutes(90));
            gateway.app().resume();
            keepAlive(node);
            gateway.app().sweep();
            assertEquals(200, browser.get("/api/v1/me/server").statusCode());
        }
    }

    @Test
    void sessionsSurviveTheServerUntilTheGracePeriodEnds() {
        FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.41");
        try (FakeNode node = connect("2.2.0", FakeNode.FEATURES)) {
            node.expect(Welcome.class);
            login(node, browser, create(node));
        }
        HostedGatewayTest.assertEventually(() -> !FakeBrowser.json(browser.get("/api/v1/me/server"))
                .path("online")
                .asBoolean());
        CLOCK.advance(Duration.ofMinutes(5));
        gateway.app().sweep();
        assertEquals(200, browser.get("/api/v1/me/server").statusCode());
        CLOCK.advance(Duration.ofMinutes(6));
        gateway.app().sweep();
        assertEquals(401, browser.get("/api/v1/me/server").statusCode());
    }

    @Test
    void aServerThatComesBackListsItsSessionsAndBothSidesForgetTheRest() {
        FakeBrowser kept = new FakeBrowser(gateway, "203.0.113.42");
        FakeBrowser dropped = new FakeBrowser(gateway, "203.0.113.43");
        SessionCreated keep;
        try (FakeNode node = connect("2.2.0", FakeNode.FEATURES)) {
            node.expect(Welcome.class);
            keep = create(node);
            login(node, kept, keep);
            login(node, dropped, create(node));
        }
        CLOCK.advance(Duration.ofMinutes(2));
        try (FakeNode node = connect("2.2.0", FakeNode.FEATURES)) {
            node.expect(Welcome.class);
            node.send(new SessionsOpen(
                    null,
                    1,
                    List.of(
                            new SessionsOpen.Entry(keep.session(), PLAYER),
                            new SessionsOpen.Entry("s-ended-here", PLAYER))));
            Message answer = node.next();
            while (answer instanceof Unsubscribe) answer = node.next();
            SessionClosed closed = assertInstanceOf(SessionClosed.class, answer);
            assertEquals("s-ended-here", closed.session());
            assertEquals(CloseReason.EXPIRED, closed.reason());
            assertEquals(401, dropped.get("/api/v1/me/server").statusCode());
            assertEquals(200, kept.get("/api/v1/me/server").statusCode());
        }
    }

    private static SessionCreated create(FakeNode node) {
        node.send(new CreateSession("c", 1, new PlayerRef(PLAYER, "Steve"), UUID.randomUUID()));
        return node.expect(SessionCreated.class);
    }

    private static void login(FakeNode node, FakeBrowser browser, SessionCreated created) {
        HostedGatewayTest.login(node, browser, created);
    }

    private static void keepAlive(FakeNode node) {
        node.send(new Ping("alive", 1, CLOCK.instant()));
        node.expect(Pong.class);
    }
}
