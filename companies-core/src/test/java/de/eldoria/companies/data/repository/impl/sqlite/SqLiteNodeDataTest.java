/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl.sqlite;

import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.wrapper.nodes.Node;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_BYTES;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqLiteNodeDataTest {
    private static final String VERSION = "2.0.0";

    @TempDir
    Path directory;

    private final UUID stale = UUID.randomUUID();
    private final Plugin plugin = plugin();
    private SqLiteNodeData nodeData;

    @BeforeEach
    void setUp() throws Exception {
        SqLiteTestDatabase.create(directory);
        nodeData = new SqLiteNodeData(new NodeSettings());
        nodeData.updateNode(plugin);
    }

    @Test
    void aSharedFileGetsANewRevisionOnlyWhenItChanged() {
        assertEquals(OptionalInt.of(1), nodeData.publishSettings("company.yml", "level: []", "startup"));
        assertEquals(OptionalInt.empty(), nodeData.publishSettings("company.yml", "level: []", "Ada"));
        assertEquals(OptionalInt.of(2), nodeData.publishSettings("company.yml", "level: [1]", "Ada"));
        assertEquals(OptionalInt.of(1), nodeData.publishSettings("user.yml", "maxOrders: 3", "Ada"));

        var latest = nodeData.latestSettings("company.yml").orElseThrow();
        assertEquals(2, latest.revision());
        assertEquals("level: [1]", latest.content());
        assertEquals(Map.of("company.yml", 2, "user.yml", 1), nodeData.settingsRevisions());
        assertTrue(nodeData.latestSettings("order.yml").isEmpty());
    }

    @Test
    void startingRecordsWhenTheNodeWasSeen() {
        var nodes = nodeData.nodes();

        assertEquals(1, nodes.size());
        var node = nodes.getFirst();
        assertEquals(nodeData.nodeUid(), node.uid());
        assertEquals(NodeType.PRIMARY, node.type());
        assertEquals(VERSION, node.version());
        assertTrue(node.seen().isPresent());
    }

    @Test
    void aStalePrimaryIsNamedAndCanBeRemoved() {
        insertStalePrimary();

        var failure = assertThrows(IllegalStateException.class, () -> nodeData.assertPrimaryNode(plugin));
        assertTrue(failure.getMessage().contains(stale.toString()), failure.getMessage());
        assertTrue(failure.getMessage().contains(nodeData.nodeUid().toString()), failure.getMessage());
        assertTrue(nodeData.nodes().stream()
                .filter(node -> node.uid().equals(stale))
                .findFirst()
                .orElseThrow()
                .seen()
                .isEmpty());

        assertEquals(ANodeData.Removal.REMOVED, nodeData.removeNode(stale));

        assertEquals(
                List.of(nodeData.nodeUid()),
                nodeData.nodes().stream().map(Node::uid).toList());
        assertEquals(0, SqLiteTestDatabase.count("node_configuration", "path = 'config.yml'"));
        assertDoesNotThrow(() -> nodeData.assertPrimaryNode(plugin));
    }

    @Test
    void theNodeOfThisServerIsKept() {
        assertEquals(ANodeData.Removal.CURRENT, nodeData.removeNode(nodeData.nodeUid()));
        assertEquals(1, nodeData.nodes().size());
    }

    @Test
    void unknownNodesAreReported() {
        assertEquals(ANodeData.Removal.UNKNOWN, nodeData.removeNode(UUID.randomUUID()));
    }

    private void insertStalePrimary() {
        var id = query("INSERT INTO node(uid, type, version) VALUES (?, 'PRIMARY', ?) RETURNING id")
                .single(call().bind(stale, UUID_BYTES).bind(VERSION))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        query("INSERT INTO node_configuration(node_id, path, content) VALUES (?, 'config.yml', '')")
                .single(call().bind(id))
                .insert();
    }

    private static Plugin plugin() {
        var meta = (PluginMeta) Proxy.newProxyInstance(
                PluginMeta.class.getClassLoader(),
                new Class<?>[] {PluginMeta.class},
                (proxy, method, args) -> "getVersion".equals(method.getName()) ? VERSION : null);
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[] {Plugin.class},
                (proxy, method, args) -> "getPluginMeta".equals(method.getName()) ? meta : null);
    }
}
