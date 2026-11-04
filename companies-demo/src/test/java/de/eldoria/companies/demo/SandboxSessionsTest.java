/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SandboxSessionsTest {
    private static final Instant SOON = Instant.parse("2026-11-04T12:30:00Z");
    private static final Instant LATER = Instant.parse("2026-11-04T13:00:00Z");

    @Test
    void theSandboxEndsWithItsLatestNode() {
        assertEquals(
                Optional.of(LATER),
                SandboxSessions.ends(
                        List.of(group(AdminSandbox.GROUP, SOON, false), group(AdminSandbox.GROUP, LATER, false))));
    }

    @Test
    void aSandboxWithoutEndHasNone() {
        assertEquals(Optional.of(Instant.MAX), SandboxSessions.ends(List.of(group(AdminSandbox.GROUP, null, false))));
    }

    @Test
    void otherGroupsAndExpiredNodesAreNoSandbox() {
        assertEquals(
                Optional.empty(),
                SandboxSessions.ends(List.of(group("default", null, false), group(AdminSandbox.GROUP, SOON, true))));
    }

    @Test
    void aPlayerNeverKeptIsLeftAloneUnlessTheirSandboxWasSeenEnding() {
        assertEquals(new SandboxSessions.Action.Ignore(), SandboxSessions.decide(false, false, Optional.empty(), SOON));
        assertEquals(new SandboxSessions.Action.Revoke(), SandboxSessions.decide(false, true, Optional.empty(), SOON));
    }

    @Test
    void aKeptPlayerWithoutSandboxIsRevoked() {
        assertEquals(new SandboxSessions.Action.Revoke(), SandboxSessions.decide(true, false, Optional.empty(), SOON));
    }

    @Test
    void aRunningSandboxIsPlannedForItsEnd() {
        assertEquals(
                new SandboxSessions.Action.Plan(Duration.ofMinutes(30)),
                SandboxSessions.decide(true, true, Optional.of(LATER), SOON));
        assertEquals(
                new SandboxSessions.Action.Plan(Duration.ofMinutes(30)),
                SandboxSessions.decide(false, false, Optional.of(LATER), SOON));
    }

    @Test
    void aSandboxWithoutEndIsKeptUnplanned() {
        assertEquals(
                new SandboxSessions.Action.Keep(), SandboxSessions.decide(false, true, Optional.of(Instant.MAX), SOON));
    }

    private static Node group(String name, Instant expiry, boolean expired) {
        return (Node) Proxy.newProxyInstance(
                SandboxSessionsTest.class.getClassLoader(),
                new Class<?>[] {InheritanceNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getGroupName" -> name;
                    case "getExpiry" -> expiry;
                    case "hasExpired" -> expired;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
