/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
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
import de.eldoria.companies.web.api.link.Pong;
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
import de.eldoria.companies.web.api.update.Unsubscribe;
import de.eldoria.companies.web.gateway.node.Icons;
import de.eldoria.companies.web.gateway.node.NodeHub;
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
import java.util.ArrayList;
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

    static SessionCreated create(FakeNode node, UUID uid) {
        node.send(new CreateSession("c", 1, new PlayerRef(PLAYER, "Steve"), uid));
        return node.expect(SessionCreated.class);
    }

    static HttpResponse<String> login(FakeNode node, FakeBrowser browser, SessionCreated created) {
        CompletableFuture<HttpResponse<String>> response =
                browser.postAsync("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"" + created.code() + "\"}");
        SessionOpened opened = node.expect(SessionOpened.class);
        node.send(new SessionConfirmed(opened.id(), 1, opened.session()));
        HttpResponse<String> answer = response.join();
        assertEquals(204, answer.statusCode(), answer.body());
        return answer;
    }

    private static JsonNode snapshot(FakeNode node, FakeBrowser browser, UUID uid, long seq) {
        CompletableFuture<HttpResponse<String>> response = browser.getAsync("/api/v1/me/snapshot");
        SnapshotRequest request = node.expect(SnapshotRequest.class);
        node.sendRaw(Recorded.snapshot(request.id(), request.session(), uid, seq));
        HttpResponse<String> answer = response.join();
        assertEquals(200, answer.statusCode(), answer.body());
        return FakeBrowser.json(answer);
    }

    private static String error(HttpResponse<String> response) {
        return FakeBrowser.json(response).path("error").asString();
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
    void reportsItsHealth() {
        HttpResponse<String> health = new FakeBrowser(gateway, "203.0.113.31").get("/api/v1/health");
        assertEquals(200, health.statusCode());
        assertEquals("up", FakeBrowser.json(health).path("status").asString());
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
            assertEquals(NodeHub.CLOSE_INVALID, node.awaitClose());
        }
    }

    @Test
    void limitsMessagesBeforeTheWelcome() {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.sendRefusable("{\"type\":\"hello\",\"pad\":\"" + "x".repeat(200 * 1024) + "\"}");
            int code = node.awaitClose();
            assertTrue(code == 1009 || code == -1, "Closed with " + code);
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
            JsonNode waiting = FakeBrowser.json(pending);
            assertEquals("Stonecutter Isles", waiting.at("/server/name").asString());
            assertEquals(5, waiting.path("attemptsLeft").asInt());
            assertEquals("SUPPORTED", waiting.at("/version/status").asString());
            assertFalse(waiting.has("turnstileSiteKey"));

            HttpResponse<String> wrong =
                    browser.post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"AAAAAA\"}");
            assertEquals(401, wrong.statusCode());
            assertEquals("WRONG_CODE", error(wrong));
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
            assertEquals(204, loggedIn.statusCode(), loggedIn.body());
            String setCookie = loggedIn.headers().firstValue("Set-Cookie").orElseThrow();
            assertTrue(setCookie.contains("HttpOnly"));
            assertTrue(setCookie.contains("SameSite=Strict"));
            assertTrue(setCookie.contains("Secure"));
            assertTrue(setCookie.startsWith("__Host-companies_session="));
            assertTrue(setCookie.contains("Path=/"));
            assertNotNull(browser.cookie());

            JsonNode server = FakeBrowser.json(browser.get("/api/v1/me/server"));
            assertEquals("Stonecutter Isles", server.at("/server/name").asString());
            assertTrue(server.path("features").valueStream().anyMatch(f -> f.asString()
                    .equals("orders.browse")));
            assertEquals(50, server.at("/limits/maxPageSize").asInt());
            assertEquals("SUPPORTED", server.at("/version/status").asString());
            assertTrue(server.path("online").asBoolean());
            assertEquals(404, browser.get("/api/v1/s/" + created.shortId()).statusCode());

            try (FakeBrowser.Stream stream = browser.stream()) {
                JsonNode hello = stream.next();
                assertEquals("server", hello.path("type").asString());
                assertEquals(
                        "Stonecutter Isles", hello.at("/server/server/name").asString());

                JsonNode first = snapshot(node, browser, uid, 10);
                assertEquals("snapshot", first.path("type").asString());
                assertEquals(3, first.at("/player/unreadInbox").asInt());

                node.send(new Patch(
                        null,
                        1,
                        List.of(created.session(), "someone-else"),
                        uid,
                        11,
                        "player.unreadInbox",
                        PatchOp.REPLACE,
                        null,
                        JsonNodeFactory.instance.numberNode(5)));
                JsonNode patch = stream.next();
                assertEquals("patch", patch.path("type").asString());
                assertEquals(5, patch.path("value").asInt());
                assertEquals(1, patch.path("sessions").size());
                assertEquals(created.session(), patch.at("/sessions/0").asString());

                assertEquals(5, cachedInbox(browser));
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
                assertEquals("company.members", stream.next().path("path").asString());
                JsonNode cached = FakeBrowser.json(browser.get("/api/v1/me/snapshot"));
                assertEquals(3, cached.at("/company/members").size());
                assertEquals("Kestrel_", cached.at("/company/members/2/name").asString());
                assertEquals(5, cached.at("/player/unreadInbox").asInt());

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
                assertEquals("resync", stream.next().path("type").asString());
                assertEquals(7, cachedInbox(browser));

                stream.ping();
                JsonNode pong = stream.next();
                assertEquals("pong", pong.path("type").asString());
                assertEquals("b-1", pong.path("id").asString());
            }
        }
    }

    private static int cachedInbox(FakeBrowser browser) {
        return FakeBrowser.json(browser.get("/api/v1/me/snapshot"))
                .at("/player/unreadInbox")
                .asInt();
    }

    @Test
    void passesOnWhatThisVersionDoesNotKnow() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.21");
            SessionCreated created = create(node, uid);
            login(node, browser, created);
            try (FakeBrowser.Stream stream = browser.stream()) {
                stream.next();
                CompletableFuture<HttpResponse<String>> response = browser.getAsync("/api/v1/me/snapshot");
                SnapshotRequest request = node.expect(SnapshotRequest.class);
                ObjectNode snapshot =
                        (ObjectNode) FakeBrowser.json(Recorded.snapshot(request.id(), request.session(), uid, 1));
                snapshot.putObject("market").put("trend", "up");
                ((ObjectNode) snapshot.path("player")).put("title", "Baron");
                node.sendRaw(snapshot.toString());
                JsonNode served = FakeBrowser.json(response.join());
                assertEquals("up", served.at("/market/trend").asString());
                assertEquals("Baron", served.at("/player/title").asString());

                node.send(new Patch(
                        null,
                        1,
                        List.of(created.session()),
                        uid,
                        2,
                        "auction.house.open",
                        PatchOp.REPLACE,
                        null,
                        JsonNodeFactory.instance.booleanNode(true)));
                JsonNode patch = stream.next();
                assertEquals("auction.house.open", patch.path("path").asString());
                node.assertQuiet();
                node.send(new Patch(
                        null,
                        1,
                        List.of(created.session()),
                        uid,
                        3,
                        "market.trend",
                        PatchOp.REPLACE,
                        null,
                        JsonNodeFactory.instance.stringNode("down")));
                stream.next();
                assertEquals(
                        "down",
                        FakeBrowser.json(browser.get("/api/v1/me/snapshot"))
                                .at("/market/trend")
                                .asString());
            }
        }
    }

    @Test
    void forwardsReadActionsWithinTheNodesLimits() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.11");
            login(node, browser, create(node, uid));

            String search = "{\"name\":\"orders.browse.search\",\"args\":{\"name\":\"diamond\",\"page\":0,"
                    + "\"pageSize\":500,\"sort\":\"PRICE\"}}";
            CompletableFuture<HttpResponse<String>> answer = browser.postAsync("/api/v1/me/actions", search);
            ActionRequest request = node.expect(ActionRequest.class);
            assertEquals("orders.browse.search", request.name());
            assertEquals(PLAYER, request.player());
            assertEquals(1, request.args().path("page").asInt());
            assertEquals(50, request.args().path("pageSize").asInt());
            assertEquals("PRICE", request.args().path("sort").asString());
            node.send(new ActionResult(
                    request.id(),
                    1,
                    true,
                    null,
                    null,
                    Recorded.message("action.result.data.json").path("data")));
            HttpResponse<String> result = answer.join();
            assertEquals(200, result.statusCode(), result.body());
            assertEquals("action.result", FakeBrowser.json(result).path("type").asString());
            assertTrue(FakeBrowser.json(result).path("ok").asBoolean());
            assertEquals(1, FakeBrowser.json(result).at("/data/pages").asInt());

            assertEquals(200, browser.post("/api/v1/me/actions", search).statusCode());
            node.assertQuiet();

            HttpResponse<String> write =
                    browser.post("/api/v1/me/actions", "{\"name\":\"orders.accept\",\"args\":{\"order\":1}}");
            assertEquals(403, write.statusCode());
            assertEquals("UNAUTHORIZED", error(write));
            assertEquals(
                    403,
                    browser.post("/api/v1/me/actions", "{\"name\":\"market.top\",\"args\":{\"page\":1}}")
                            .statusCode());
            HttpResponse<String> stranger = new FakeBrowser(gateway, "203.0.113.12")
                    .post("/api/v1/me/actions", "{\"name\":\"inbox.page\",\"args\":{\"page\":1}}");
            assertEquals(401, stranger.statusCode());
            assertEquals("UNAUTHORIZED", error(stranger));
        }
    }

    @Test
    void forwardsWritesUncachedAndDropsTheCachedReads() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.71");
            login(node, browser, create(node, uid));
            assertEquals(1, readFromNode(node, browser, INBOX_PAGE, 1));
            assertEquals(1, readCached(browser, INBOX_PAGE));

            String readAll = "{\"name\":\"inbox.read-all\",\"args\":{}}";
            CompletableFuture<HttpResponse<String>> first = browser.postAsync("/api/v1/me/actions", readAll);
            ActionRequest request = node.expect(ActionRequest.class);
            assertEquals("inbox.read-all", request.name());
            answer(node, request, 2);
            assertEquals(2, content(first.join()));

            CompletableFuture<HttpResponse<String>> second = browser.postAsync("/api/v1/me/actions", readAll);
            answer(node, node.expect(ActionRequest.class), 3);
            assertEquals(3, content(second.join()));

            assertEquals(4, readFromNode(node, browser, INBOX_PAGE, 4));
        }
    }

    private static final String TREASURY_LOG = "{\"name\":\"company.treasury.log\",\"args\":{\"page\":1}}";
    private static final String INBOX_PAGE = "{\"name\":\"inbox.page\",\"args\":{\"page\":1}}";
    private static final String RANKINGS_PAGE = "{\"name\":\"rankings.page\",\"args\":{\"page\":1}}";

    @Test
    void aPatchDropsTheCachedReadsOfItsSessionOnly() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = treasuryNode(uid)) {
            FakeBrowser patched = new FakeBrowser(gateway, "203.0.113.63");
            SessionCreated created = create(node, uid);
            login(node, patched, created);
            FakeBrowser other = new FakeBrowser(gateway, "203.0.113.64");
            login(node, other, create(node, uid));

            try (FakeBrowser.Stream stream = patched.stream()) {
                stream.next();
                snapshot(node, patched, uid, 1);
                assertEquals(1, readFromNode(node, patched, TREASURY_LOG, 1));
                assertEquals(2, readFromNode(node, other, TREASURY_LOG, 2));
                assertEquals(1, readCached(patched, TREASURY_LOG));
                assertEquals(2, readCached(other, TREASURY_LOG));
                node.assertQuiet();

                patch(node, created, uid, 2, "company.treasury.balance", 2600);
                assertEquals(
                        "company.treasury.balance", stream.next().path("path").asString());

                assertEquals(2, readCached(other, TREASURY_LOG));
                node.assertQuiet();
                assertEquals(3, readFromNode(node, patched, TREASURY_LOG, 3));
            }
        }
    }

    @Test
    void anAnswerArrivingAfterAPatchIsNotServed() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = treasuryNode(uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.65");
            SessionCreated created = create(node, uid);
            login(node, browser, created);

            try (FakeBrowser.Stream stream = browser.stream()) {
                stream.next();
                snapshot(node, browser, uid, 1);
                CompletableFuture<HttpResponse<String>> answer = browser.postAsync("/api/v1/me/actions", TREASURY_LOG);
                ActionRequest held = node.expect(ActionRequest.class);

                patch(node, created, uid, 2, "company.treasury.balance", 2600);
                assertEquals(
                        "company.treasury.balance", stream.next().path("path").asString());
                answer(node, held, 1);
                assertEquals(1, content(answer.join()));

                assertEquals(2, readFromNode(node, browser, TREASURY_LOG, 2));
            }
        }
    }

    @Test
    void aPatchBufferedDuringTheFirstLoadDropsTheCachedReads() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = treasuryNode(uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.69");
            SessionCreated created = create(node, uid);
            login(node, browser, created);

            CompletableFuture<HttpResponse<String>> loading = browser.getAsync("/api/v1/me/snapshot");
            SnapshotRequest request = node.expect(SnapshotRequest.class);
            assertEquals(1, readFromNode(node, browser, TREASURY_LOG, 1));
            assertEquals(2, readFromNode(node, browser, INBOX_PAGE, 2));
            patch(node, created, uid, 2, "company.treasury.balance", 2600);
            node.sendRaw(Recorded.snapshot(request.id(), request.session(), uid, 1));
            assertEquals(200, loading.join().statusCode());

            assertEquals(3, readFromNode(node, browser, TREASURY_LOG, 3));
            assertEquals(4, readFromNode(node, browser, INBOX_PAGE, 4));
        }
    }

    @Test
    void aRankingsPatchBufferedDuringTheFirstLoadKeepsOtherReads() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.70");
            SessionCreated created = create(node, uid);
            login(node, browser, created);

            CompletableFuture<HttpResponse<String>> loading = browser.getAsync("/api/v1/me/snapshot");
            SnapshotRequest request = node.expect(SnapshotRequest.class);
            assertEquals(1, readFromNode(node, browser, INBOX_PAGE, 1));
            assertEquals(2, readFromNode(node, browser, RANKINGS_PAGE, 2));
            patch(node, created, uid, 2, "rankings.pages", 5);
            node.sendRaw(Recorded.snapshot(request.id(), request.session(), uid, 1));
            assertEquals(200, loading.join().statusCode());

            assertEquals(1, readCached(browser, INBOX_PAGE));
            node.assertQuiet();
            assertEquals(3, readFromNode(node, browser, RANKINGS_PAGE, 3));
        }
    }

    @Test
    void aReloadedSnapshotDropsTheCachedReads() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.66");
            SessionCreated created = create(node, uid);
            login(node, browser, created);

            try (FakeBrowser.Stream stream = browser.stream()) {
                stream.next();
                snapshot(node, browser, uid, 1);
                assertEquals(1, readFromNode(node, browser, INBOX_PAGE, 1));

                patch(node, created, uid, 2, "market.top", 1);
                assertEquals("market.top", stream.next().path("path").asString());
                assertEquals(1, readCached(browser, INBOX_PAGE));
                patch(node, created, uid, 5, "market.top", 2);
                SnapshotRequest reload = node.expect(SnapshotRequest.class);
                node.sendRaw(Recorded.snapshot(reload.id(), reload.session(), uid, 5));
                assertEquals("resync", stream.next().path("type").asString());

                assertEquals(2, readFromNode(node, browser, INBOX_PAGE, 2));
            }
        }
    }

    @Test
    void aRankingsPatchDropsOnlyTheRankingReads() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.67");
            SessionCreated created = create(node, uid);
            login(node, browser, created);

            try (FakeBrowser.Stream stream = browser.stream()) {
                stream.next();
                snapshot(node, browser, uid, 1);
                assertEquals(1, readFromNode(node, browser, RANKINGS_PAGE, 1));
                assertEquals(2, readFromNode(node, browser, INBOX_PAGE, 2));

                patch(node, created, uid, 2, "rankings.pages", 5);
                assertEquals("rankings.pages", stream.next().path("path").asString());

                assertEquals(2, readCached(browser, INBOX_PAGE));
                node.assertQuiet();
                assertEquals(3, readFromNode(node, browser, RANKINGS_PAGE, 3));
            }
        }
    }

    @Test
    void aPatchOfAnUnknownPartKeepsTheCachedReads() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.68");
            SessionCreated created = create(node, uid);
            login(node, browser, created);

            try (FakeBrowser.Stream stream = browser.stream()) {
                stream.next();
                snapshot(node, browser, uid, 1);
                assertEquals(1, readFromNode(node, browser, INBOX_PAGE, 1));

                patch(node, created, uid, 2, "market.top", 1);
                assertEquals("market.top", stream.next().path("path").asString());

                assertEquals(1, readCached(browser, INBOX_PAGE));
                node.assertQuiet();
            }
        }
    }

    private FakeNode treasuryNode(UUID uid) {
        List<String> features = new ArrayList<>(FakeNode.FEATURES);
        features.add(Features.COMPANY_TREASURY);
        return FakeNode.welcomed(
                gateway, FakeNode.hello(KeyKind.DASHBOARD_KEY, key(), uid, NodeRole.PRIMARY, PLUGIN, features, null));
    }

    private static void patch(FakeNode node, SessionCreated created, UUID uid, long seq, String path, int value) {
        node.send(new Patch(
                null,
                1,
                List.of(created.session()),
                uid,
                seq,
                path,
                PatchOp.REPLACE,
                null,
                JsonNodeFactory.instance.numberNode(value)));
    }

    /**
     * Reads through the node, which answers with {@code content}, and returns the content the browser got.
     */
    private static int readFromNode(FakeNode node, FakeBrowser browser, String action, int content) {
        CompletableFuture<HttpResponse<String>> answer = browser.postAsync("/api/v1/me/actions", action);
        answer(node, node.expect(ActionRequest.class), content);
        return content(answer.join());
    }

    private static int readCached(FakeBrowser browser, String action) {
        return content(browser.post("/api/v1/me/actions", action));
    }

    private static void answer(FakeNode node, ActionRequest request, int content) {
        node.send(new ActionResult(
                request.id(),
                1,
                true,
                null,
                null,
                JsonNodeFactory.instance.objectNode().put("content", content)));
    }

    private static int content(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        return FakeBrowser.json(response).at("/data/content").asInt();
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
                assertEquals(created.session(), node.expect(Unsubscribe.class).session());
                JsonNode event = stream.next();
                assertEquals("session.closed", event.path("type").asString());
                assertEquals("LOGOUT", event.path("reason").asString());
                assertEquals(4401, stream.awaitClose());
                assertNull(browser.cookie());
                browser.cookie(cookie);
                assertEquals(401, browser.get("/api/v1/me/server").statusCode());
            }
        }
    }

    @Test
    void refusesRequestsFromOtherSites() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.22");
            login(node, browser, create(node, uid));
            browser.origin("https://evil.test");
            try (FakeBrowser.Stream stream = browser.stream()) {
                assertEquals(1008, stream.awaitClose());
            }
            HttpResponse<String> logout = browser.post("/api/v1/me/logout", "");
            assertEquals(403, logout.statusCode());
            assertEquals("UNAUTHORIZED", error(logout));
            assertEquals(
                    403,
                    browser.post("/api/v1/me/actions", "{\"name\":\"inbox.page\",\"args\":{}}")
                            .statusCode());
            node.assertQuiet();
            browser.origin("https://dash.test");
            assertEquals(200, browser.get("/api/v1/me/server").statusCode());
        }
    }

    @Test
    void aServerCannotTouchAnotherServersSessions() {
        UUID uidA = UUID.randomUUID();
        UUID uidB = UUID.randomUUID();
        try (FakeNode serverA = node(key(), uidA);
                FakeNode serverB = node(key(), uidB)) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.23");
            SessionCreated created = create(serverB, uidB);
            login(serverB, browser, created);
            try (FakeBrowser.Stream stream = browser.stream()) {
                stream.next();
                snapshot(serverB, browser, uidB, 5);
                serverA.send(new Patch(
                        null,
                        1,
                        List.of(created.session()),
                        uidA,
                        1,
                        "player.unreadInbox",
                        PatchOp.REPLACE,
                        null,
                        JsonNodeFactory.instance.numberNode(666)));
                serverA.send(new SessionClosed(null, 1, created.session(), PLAYER, CloseReason.REVOKED));
                serverA.send(new SessionsRevoked(null, 1, PLAYER, CloseReason.REVOKED));
                serverA.send(new Ping("after", 1, Instant.now()));
                serverA.expect(Pong.class);
                assertEquals(200, browser.get("/api/v1/me/server").statusCode());
                assertEquals(3, cachedInbox(browser));
                stream.ping();
                assertEquals("pong", stream.next().path("type").asString());
            }
        }
    }

    @Test
    void aNodeThatConnectsAgainReplacesItsConnectionAndRestartsItsSequence() {
        String key = key();
        UUID uid = UUID.randomUUID();
        FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.24");
        try (FakeNode first = node(key, uid)) {
            SessionCreated created = create(first, uid);
            login(first, browser, created);
            try (FakeBrowser.Stream stream = browser.stream()) {
                stream.next();
                snapshot(first, browser, uid, 40);
                try (FakeNode second = node(key, uid)) {
                    assertEquals(NodeHub.CLOSE_REPLACED, first.awaitClose());
                    second.expect(Subscribe.class);
                    SnapshotRequest reload = second.expect(SnapshotRequest.class);
                    second.sendRaw(Recorded.snapshot(reload.id(), reload.session(), uid, 0));
                    assertEquals("server", stream.next().path("type").asString());
                    assertEquals("resync", stream.next().path("type").asString());
                    second.send(new Patch(
                            null,
                            1,
                            List.of(created.session()),
                            uid,
                            1,
                            "player.unreadInbox",
                            PatchOp.REPLACE,
                            null,
                            JsonNodeFactory.instance.numberNode(9)));
                    assertEquals(9, stream.next().path("value").asInt());
                    assertEquals(9, cachedInbox(browser));
                }
            }
        }
    }

    @Test
    void aCompanyChangeResubscribesTheOtherNodes() {
        String key = key();
        UUID primaryId = UUID.randomUUID();
        UUID secondaryId = UUID.randomUUID();
        try (FakeNode primary = node(key, primaryId);
                FakeNode secondary = FakeNode.welcomed(
                        gateway,
                        FakeNode.hello(
                                KeyKind.DASHBOARD_KEY,
                                key,
                                secondaryId,
                                NodeRole.SECONDARY,
                                PLUGIN,
                                FakeNode.FEATURES,
                                null))) {
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.25");
            SessionCreated created = create(primary, primaryId);
            login(primary, browser, created);
            CompletableFuture<HttpResponse<String>> loading = browser.getAsync("/api/v1/me/snapshot");
            SnapshotRequest request = primary.expect(SnapshotRequest.class);
            assertEquals(created.session(), secondary.expect(Subscribe.class).session());
            primary.sendRaw(Recorded.snapshot(request.id(), request.session(), primaryId, 1));
            assertEquals(200, loading.join().statusCode());

            ObjectNode other = ((ObjectNode) Recorded.message("snapshot.json").path("company")).put("id", 9);
            other.putArray("members");
            primary.send(new Patch(
                    null, 1, List.of(created.session()), primaryId, 2, "company", PatchOp.REPLACE, null, other));
            assertEquals(created.session(), secondary.expect(Subscribe.class).session());
            primary.assertQuiet();
            assertEquals(
                    9,
                    FakeBrowser.json(browser.get("/api/v1/me/snapshot"))
                            .at("/company/id")
                            .asInt());

            primary.send(new Patch(
                    null, 1, List.of(created.session()), primaryId, 3, "company", PatchOp.REMOVE, null, null));
            assertEquals(created.session(), secondary.expect(Subscribe.class).session());
            secondary.send(new Patch(
                    null,
                    1,
                    List.of(created.session()),
                    secondaryId,
                    1,
                    "company.members",
                    PatchOp.UPSERT,
                    "0b1c2d3e-4f5a-4b6c-9d7e-8f9a0b1c2d3e",
                    Recorded.message("patch.json").path("value")));
            SnapshotRequest reload = primary.expect(SnapshotRequest.class);
            ObjectNode without =
                    (ObjectNode) FakeBrowser.json(Recorded.snapshot(reload.id(), reload.session(), primaryId, 3));
            without.remove("company");
            primary.sendRaw(without.toString());
            assertTrue(FakeBrowser.json(browser.get("/api/v1/me/snapshot"))
                    .path("company")
                    .isMissingNode());
        }
    }

    @Test
    void thePluginRevokesSessions() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            FakeBrowser phone = new FakeBrowser(gateway, "203.0.113.14");
            FakeBrowser desktop = new FakeBrowser(gateway, "203.0.113.15");
            login(node, phone, create(node, uid));
            login(node, desktop, create(node, uid));
            node.send(new SessionsRevoked(null, 1, PLAYER, CloseReason.REVOKED));
            assertEventually(() -> phone.get("/api/v1/me/server").statusCode() == 401);
            assertEquals(401, desktop.get("/api/v1/me/snapshot").statusCode());
        }
    }

    @Test
    void wrongCodesEndThePendingSession() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            SessionCreated created = create(node, uid);
            FakeBrowser browser = new FakeBrowser(gateway, "203.0.113.16");
            for (int left = 4; left >= 1; left--) {
                HttpResponse<String> wrong =
                        browser.post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"ZZZZZZ\"}");
                assertEquals(401, wrong.statusCode());
                assertEquals(left, FakeBrowser.json(wrong).path("attemptsLeft").asInt());
                assertEquals(
                        left,
                        FakeBrowser.json(browser.get("/api/v1/s/" + created.shortId()))
                                .path("attemptsLeft")
                                .asInt());
            }
            HttpResponse<String> last =
                    browser.post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"ZZZZZZ\"}");
            assertEquals(410, last.statusCode());
            assertEquals("TOO_MANY_ATTEMPTS", error(last));
            assertEquals(CloseReason.EXPIRED, node.expect(SessionClosed.class).reason());
            HttpResponse<String> gone = browser.get("/api/v1/s/" + created.shortId());
            assertEquals(404, gone.statusCode());
            assertEquals("UNKNOWN_SESSION", error(gone));
            assertEquals(
                    404,
                    browser.post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"" + created.code() + "\"}")
                            .statusCode());
        }
    }

    @Test
    void parallelWrongCodesCannotExceedTheAttempts() {
        UUID uid = UUID.randomUUID();
        try (FakeNode node = node(key(), uid)) {
            SessionCreated created = create(node, uid);
            List<CompletableFuture<HttpResponse<String>>> tries = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                tries.add(new FakeBrowser(gateway, "198.18.0." + (i + 1))
                        .postAsync("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"ZZZZZZ\"}"));
            }
            List<Integer> statuses = tries.stream()
                    .map(CompletableFuture::join)
                    .map(HttpResponse::statusCode)
                    .toList();
            assertEquals(4, statuses.stream().filter(status -> status == 401).count(), statuses.toString());
            assertEquals(CloseReason.EXPIRED, node.expect(SessionClosed.class).reason());
            assertEquals(created.session(), node.expect(Unsubscribe.class).session());
            node.assertQuiet();
            assertEquals(
                    404,
                    new FakeBrowser(gateway, "198.18.1.1")
                            .post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"" + created.code() + "\"}")
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
                    HttpResponse<String> response =
                            browser.post("/api/v1/s/" + created.shortId() + "/login", "{\"code\":\"ZZZZZZ\"}");
                    if (response.statusCode() == 429) {
                        assertEquals("RATE_LIMITED", error(response));
                        refused++;
                    }
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
            assertEventually(() -> icon(hash).statusCode() == 200);
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
        HttpResponse<String> served = icon(hash);
        assertEquals("image/png", served.headers().firstValue("Content-Type").orElse(""));
        assertTrue(served.headers().firstValue("Cache-Control").orElse("").contains("immutable"));
        assertEquals(404, icon("0".repeat(64)).statusCode());

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
            HttpResponse<String> server = same.get("/api/v1/me/server");
            assertEquals(200, server.statusCode(), server.body());
            assertFalse(FakeBrowser.json(server).path("online").asBoolean());
            HttpResponse<String> snapshot = same.get("/api/v1/me/snapshot");
            assertEquals(503, snapshot.statusCode());
            assertEquals("SERVER_OFFLINE", error(snapshot));
        }
    }

    private static HttpResponse<String> icon(String hash) {
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
