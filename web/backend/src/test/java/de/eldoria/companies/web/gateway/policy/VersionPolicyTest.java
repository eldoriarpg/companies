/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.policy;

import de.eldoria.companies.web.api.handshake.DeprecatedPart;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionPolicyTest {
    private static final LocalDate TODAY = LocalDate.parse("2027-01-10");
    private static final VersionPolicy POLICY = read("""
            {"windowMonths": 6,
             "versions": [
               {"before": "2.0", "status": "DISCONTINUED", "since": "2026-12-01", "message": "Too old"},
               {"from": "2.0.0", "before": "2.1.0", "licence": "TRIAL", "status": "DISCONTINUED", "since": "2027-01-01",
                "message": "The trial version is no longer supported on the dashboard."},
               {"from": "2.0.0", "before": "2.1.0", "status": "DEPRECATED", "discontinuedAt": "2027-06-01",
                "updateTo": "2.2.0"},
               {"from": "2.1.0", "before": "2.2.0", "status": "DEPRECATED", "discontinuedAt": "2027-01-05"},
               {"from": "2.2.0", "before": "2.2.1", "status": "DISCONTINUED", "since": "2027-03-01"}
             ],
             "deprecations": [
               {"kind": "FEATURE", "name": "orders.create", "since": "2026-12-01", "replacement": "orders.create.v2"},
               {"kind": "FIELD", "name": "snapshot.rankings", "since": "2026-11-01", "removedAt": "2027-05-01",
                "before": "2.3.0"},
               {"kind": "PROTOCOL", "name": "1", "since": "2026-10-01", "removedAt": "2027-04-01"},
               {"kind": "FEATURE", "name": "market", "since": "2026-01-01"}
             ]}""");

    private static VersionPolicy read(String json) {
        return VersionPolicy.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theShippedPolicyReads() {
        VersionPolicy bundled = VersionPolicy.bundled();
        assertEquals(
                VersionState.SUPPORTED, bundled.status("2.2.0", null, TODAY).status());
    }

    @Test
    void versionsMoveAlongTheirDates() {
        assertEquals(
                VersionState.DISCONTINUED, POLICY.status("1.9.9", null, TODAY).status());
        VersionStatus deprecated = POLICY.status("2.0.3", "BOUGHT", TODAY);
        assertEquals(VersionState.DEPRECATED, deprecated.status());
        assertEquals(LocalDate.parse("2027-06-01"), deprecated.discontinuedAt());
        assertEquals("2.2.0", deprecated.updateTo());
        VersionStatus trial = POLICY.status("2.0.3", "TRIAL", TODAY);
        assertEquals(VersionState.DISCONTINUED, trial.status());
        assertEquals(LocalDate.parse("2027-01-01"), trial.since());
        VersionStatus passed = POLICY.status("2.1.4-SNAPSHOT", null, TODAY);
        assertEquals(VersionState.DISCONTINUED, passed.status());
        assertEquals(LocalDate.parse("2027-01-05"), passed.since());
        VersionStatus announced = POLICY.status("2.2.0", null, TODAY);
        assertEquals(VersionState.DEPRECATED, announced.status());
        assertEquals(LocalDate.parse("2027-03-01"), announced.discontinuedAt());
        assertEquals(VersionState.SUPPORTED, POLICY.status("2.2.1", null, TODAY).status());
        assertEquals(VersionState.SUPPORTED, POLICY.status("9.0.0", null, TODAY).status());
    }

    @Test
    void listsTheDeprecatedPartsANodeUses() {
        List<DeprecatedPart> parts = POLICY.inUse("2.2.0", List.of("orders.create", "market"), 1, TODAY);
        assertEquals(
                Set.of("orders.create", "snapshot.rankings", "1"),
                Set.copyOf(parts.stream().map(DeprecatedPart::name).toList()));
        DeprecatedPart feature = parts.stream()
                .filter(part -> part.name().equals("orders.create"))
                .findFirst()
                .orElseThrow();
        assertEquals(LocalDate.parse("2027-06-01"), feature.removedAt());
        assertTrue(POLICY.inUse("2.3.0", List.of(), 2, TODAY).isEmpty());
        assertEquals(Set.of("market"), POLICY.removedFeatures(TODAY));
    }

    @Test
    void refusesIncompleteRules() {
        assertThrows(
                IllegalArgumentException.class,
                () -> read("{\"windowMonths\":6,\"versions\":[{\"status\":\"DEPRECATED\"}],\"deprecations\":[]}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> read("{\"windowMonths\":6,\"versions\":[{\"status\":\"SUPPORTED\"}],\"deprecations\":[]}"));
    }

    @Test
    void comparesVersionsByTheirNumbers() {
        assertEquals(0, PluginVersion.parse("2.2").compareTo(PluginVersion.parse("2.2.0")));
        assertEquals(PluginVersion.parse("2.2"), PluginVersion.parse("2.2.0-SNAPSHOT"));
        assertTrue(PluginVersion.parse("2.10.0").compareTo(PluginVersion.parse("2.9.9")) > 0);
    }
}
