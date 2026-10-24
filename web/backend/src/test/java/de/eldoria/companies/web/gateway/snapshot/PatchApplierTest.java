/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.snapshot;

import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.update.Patch;
import de.eldoria.companies.web.api.update.PatchOp;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchApplierTest {
    private static final JsonMapper MAPPER = Protocol.mapper();

    private static ObjectNode snapshot() {
        return (ObjectNode) MAPPER.readTree("""
                {"player": {"name": "Steve", "unreadInbox": 3},
                 "company": {"members": [{"uuid": "a", "name": "Steve"}, {"uuid": "b", "name": "Alex"}],
                             "orders": [{"id": 101, "name": "Diamonds"}],
                             "tags": ["x"]},
                 "orders": []}""");
    }

    private static Patch patch(String path, PatchOp op, @Nullable String key, @Nullable String value) {
        JsonNode node = value == null ? null : MAPPER.readTree(value);
        return new Patch(null, 1, List.of("s"), UUID.randomUUID(), 1, path, op, key, node);
    }

    @Test
    void replacesAValue() throws PatchApplier.Unapplicable {
        ObjectNode snapshot = snapshot();
        PatchApplier.apply(snapshot, patch("player.unreadInbox", PatchOp.REPLACE, null, "4"));
        assertEquals(4, snapshot.at("/player/unreadInbox").asInt());
    }

    @Test
    void upsertsListEntriesByIdOrUuid() throws PatchApplier.Unapplicable {
        ObjectNode snapshot = snapshot();
        PatchApplier.apply(snapshot, patch("company.members", PatchOp.UPSERT, "b", "{\"uuid\":\"b\",\"name\":\"Al\"}"));
        PatchApplier.apply(
                snapshot, patch("company.members", PatchOp.UPSERT, "c", "{\"uuid\":\"c\",\"name\":\"Kes\"}"));
        PatchApplier.apply(snapshot, patch("company.orders", PatchOp.UPSERT, "101", "{\"id\":101,\"name\":\"More\"}"));
        assertEquals(3, snapshot.at("/company/members").size());
        assertEquals("Al", snapshot.at("/company/members/1/name").asString());
        assertEquals("More", snapshot.at("/company/orders/0/name").asString());
    }

    @Test
    void removesListEntriesAndValues() throws PatchApplier.Unapplicable {
        ObjectNode snapshot = snapshot();
        PatchApplier.apply(snapshot, patch("company.orders", PatchOp.REMOVE, "101", null));
        PatchApplier.apply(snapshot, patch("company.tags", PatchOp.REMOVE, "x", null));
        PatchApplier.apply(snapshot, patch("company.members", PatchOp.REMOVE, "nobody", null));
        assertEquals(0, snapshot.at("/company/orders").size());
        assertEquals(0, snapshot.at("/company/tags").size());
        assertEquals(2, snapshot.at("/company/members").size());
        PatchApplier.apply(snapshot, patch("company", PatchOp.REMOVE, null, null));
        assertFalse(snapshot.has("company"));
    }

    @Test
    void replacesTheCompanyAsAWhole() throws PatchApplier.Unapplicable {
        ObjectNode snapshot = snapshot();
        snapshot.remove("company");
        PatchApplier.apply(snapshot, patch("company", PatchOp.REPLACE, null, "{\"members\":[]}"));
        assertTrue(snapshot.at("/company/members").isArray());
    }

    @Test
    void recognisesPatchesOfAnotherCompany() {
        ObjectNode snapshot = snapshot();
        snapshot.withObject("company").put("id", 7);
        assertFalse(PatchApplier.foreignCompany(
                snapshot, patch("company.orders", PatchOp.UPSERT, "1", "{\"id\":1,\"company\":7}")));
        assertTrue(PatchApplier.foreignCompany(
                snapshot, patch("company.orders", PatchOp.UPSERT, "1", "{\"id\":1,\"company\":8}")));
        assertFalse(PatchApplier.foreignCompany(snapshot, patch("company.members", PatchOp.REMOVE, "a", null)));
        assertFalse(PatchApplier.foreignCompany(snapshot, patch("company", PatchOp.REPLACE, null, "{\"id\":8}")));
        snapshot.remove("company");
        assertTrue(PatchApplier.foreignCompany(snapshot, patch("company.members", PatchOp.REMOVE, "a", null)));
    }

    @Test
    void refusesWhatDoesNotFit() {
        ObjectNode snapshot = snapshot();
        snapshot.remove("company");
        assertThrows(
                PatchApplier.Unapplicable.class,
                () -> PatchApplier.apply(snapshot, patch("company.members", PatchOp.UPSERT, "a", "{}")));
        assertThrows(
                PatchApplier.Unapplicable.class,
                () -> PatchApplier.apply(snapshot, patch("player.name", PatchOp.UPSERT, "a", "{}")));
        assertThrows(
                PatchApplier.Unapplicable.class,
                () -> PatchApplier.apply(snapshot, patch("player.unreadInbox", PatchOp.UNKNOWN, null, "1")));
    }
}
