/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.gateway.storage.TokenRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A self-hosted gateway whose {@code default} token is set with {@code SERVER_TOKEN}.
 */
class FixedTokenGatewayTest {
    private static final String FIRST = "cst_" + "a".repeat(43);
    private static final String SECOND = "cst_" + "b".repeat(43);

    private static Map<String, String> env(String schema, String token) {
        Map<String, String> env = new HashMap<>(TestDatabase.env(schema));
        env.put("GATEWAY_MODE", "self-hosted");
        env.put("LYNA_URL", "http://localhost:1");
        env.put("SERVER_TOKEN", token);
        return env;
    }

    private static Welcome connect(TestGateway gateway, String token) {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.SERVER_TOKEN, token, UUID.randomUUID(), "2.2.0"));
            return node.expect(Welcome.class);
        }
    }

    private static ErrorMessage refused(TestGateway gateway, String token) {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.SERVER_TOKEN, token, UUID.randomUUID(), "2.2.0"));
            ErrorMessage error = node.expect(ErrorMessage.class);
            assertEquals(4401, node.awaitClose());
            return error;
        }
    }

    @Test
    void acceptsTheTokenFromTheEnvironmentAndFollowsItsChanges() {
        try (TestGateway gateway = TestGateway.start(env("fixed_token", FIRST))) {
            assertFalse(gateway.printed().contains("cst_"), gateway.printed());
            assertEquals(GatewayKind.SELF_HOSTED, connect(gateway, FIRST).gateway());
        }
        try (TestGateway gateway = TestGateway.start(env("fixed_token", SECOND))) {
            assertEquals(ErrorCode.UNAUTHORIZED, refused(gateway, FIRST).code());
            assertEquals(GatewayKind.SELF_HOSTED, connect(gateway, SECOND).gateway());
        }
    }

    @Test
    void replacesAPrintedDefaultAndKeepsOtherNetworks() {
        Map<String, String> generated = env("fixed_replace", "");
        generated.remove("SERVER_TOKEN");
        String printed;
        try (TestGateway gateway = TestGateway.start(generated)) {
            printed = gateway.printed()
                    .substring(gateway.printed().indexOf("cst_"))
                    .split("\\s")[0];
        }
        TokenRepository tokens = new TokenRepository();
        String other = tokens.create("other").orElseThrow();
        String id = tokens.find(printed).orElseThrow().id();

        try (TestGateway gateway = TestGateway.start(env("fixed_replace", FIRST))) {
            assertEquals(ErrorCode.UNAUTHORIZED, refused(gateway, printed).code());
            assertEquals(GatewayKind.SELF_HOSTED, connect(gateway, FIRST).gateway());
            assertEquals(GatewayKind.SELF_HOSTED, connect(gateway, other).gateway());
            assertEquals(id, tokens.find(FIRST).orElseThrow().id());
        }
    }

    @Test
    void refusesToRegenerateOrDeleteAFixedDefault() {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(printed, true, StandardCharsets.UTF_8);
        TokenRepository tokens = new TokenRepository();

        assertEquals(1, GatewayService.tokens(List.of("token", "regenerate", "default"), tokens, true, out));
        assertEquals(1, GatewayService.tokens(List.of("token", "delete", "default"), tokens, true, out));
        assertTrue(printed.toString(StandardCharsets.UTF_8).contains("SERVER_TOKEN"));
    }

    @Test
    void tokensNeedThePrefixAndLength() {
        assertTrue(GatewayService.validToken(FIRST));
        assertFalse(GatewayService.validToken("a".repeat(47)));
        assertFalse(GatewayService.validToken("cst_short"));
    }
}
