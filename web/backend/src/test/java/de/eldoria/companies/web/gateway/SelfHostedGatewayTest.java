/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.gateway.storage.TokenRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The self-hosted gateway: server tokens generated on the first start and managed by command, several networks per
 * gateway, and no Lyna.
 */
class SelfHostedGatewayTest {
    private static final Pattern TOKEN = Pattern.compile("cst_[A-Za-z0-9_-]{43}");
    private static Map<String, String> env;
    private static TestGateway gateway;

    @BeforeAll
    static void start() {
        env = TestDatabase.env("self_hosted");
        env.put("GATEWAY_MODE", "self-hosted");
        env.put("LYNA_URL", "http://localhost:1");
        gateway = TestGateway.start(env);
    }

    @AfterAll
    static void stop() {
        gateway.close();
    }

    private static String token(String printed) {
        Matcher matcher = TOKEN.matcher(printed);
        assertTrue(matcher.find(), "No token in: " + printed);
        return matcher.group();
    }

    private static Welcome connect(String token) {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.SERVER_TOKEN, token, UUID.randomUUID(), "2.2.0"));
            return node.expect(Welcome.class);
        }
    }

    private static ErrorMessage refused(KeyKind kind, String token) {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(kind, token, UUID.randomUUID(), "2.2.0"));
            ErrorMessage error = node.expect(ErrorMessage.class);
            assertEquals(4401, node.awaitClose());
            return error;
        }
    }

    private static String run(String... args) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        GatewayService.tokens(
                List.of(args), new TokenRepository(), new PrintStream(printed, true, StandardCharsets.UTF_8));
        return printed.toString(StandardCharsets.UTF_8);
    }

    @Test
    void theFirstStartPrintsATokenOnce() {
        String token = token(gateway.printed());
        Welcome welcome = connect(token);
        assertEquals(GatewayKind.SELF_HOSTED, welcome.gateway());
        try (TestGateway again = TestGateway.start(env)) {
            assertFalse(TOKEN.matcher(again.printed()).find());
        }
        assertTrue(run("token", "list").contains("default"));
    }

    @Test
    void refusesUnknownTokensAndDashboardKeys() {
        assertEquals(
                ErrorCode.UNAUTHORIZED,
                refused(KeyKind.SERVER_TOKEN, "cst_wrong").code());
        assertEquals(
                ErrorCode.UNAUTHORIZED,
                refused(KeyKind.DASHBOARD_KEY, "lyd_anything").code());
    }

    @Test
    void servesSeveralNetworksAndRegeneratesTheirTokens() {
        String first = token(run("token", "create", "network-a"));
        String second = token(run("token", "create", "network-b"));
        assertTrue(run("token", "create", "network-a").contains("exists"));
        assertEquals(GatewayKind.SELF_HOSTED, connect(first).gateway());
        assertEquals(GatewayKind.SELF_HOSTED, connect(second).gateway());

        String regenerated = token(run("token", "regenerate", "network-a"));
        refused(KeyKind.SERVER_TOKEN, first);
        assertEquals(GatewayKind.SELF_HOSTED, connect(regenerated).gateway());

        assertTrue(run("token", "delete", "network-b").contains("Deleted"));
        refused(KeyKind.SERVER_TOKEN, second);
        assertTrue(run("token", "regenerate", "network-b").contains("No token"));
        assertTrue(run("token").startsWith("Usage"));
    }
}
