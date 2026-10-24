/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.Features;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import de.eldoria.companies.web.api.handshake.Branding;
import de.eldoria.companies.web.api.handshake.BrandingUpdate;
import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.NodeRole;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.link.Ping;
import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.companies.web.api.session.CreateSession;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.session.SessionClosed;
import de.eldoria.companies.web.api.session.SessionConfirmed;
import de.eldoria.companies.web.api.session.SessionCreated;
import de.eldoria.companies.web.api.session.SessionOpened;
import de.eldoria.companies.web.api.session.SessionsRevoked;
import de.eldoria.companies.web.api.snapshot.SnapshotRequest;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.api.update.PatchOp;
import de.eldoria.companies.web.api.update.Subscribe;
import de.eldoria.companies.web.gateway.node.Icons;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hosted gateway with a fake Lyna: handshake, login, snapshot, patches, reads and the end of sessions, driven by
 * a fake plugin node and a fake browser.
 */
class HostedGatewayTest {
    private static final UUID PLAYER = UUID.fromString("a3b1c2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    private static final String PLUGIN = "2.2.0";
    private static FakeLyna lyna;
    private static TestGateway gateway;
    private static Map<String, String> env;
    private int keys;

    @BeforeAll
    static void start() {
        lyna = new FakeLyna();
        env = TestDatabase.env("hosted");
        env.put("GATEWAY_MODE", "hosted");
        env.put("LYNA_URL", lyna.url());
        env.put("DASHBOARD_GATEWAYSECRET", FakeLyna.SECRET);
        env.put("LOGINS_PER_ADDRESS", "10");
        gateway = TestGateway.start(env);
    }

    @AfterAll
    static void stop() {
        gateway.close();
        lyna.close();
    }

    private String key() {
        String key = "lyd_test" + ++keys + "_" + UUID.randomUUID();
        lyna.valid(key, "key-" + UUID.randomUUID(), 2, "BOUGHT");
        return key;
    }

    private static FakeNode node(String key, UUID uid) {
        return FakeNode.welcomed(gateway, FakeNode.hello(KeyKind.DASHBOARD_KEY, key, uid, PLUGIN));
    }

    private static SessionCreated create(FakeNode node, UUID uid) {
        node.send(new CreateSession("c", 1, new PlayerRef(PLAYER, "Steve"), uid));
        return node.expect(SessionCreated.class);
    }

    private static HttpResponse<String> login(FakeNode node, FakeBrowser browser, SessionCreated created) {
        CompletableFuture<HttpResponse<String>> response =
                browser.postAsync("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"" + created.code() + "\"}");
        SessionOpened opened = node.expect(SessionOpened.class);
        node.send(new SessionConfirmed(opened.id(), 1, opened.session()));
        return response.join();
    }

    private static JsonNode snapshot(FakeNode node, FakeBrowser browser, UUID uid, long seq) {
        CompletableFuture<HttpResponse<String>> response = browser.getAsync("/api/v1/me/snapshot");
        SnapshotRequest request = node.expect(SnapshotRequest.class);
        node.sendRaw(Recorded.snapshot(request.id(), request.session(), uid, seq));
        HttpResponse<String> answer = response.join();
        assertEquals(200, answer.statusCode(), answer.body());
        return FakeBrowser.json(answer);
    }

    @Test
    void welcomesAVerifiedKey() {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(
                    KeyKind.DASHBOARD_KEY,
                    key(),
                    UUID.randomUUID(),
                    NodeRole.PRIMARY,
                    PLUGIN,
                    List.of(Features.ORDERS_BROWSE, "feature.from.the.future"),
                    null));
            Welcome welcome = node.expect(Welcome.class);
            assertEquals("1", welcome.id());
            assertEquals(1, welcome.protocol());
            assertEquals(GatewayKind.HOSTED, welcome.gateway());
            assertEquals(VersionState.SUPPORTED, welcome.version().status());
            assertTrue(welcome.features().containsAll(Features.ALL));
            assertTrue(welcome.deprecated().isEmpty());
            assertFalse(welcome.iconCached());
        }
    }

    @Test
    void refusesAnUnknownKey() {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.DASHBOARD_KEY, "lyd_unknown", UUID.randomUUID(), PLUGIN));
            ErrorMessage error = node.expect(ErrorMessage.class);
            assertEquals(ErrorCode.UNAUTHORIZED, error.code());
            assertEquals("UNKNOWN", error.message());
            assertEquals(4401, node.awaitClose());
        }
    }

    @Test
    void refusesARevokedKeyWithLynasReason() {
        lyna.invalid("lyd_revoked", "EXPIRED");
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.DASHBOARD_KEY, "lyd_revoked", UUID.randomUUID(), PLUGIN));
            assertEquals("EXPIRED", node.expect(ErrorMessage.class).message());
            assertEquals(4401, node.awaitClose());
        }
    }

    @Test
    void refusesAKeyOfAnotherProduct() {
        lyna.valid("lyd_bloodnight", "key-other", 5, "BOUGHT");
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.DASHBOARD_KEY, "lyd_bloodnight", UUID.randomUUID(), PLUGIN));
            assertEquals(ErrorCode.UNAUTHORIZED, node.expect(ErrorMessage.class).code());
            assertEquals(4401, node.awaitClose());
        }
    }

    @Test
    void refusesAServerTokenWithoutAskingLyna() {
        int calls = lyna.calls();
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.SERVER_TOKEN, "cst_whatever", UUID.randomUUID(), PLUGIN));
            assertEquals(ErrorCode.UNAUTHORIZED, node.expect(ErrorMessage.class).code());
        }
        assertEquals(calls, lyna.calls());
    }

    @Test
    void verifiesEveryConnectionAndRemembersAnswersForOutages() {
        String key = key();
        int calls = lyna.calls();
        node(key, UUID.randomUUID()).close();
        node(key, UUID.randomUUID()).close();
        assertEquals(calls + 2, lyna.calls());
        lyna.down(key);
        node(key, UUID.randomUUID()).close();
        lyna.down("lyd_never_seen");
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.DASHBOARD_KEY, "lyd_never_seen", UUID.randomUUID(), PLUGIN));
            assertEquals(ErrorCode.INTERNAL, node.expect(ErrorMessage.class).code());
        }
    }

    @Test
    void refusesMessagesBeforeTheHello() {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(new Ping("p", 1, Instant.now()));
            assertEquals(ErrorCode.UNAUTHORIZED, node.expect(ErrorMessage.class).code());
        }
    }

    @Test
    void logsInServesTheSnapshotAndAppliesPatches() {
        UUID uid = UUID.randomUUID();
        Branding branding = new Branding("Stonecutter Isles", null, null);
        try (FakeNode node = FakeNode.welcomed(
                gateway,
                FakeNode.hello(
                        KeyKind.DASHBOARD_KEY, key(), uid, NodeRole.PRIMARY, PLUGIN, FakeNode.FEATURES, branding))) {
            SessionCreated created = create(node, uid);
            assertTrue(created.shortId().matches("[" + Secrets.SHORT_ID_ALPHABET + "]{6}"));
            assertTrue(created.code().matches("[" + Secrets.CODE_ALPHABET + "]{6}"));
            assertEquals("https://dash.test/" + created.shortId(), created.link());

            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.10");
            HttpResponse<String> pending =
                    browser.get("/api/v1/s/" + created.shortId().toLowerCase());
            assertEquals(200, pending.statusCode(), pending.body());
            assertEquals(
                    "no-store", pending.headers().firstValue("Cache-Control").orElse(""));
            assertEquals(
                    "Stonecutter Isles",
                    FakeBrowser.json(pending).at("/server/name").asString());
            assertTrue(FakeBrowser.json(pending).at("/server/online").asBoolean());

            HttpResponse<String> wrong =
                    browser.post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"AAAAAA\"}");
            assertEquals(401, wrong.statusCode());
            assertEquals(4, FakeBrowser.json(wrong).path("attemptsLeft").asInt());

            CompletableFuture<HttpResponse<String>> response = browser.postAsync(
                    "/api/v1/s/" + created.shortId() + "/login",
                    "{\"code\":\"" + created.code().substring(0, 3) + " "
                            + created.code().substring(3).toLowerCase() + "\"}");
            SessionOpened opened = node.expect(SessionOpened.class);
            assertEquals(created.session(), opened.session());
            assertEquals(PLAYER, opened.player());
            assertEquals("Firefox on Android", opened.device());
            node.send(new SessionConfirmed(opened.id(), 1, opened.session()));
            HttpResponse<String> loggedIn = response.join();
            assertEquals(200, loggedIn.statusCode(), loggedIn.body());
            String setCookie = loggedIn.headers().firstValue("Set-Cookie").orElseThrow();
            assertTrue(setCookie.contains("HttpOnly"));
            assertTrue(setCookie.contains("SameSite=Strict"));
            assertTrue(setCookie.contains("Secure"));
            assertNotNull(browser.cookie());

            JsonNode me = FakeBrowser.json(browser.get("/api/v1/me"));
            assertEquals("Steve", me.at("/player/name").asString());
            assertTrue(
                    me.path("features").valueStream().anyMatch(f -> f.asString().equals("orders.browse")));
            assertEquals(404, browser.get("/api/v1/s/" + created.shortId()).statusCode());

            try (FakeBrowser.Stream stream = browser.stream()) {
                assertEquals("server", stream.next().path("type").asString());

                JsonNode first = snapshot(node, browser, uid, 10);
                assertEquals(1, first.path("revision").asLong());
                assertEquals(3, first.at("/snapshot/player/unreadInbox").asInt());

                node.send(new Patch(
                        null,
                        1,
                        List.of(created.session()),
                        uid,
                        11,
                        "player.unreadInbox",
                        PatchOp.REPLACE,
                        null,
                        JsonNodeFactory.instance.numberNode(5)));
                JsonNode patch = stream.next();
                assertEquals("patch", patch.path("type").asString());
                assertEquals(2, patch.path("revision").asLong());
                assertEquals(5, patch.path("value").asInt());

                JsonNode cached = FakeBrowser.json(browser.get("/api/v1/me/snapshot"));
                assertEquals(2, cached.path("revision").asLong());
                assertEquals(5, cached.at("/snapshot/player/unreadInbox").asInt());
                node.assertQuiet();

                node.send(new Patch(
                        null,
                        1,
                        List.of(created.session()),
                        uid,
                        10,
                        "player.unreadInbox",
                        PatchOp.REPLACE,
                        null,
                        JsonNodeFactory.instance.numberNode(99)));
                node.send(new Patch(
                        null,
                        1,
                        List.of(created.session()),
                        uid,
                        12,
                        "company.members",
                        PatchOp.UPSERT,
                        "11111111-2222-4333-8444-555555555555",
                        ((ObjectNode) Recorded.message("patch.json").path("value"))
                                .put("uuid", "11111111-2222-4333-8444-555555555555")
                                .put("name", "Kestrel_")));
                assertEquals(3, stream.next().path("revision").asLong());
                JsonNode members = FakeBrowser.json(browser.get("/api/v1/me/members?search=kes"));
                assertEquals(1, members.path("total").asInt());
                assertEquals("Kestrel_", members.at("/entries/0/name").asString());
                assertEquals(
                        3,
                        FakeBrowser.json(browser.get("/api/v1/me/members"))
                                .path("total")
                                .asInt());
                assertEquals(5, cachedInbox(browser));

                node.send(new Patch(
                        null,
                        1,
                        List.of(created.session()),
                        uid,
                        15,
                        "player.unreadInbox",
                        PatchOp.REPLACE,
                        null,
                        JsonNodeFactory.instance.numberNode(7)));
                SnapshotRequest reload = node.expect(SnapshotRequest.class);
                node.sendRaw(Recorded.snapshot(reload.id(), reload.session(), uid, 14));
                JsonNode reloaded = stream.next();
                assertEquals("reload", reloaded.path("type").asString());
                assertEquals(7, cachedInbox(browser));
            }
        }
    }

    private static int cachedInbox(FakeBrowser browser) {
        return FakeBrowser.json(browser.get("/api/v1/me/snapshot"))
                .at("/snapshot/player/unreadInbox")
                .asInt();
    }

    @Test
    void forwardsReadRequestsWithinTheNodesLimits() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.11");
            assertEquals(200, login(node, browser, create(node, uid)).statusCode());

            CompletableFuture<HttpResponse<String>> search = browser.postAsync(
                    "/api/v1/me/actions/orders.browse.search", "{\"query\":\"diamond\",\"page\":0,\"size\":500}");
            ActionRequest request = node.expect(ActionRequest.class);
            assertEquals("orders.browse.search", request.name());
            assertEquals(PLAYER, request.player());
            assertEquals(1, request.args().path("page").asInt());
            assertEquals(50, request.args().path("size").asInt());
            node.send(new ActionResult(
                    request.id(),
                    1,
                    true,
                    null,
                    null,
                    Recorded.message("action.result.data.json").path("data")));
            HttpResponse<String> answer = search.join();
            assertEquals(200, answer.statusCode(), answer.body());
            assertTrue(FakeBrowser.json(answer).path("ok").asBoolean());
            assertEquals(1, FakeBrowser.json(answer).at("/data/pages").asInt());

            HttpResponse<String> again = browser.post(
                    "/api/v1/me/actions/orders.browse.search", "{\"query\":\"diamond\",\"page\":0,\"size\":500}");
            assertEquals(200, again.statusCode());
            node.assertQuiet();

            assertEquals(
                    403,
                    browser.post("/api/v1/me/actions/orders.accept", "{\"order\":1}")
                            .statusCode());
            assertEquals(
                    404, browser.post("/api/v1/me/actions/market.top", "{}").statusCode());
            assertEquals(
                    401,
                    new FakeBrowser(gateway, "203.0.113.12")
                            .post("/api/v1/me/actions/inbox.page", "{}")
                            .statusCode());
        }
    }

    @Test
    void logoutEndsTheSessionEverywhere() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.13");
            SessionCreated created = create(node, uid);
            login(node, browser, created);
            try (FakeBrowser.Stream stream = browser.stream()) {
                stream.next();
                String cookie = browser.cookie();
                assertEquals(204, browser.post("/api/v1/me/logout", "").statusCode());
                SessionClosed closed = node.expect(SessionClosed.class);
                assertEquals(created.session(), closed.session());
                assertEquals(CloseReason.LOGOUT, closed.reason());
                JsonNode event = stream.next();
                assertEquals("closed", event.path("type").asString());
                assertEquals("LOGOUT", event.path("reason").asString());
                assertEquals(4401, stream.awaitClose());
                assertNull(browser.cookie());
                browser.cookie(cookie);
                assertEquals(401, browser.get("/api/v1/me").statusCode());
            }
        }
    }

    @Test
    void thePluginRevokesSessions() throws InterruptedException {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser phone = new FakeBrowser(gateway, "203.0.113.14");
            FakeBrowser desktop = new FakeBrowser(gateway, "203.0.113.15");
            login(node, phone, create(node, uid));
            login(node, desktop, create(node, uid));
            node.send(new SessionsRevoked(null, 1, PLAYER, CloseReason.REVOKED));
            assertEventually(() -> phone.get("/api/v1/me").statusCode() == 401);
            assertEquals(401, desktop.get("/api/v1/me").statusCode());
        }
    }

    @Test
    void wrongCodesEndThePendingSession() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            SessionCreated created = create(node, uid);
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.16");
            for (int left = 4; left >= 0; left--) {
                HttpResponse<String> wrong =
                        browser.post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"ZZZZZZ\"}");
                assertEquals(401, wrong.statusCode());
                assertEquals(left, FakeBrowser.json(wrong).path("attemptsLeft").asInt());
            }
            SessionClosed closed = node.expect(SessionClosed.class);
            assertEquals(CloseReason.EXPIRED, closed.reason());
            assertEquals(404, browser.get("/api/v1/s/" + created.shortId()).statusCode());
            assertEquals(
                    404,
                    browser.post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"" + created.code() + "\"}")
                            .statusCode());
        }
    }

    @Test
    void limitsLoginAttemptsPerAddress() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.17");
            int refused = 0;
            for (int i = 0; i < 3; i++) {
                SessionCreated created = create(node, uid);
                for (int attempt = 0; attempt < 4; attempt++) {
                    int status = browser.post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"ZZZZZZ\"}")
                            .statusCode();
                    if (status == 429) refused++;
                }
            }
            assertEquals(2, refused);
            assertEquals(
                    401,
                    new FakeBrowser(gateway, "203.0.113.18")
                            .post("/api/v1/s/" + create(node, uid).shortId() + "/login", "{\"code\":\"ZZZZZZ\"}")
                            .statusCode());
        }
    }

    @Test
    void storesIconsByHashAndServesThem() throws IOException {
        byte[] png = png();
        String hash = Icons.sha256(png);
        String icon = Base64.getEncoder().encodeToString(png);
        String key = key();
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(
                    KeyKind.DASHBOARD_KEY,
                    key,
                    UUID.randomUUID(),
                    NodeRole.PRIMARY,
                    PLUGIN,
                    FakeNode.FEATURES,
                    new Branding("Iconic", null, hash)));
            assertFalse(node.expect(Welcome.class).iconCached());
            node.send(new BrandingUpdate(null, 1, new Branding("Iconic", icon, hash)));
            assertEventually(() -> gateway(hash).statusCode() == 200);
        }
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(
                    KeyKind.DASHBOARD_KEY,
                    key,
                    UUID.randomUUID(),
                    NodeRole.PRIMARY,
                    PLUGIN,
                    FakeNode.FEATURES,
                    new Branding("Iconic", null, hash)));
            assertTrue(node.expect(Welcome.class).iconCached());
        }
        HttpResponse<String> served = gateway(hash);
        assertEquals("image/png", served.headers().firstValue("Content-Type").orElse(""));
        assertTrue(served.headers().firstValue("Cache-Control").orElse("").contains("immutable"));
        assertEquals(404, gateway("0".repeat(64)).statusCode());

        String other = Base64.getEncoder().encodeToString(png());
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(
                    KeyKind.DASHBOARD_KEY,
                    key(),
                    UUID.randomUUID(),
                    NodeRole.PRIMARY,
                    PLUGIN,
                    FakeNode.FEATURES,
                    new Branding("Liar", other, "a".repeat(64))));
            assertFalse(node.expect(Welcome.class).iconCached());
        }
    }

    @Test
    void routesRequestsToThePrimaryFirst() {
        String key = key();
        UUID primaryId = UUID.randomUUID();
        UUID secondaryId = UUID.randomUUID();
        try (FakeNode secondary = FakeNode.welcomed(
                        gateway,
                        FakeNode.hello(
                                KeyKind.DASHBOARD_KEY,
                                key,
                                secondaryId,
                                NodeRole.SECONDARY,
                                PLUGIN,
                                FakeNode.FEATURES,
                                null));
                FakeNode primary = node(key, primaryId)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.19");
            login(secondary, browser, create(secondary, secondaryId));
            CompletableFuture<HttpResponse<String>> response = browser.getAsync("/api/v1/me/snapshot");
            SnapshotRequest request = primary.expect(SnapshotRequest.class);
            secondary.expect(Subscribe.class);
            primary.sendRaw(Recorded.snapshot(request.id(), request.session(), primaryId, 1));
            assertEquals(200, response.join().statusCode());
        }
    }

    @Test
    void keepsLoginsAcrossARestart() {
        UUID uid = UUID.randomUUID();
        FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.20");
        try (FakeNode node = node(key(), uid)) {
            login(node, browser, create(node, uid));
        }
        try (TestGateway restarted = TestGateway.start(env)) {
            FakeBrowser same = new FakeBrowser(restarted, "203.0.113.20");
            same.cookie(browser.cookie());
            HttpResponse<String> me = same.get("/api/v1/me");
            assertEquals(200, me.statusCode(), me.body());
            assertFalse(FakeBrowser.json(me).at("/server/online").asBoolean());
            assertEquals(503, same.get("/api/v1/me/snapshot").statusCode());
        }
    }

    private static HttpResponse<String> gateway(String hash) {
        return new FakeBrowser(gateway, "203.0.113.30").get("/api/v1/icons/" + hash);
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(3, 4, (int) (Math.random() * Integer.MAX_VALUE));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    static void assertEventually(BooleanSupplier condition) {
        long until = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < until) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(condition.getAsBoolean(), "The condition did not hold in time");
    }
}
