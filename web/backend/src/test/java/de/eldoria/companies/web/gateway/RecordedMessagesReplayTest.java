/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.eldoria.companies.web.api.Message;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.Credentials;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.ErrorCode;
import de.eldoria.companies.web.api.link.ErrorMessage;
import de.eldoria.companies.web.api.link.Pong;
import de.eldoria.companies.web.api.link.UnknownMessage;
import de.eldoria.companies.web.api.session.SessionCreated;
import de.eldoria.companies.web.gateway.node.NodeHub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the protocol messages recorded for every released protocol version, as the gateway must keep reading what
 * plugins of each version send: every recorded message reads through the gateway's reader as its own type, and a
 * live gateway answers each one as its type demands without dropping the connection.
 */
class RecordedMessagesReplayTest {
    private static final Set<String> SILENT =
            Set.of("branding.update", "patch", "session.closed", "session.revoked", "pong", "error");
    private static final Map<String, Class<? extends Message>> ANSWERED =
            Map.of("ping", Pong.class, "session.create", SessionCreated.class);
    private static TestGateway gateway;
    private static String token;

    @BeforeAll
    static void start() {
        Map<String, String> env = TestDatabase.env("replay");
        env.put("GATEWAY_MODE", "self-hosted");
        gateway = TestGateway.start(env);
        Matcher matcher = Pattern.compile("cst_\\S+").matcher(gateway.printed());
        assertTrue(matcher.find());
        token = matcher.group();
    }

    @AfterAll
    static void stop() {
        gateway.close();
    }

    @Test
    void messagesAreRecorded() {
        assertFalse(Recorded.files().isEmpty(), "No recorded messages under " + Recorded.directory());
    }

    @TestFactory
    Stream<DynamicTest> everyRecordedMessageReadsAsItsType() {
        return Recorded.files().stream()
                .map(file -> DynamicTest.dynamicTest(name(file), () -> {
                    String text = Files.readString(file);
                    String type = typeOf(text);
                    Message message = NodeHub.read(text);
                    assertFalse(message instanceof UnknownMessage, "Unknown to the gateway: " + type);
                    assertEquals(
                            type,
                            message.getClass().getAnnotation(JsonTypeName.class).value());
                }));
    }

    @TestFactory
    Stream<DynamicTest> aLiveGatewayAnswersEveryRecordedMessage() {
        return Recorded.files().stream()
                .map(file -> DynamicTest.dynamicTest(name(file), () -> {
                    String text = Files.readString(file);
                    String type = typeOf(text);
                    try (FakeNode node = FakeNode.connect(gateway)) {
                        ObjectNode hello = Recorded.message("hello.json");
                        Hello welcomeMe = (Hello) NodeHub.read(hello.toString());
                        node.send(new Hello(
                                welcomeMe.id(),
                                welcomeMe.v(),
                                welcomeMe.plugin(),
                                welcomeMe.schema(),
                                welcomeMe.node(),
                                welcomeMe.features(),
                                welcomeMe.limits(),
                                new Credentials(KeyKind.SERVER_TOKEN, token),
                                welcomeMe.userAgent(),
                                welcomeMe.branding()));
                        node.expect(Welcome.class);
                        if (type.equals("hello")) {
                            node.sendRaw(text);
                            assertEquals(
                                    ErrorCode.INVALID,
                                    node.expect(ErrorMessage.class).code());
                            return;
                        }
                        node.sendRaw(text);
                        if (SILENT.contains(type)) {
                            node.assertQuiet();
                        } else if (ANSWERED.containsKey(type)) {
                            assertInstanceOf(ANSWERED.get(type), node.next());
                        } else {
                            assertEquals(
                                    ErrorCode.INVALID,
                                    node.expect(ErrorMessage.class).code(),
                                    type);
                        }
                    }
                }));
    }

    @Test
    void anUnknownTypeIsAnsweredAndTheConnectionStays() {
        try (FakeNode node = FakeNode.connect(gateway)) {
            node.send(FakeNode.hello(KeyKind.SERVER_TOKEN, token, UUID.randomUUID(), "9.0.0"));
            node.expect(Welcome.class);
            node.sendRaw("{\"type\":\"from.the.future\",\"id\":\"f\",\"v\":2,\"extra\":true}");
            assertEquals(ErrorCode.UNKNOWN_TYPE, node.expect(ErrorMessage.class).code());
            node.sendRaw("not json");
            assertEquals(ErrorCode.INVALID, node.expect(ErrorMessage.class).code());
            node.sendRaw("{\"type\":\"ping\",\"id\":\"p\",\"v\":1,\"time\":\"2026-10-24T12:00:00Z\"}");
            assertEquals("p", node.expect(Pong.class).id());
        }
    }

    private static String typeOf(String text) {
        return Protocol.mapper().readTree(text).path("type").asString();
    }

    private static String name(Path file) {
        return file.getParent().getFileName() + "/" + file.getFileName();
    }
}
