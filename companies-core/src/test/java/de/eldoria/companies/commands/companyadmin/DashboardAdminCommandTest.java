/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.dashboard.Dashboard;
import de.eldoria.companies.dashboard.gateway.GatewayConnection;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.handshake.GatewayKind;
import de.eldoria.companies.web.api.handshake.KeyKind;
import de.eldoria.companies.web.api.handshake.VersionState;
import de.eldoria.companies.web.api.handshake.VersionStatus;
import de.eldoria.companies.web.api.handshake.Welcome;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardAdminCommandTest {
    @Test
    void describesAConnectedServerWithAKeyAndNotices() {
        var welcome = new Welcome(
                null,
                Protocol.VERSION,
                Protocol.VERSION,
                GatewayKind.SELF_HOSTED,
                List.of(),
                new VersionStatus(VersionState.SUPPORTED, null, null, null, null),
                List.of(),
                false);
        var status = new Dashboard.Status(
                true,
                GatewayConnection.State.CONNECTED,
                Optional.of(URI.create("ws://gateway:8080/api/v1/servers/connect")),
                Optional.of(welcome),
                Optional.of(KeyKind.values()[0]),
                Optional.of("2026-11-01T04:00"),
                Optional.empty(),
                List.of("The gateway drops field a soon.", "The gateway drops field b soon."),
                0,
                0);

        var keys = DashboardAdminCommand.describe(status, key -> key).replacements().stream()
                .map(resolver -> ((TagResolver.Single) resolver).key())
                .toList();

        assertTrue(keys.containsAll(List.of("kind", "key_kind", "notice")), keys::toString);
        assertEquals(keys.size(), Set.copyOf(keys).size(), keys::toString);
    }
}
