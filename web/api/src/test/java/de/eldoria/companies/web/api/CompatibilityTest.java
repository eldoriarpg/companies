/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.Hello;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.NodeRole;
import de.eldoria.companies.web.api.handshake.PartKind;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.Welcome;
import de.eldoria.companies.web.api.link.Ping;
import de.eldoria.companies.web.api.link.UnknownMessage;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.api.update.PatchOp;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Messages of newer peers must read with the current records: unknown fields are ignored, unknown enum values read as
 * {@code UNKNOWN}, and an unknown message type reads as {@link UnknownMessage}. The messages are under
 * {@code src/test/resources/compat/}.
 */
class CompatibilityTest {
    private static final Path COMPAT = Path.of("src/test/resources/compat");
    private static final JsonMapper MAPPER = Protocol.mapper();

    @Test
    void aNewerPluginsHelloReads() {
        Hello hello = assertInstanceOf(Hello.class, read("hello.from-newer-plugin"));
        assertEquals("3.0.0", hello.plugin());
        assertEquals(NodeRole.UNKNOWN, hello.node().role());
        assertEquals(KeyKind.UNKNOWN, hello.credentials().kind());
        assertEquals(100, hello.limits().maxPageSize());
        assertEquals(List.of("orders.browse", "orders.create.v2", "guilds"), hello.features());
    }

    @Test
    void aNewerGatewaysWelcomeReads() {
        Welcome welcome = assertInstanceOf(Welcome.class, read("welcome.from-newer-gateway"));
        assertEquals(GatewayKind.UNKNOWN, welcome.gateway());
        assertEquals(VersionState.UNKNOWN, welcome.version().status());
        assertEquals(PartKind.UNKNOWN, welcome.deprecated().getFirst().kind());
    }

    @Test
    void anUnknownPatchOperationReads() {
        Patch patch = assertInstanceOf(Patch.class, read("patch.unknown-op"));
        assertEquals(PatchOp.UNKNOWN, patch.op());
        assertEquals(9, patch.seq());
    }

    @Test
    void anUnknownMessageTypeReads() {
        UnknownMessage message = assertInstanceOf(UnknownMessage.class, read("unknown-type"));
        assertEquals("m-1", message.id());
    }

    @Test
    void messagesAreWrittenWithTheirTypeAndWithoutNulls() {
        JsonNode written = MAPPER.valueToTree(new Ping(null, Protocol.VERSION, Instant.parse("2026-10-24T12:00:00Z")));
        assertEquals("ping", written.path("type").asString());
        assertEquals("2026-10-24T12:00:00Z", written.path("time").asString());
        assertFalse(written.has("id"));
    }

    @Test
    void enumsAreWrittenAsTheirConstantName() {
        assertEquals("\"SELF_HOSTED\"", MAPPER.writeValueAsString(GatewayKind.SELF_HOSTED));
        assertEquals("\"UPSERT\"", MAPPER.writeValueAsString(PatchOp.UPSERT));
    }

    private static Message read(String name) {
        return MAPPER.readValue(COMPAT.resolve(name + ".json").toFile(), Message.class);
    }
}
