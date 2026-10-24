/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api;

import de.eldoria.companies.schema.JsonSchemas;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolSchemaTest {
    @DeprecatedSince(value = "2027-03-01", replacement = "replacement")
    record Old(
            @DeprecatedSince(value = "2027-03-01", replacement = "replacement")
            String old,

            String replacement) {}

    @Test
    void theCommittedSchemaIsCurrent() throws IOException {
        assertTrue(Files.exists(ProtocolSchema.PATH), "Run ./gradlew :web-api:generateSchema");
        assertEquals(
                ProtocolSchema.generate(),
                Files.readString(ProtocolSchema.PATH),
                "The protocol schema is stale, run ./gradlew :web-api:generateSchema");
    }

    @Test
    void deprecatedPartsAreMarked() {
        JsonNode old = JsonSchemas.document(Old.class, Protocol.mapper(), new ProtocolSchema.Deprecations())
                .path("$defs")
                .path("Old");
        assertTrue(old.path("deprecated").asBoolean());
        assertEquals("2027-03-01", old.at("/properties/old/x-deprecated-since").asString());
        assertEquals("replacement", old.at("/properties/old/x-replacement").asString());
        assertFalse(old.at("/properties/replacement").has("deprecated"));
    }

    @Test
    void featuresAndActionsAreListed() {
        JsonNode document = ProtocolSchema.document();
        assertEquals(Protocol.VERSION, document.path("x-protocol").asInt());
        assertEquals(Features.ALL.size(), document.path("x-features").size());
        assertEquals(
                Features.ORDERS_ACCEPT,
                document.at("/x-actions/orders.accept.abort/feature").asString());
    }
}
