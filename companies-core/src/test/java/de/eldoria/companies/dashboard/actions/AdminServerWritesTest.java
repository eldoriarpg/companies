/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.configuration.ChangeTracker;
import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.dashboard.DashboardWorld;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteNodeData;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard's tools for boards, nodes and the configuration: each allowed with the permission of its command,
 * NPCs bound and unbound on the node that answers, old nodes removed but never the answering one, and every change
 * written to the admin log.
 */
class AdminServerWritesTest {
    @TempDir
    Path directory;

    private DashboardWorld world;
    private UUID admin;

    @BeforeEach
    void setUp() throws Exception {
        world = DashboardWorld.create(directory);
        admin = world.player("Ada");
    }

    @AfterEach
    void tearDown() {
        world.close();
    }

    private ActionResult perform(String action, String args, String... permissions) {
        var session = new StoredSession(
                "session",
                admin,
                "Ada",
                UUID.randomUUID(),
                Times.now(),
                Times.now(),
                Times.now(),
                Times.now(),
                null,
                Set.of(permissions));
        return world.writes.perform(
                new ActionRequest(
                        "act",
                        Protocol.VERSION,
                        session.id(),
                        admin,
                        action,
                        Protocol.mapper().readTree(args)),
                session);
    }

    private ActionResult boards(String action, String args) {
        return perform(action, args, Permission.Admin.BOARDS);
    }

    @Test
    void eachToolNeedsThePermissionOfItsCommand() {
        assertEquals(
                "companyadmin.error.permission",
                perform(Actions.ADMIN_BOARDS_RELOAD, "{}", Permission.Admin.NODES)
                        .reason());
        assertEquals(
                "companyadmin.error.permission",
                perform(Actions.ADMIN_NODES_LIST, "{}", Permission.Admin.BOARDS).reason());
        assertEquals(
                "companyadmin.error.permission",
                perform(Actions.ADMIN_CONFIG_SYNC, "{}", Permission.Admin.NODES).reason());
        assertEquals(0, world.configSyncs.get());
        assertTrue(world.adminActions.isEmpty());
    }

    @Test
    void thePresetsAreListedWithTheirOpenOrders() {
        var presets = boards(Actions.ADMIN_BOARDS_PRESETS, "{}");

        assertTrue(presets.ok(), presets.reason());
        assertEquals("iron", presets.data().path("presets").get(0).path("name").asString());
        assertEquals("Iron", presets.data().path("presets").get(0).path("label").asString());
        assertEquals(0, presets.data().path("presets").get(0).path("open").asInt());
        assertTrue(boards(Actions.ADMIN_BOARDS_RELOAD, "{}").ok());
    }

    @Test
    void anNpcIsBoundAndUnbound() {
        var none = boards(Actions.ADMIN_BOARDS_NPC_LIST, "{}");
        assertEquals("Citizens", none.data().path("hooks").get(0).asString());
        assertEquals(0, none.data().path("bindings").size());

        var linked = boards(Actions.ADMIN_BOARDS_NPC_LINK, "{\"hook\":\"citizens\",\"npc\":\"4\",\"board\":\"iron\"}");
        assertTrue(linked.ok(), linked.reason());
        assertEquals("Citizens", linked.data().path("hook").asString());
        assertEquals(
                1,
                boards(Actions.ADMIN_BOARDS_NPC_LIST, "{}")
                        .data()
                        .path("bindings")
                        .size());
        assertEquals(
                "boards.error.unknownNpc",
                boards(Actions.ADMIN_BOARDS_NPC_LINK, "{\"hook\":\"Citizens\",\"npc\":\"9\"}")
                        .reason());
        assertEquals(
                "dashboard.invalid",
                boards(Actions.ADMIN_BOARDS_NPC_LINK, "{\"hook\":\"Citizens\",\"npc\":\"Selected\"}")
                        .reason());
        assertEquals(
                "boards.error.unknownHook",
                boards(Actions.ADMIN_BOARDS_NPC_LINK, "{\"hook\":\"FancyNpcs\",\"npc\":\"guide\"}")
                        .reason());

        assertTrue(boards(Actions.ADMIN_BOARDS_NPC_UNLINK, "{\"hook\":\"Citizens\",\"npc\":\"4\"}")
                .ok());
        assertEquals(
                "boards.error.notLinked",
                boards(Actions.ADMIN_BOARDS_NPC_UNLINK, "{\"hook\":\"Citizens\",\"npc\":\"4\"}")
                        .reason());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("board npc link")));
    }

    @Test
    void oldNodesAreRemovedButNeverTheAnsweringOne() {
        var listed = perform(Actions.ADMIN_NODES_LIST, "{}", Permission.Admin.NODES);
        assertTrue(listed.ok(), listed.reason());
        var current = listed.data().path("nodes").get(0);
        assertTrue(current.path("current").asBoolean());
        assertEquals("Redis", current.path("bus").asString());
        assertFalse(current.path("behind").asBoolean());
        assertTrue(current.path("restart").isNull() || current.path("restart").isMissingNode());

        world.nodeData.publishSettings("user.yml", "maxOrders: 3", "Ada");
        world.nodeData.appliedSettings("user.yml", 0);
        world.nodeData.restartPending(List.of(ChangeTracker.BOARDS_ENABLED));
        var marked = perform(Actions.ADMIN_NODES_LIST, "{}", Permission.Admin.NODES)
                .data()
                .path("nodes")
                .get(0);
        assertTrue(marked.path("behind").asBoolean());
        assertEquals("config.yml boards.enabled", marked.path("restart").asString());

        assertEquals(
                "companyadmin.node.error.current",
                perform(
                                Actions.ADMIN_NODES_REMOVE,
                                "{\"uid\":\"%s\"}".formatted(current.path("uid").asString()),
                                Permission.Admin.NODES)
                        .reason());
        assertEquals(
                "companyadmin.node.error.unknown",
                perform(
                                Actions.ADMIN_NODES_REMOVE,
                                "{\"uid\":\"%s\"}".formatted(UUID.randomUUID()),
                                Permission.Admin.NODES)
                        .reason());

        var old = new SqLiteNodeData(new NodeSettings());
        old.updateNode(world.plugin);
        var removed = perform(
                Actions.ADMIN_NODES_REMOVE, "{\"uid\":\"%s\"}".formatted(old.nodeUid()), Permission.Admin.NODES);
        assertTrue(removed.ok(), removed.reason());
        assertEquals(1, removed.data().path("nodes").size());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("node remove")));
    }

    @Test
    void syncingIsRefusedOnASecondaryAndWhenItFails() {
        world.nodeType = NodeType.SECONDARY;
        assertEquals(
                "error.secondarynode",
                perform(Actions.ADMIN_CONFIG_SYNC, "{}", Permission.Admin.ADMIN).reason());

        world.nodeType = NodeType.PRIMARY;
        world.failSync = true;
        assertEquals(
                "companyadmin.syncconfiguration.failed",
                perform(Actions.ADMIN_CONFIG_SYNC, "{}", Permission.Admin.ADMIN).reason());
        assertEquals(0, world.configSyncs.get());
        assertTrue(world.adminActions.stream().noneMatch(line -> line.contains("syncconfiguration")));
    }

    @Test
    void theConfigurationIsSynced() {
        assertTrue(
                perform(Actions.ADMIN_CONFIG_SYNC, "{}", Permission.Admin.ADMIN).ok());
        assertEquals(1, world.configSyncs.get());
        assertTrue(world.adminActions.stream().anyMatch(line -> line.contains("syncconfiguration")));
    }
}
