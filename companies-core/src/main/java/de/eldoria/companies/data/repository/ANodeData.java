/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.wrapper.nodes.Node;
import de.eldoria.companies.util.Times;
import de.eldoria.eldoutilities.config.JacksonConfig;
import de.eldoria.eldoutilities.utils.Version;
import dev.chojo.ocular.components.Format;
import dev.chojo.ocular.dataformats.YamlDataFormat;
import dev.chojo.ocular.exceptions.ConfigurationException;
import dev.chojo.ocular.key.Key;
import org.bukkit.plugin.Plugin;
import tools.jackson.core.JacksonException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class ANodeData {
    protected final NodeSettings nodeSettings;
    private int nodeId = -1;

    public ANodeData(NodeSettings nodeSettings) {
        this.nodeSettings = nodeSettings;
    }

    /**
     * Updates the state of the current node and records it as seen now.
     *
     * @param plugin plugin
     */
    public void updateNode(Plugin plugin) {
        updateNode(
                nodeSettings.nodeUid(),
                nodeSettings.nodeType(),
                plugin.getPluginMeta().getVersion(),
                Times.now());
    }

    protected abstract void updateNode(UUID nodeId, NodeType type, String version, LocalDateTime seen);

    /**
     * Every node registered in the database, in the order they were registered.
     *
     * @return the nodes
     */
    public abstract List<Node> nodes();

    /**
     * Removes a node and the configuration it stored. The node of this server is never removed.
     *
     * @param uid uid of the node
     * @return what happened
     */
    public Removal removeNode(UUID uid) {
        if (uid.equals(nodeUid())) return Removal.CURRENT;
        return deleteNode(uid) ? Removal.REMOVED : Removal.UNKNOWN;
    }

    /**
     * Deletes a node and the configuration it stored.
     *
     * @param uid uid of the node
     * @return true when a node was deleted
     */
    protected abstract boolean deleteNode(UUID uid);

    /**
     * Check whether there is more than one node marked as main.
     * <p>
     * Should always be called after {@link #updateNode(Plugin)}
     *
     * @throws IllegalStateException when there is more than one or none at all. Or if the primary node is on a newer or older version
     */
    public void assertPrimaryNode(Plugin plugin) {
        List<Node> mainNodes = getPrimaryNodes();
        if (mainNodes.isEmpty()) {
            throw new IllegalStateException("No primary node is present. Set one node to PRIMARY in node.yml");
        }
        if (mainNodes.size() > 1) {
            String nodes = mainNodes.stream().map(ANodeData::describe).collect(Collectors.joining(", "));
            throw new IllegalStateException(
                    "Multiple nodes are set as primary. Only one node is allowed to be a primary"
                            + " node. This node is " + nodeUid() + ". Nodes marked as primary: " + nodes
                            + ". Remove the ones that no longer run with /companyadmin node remove <uid>.");
        }
        Node node = mainNodes.get(0);
        if (Version.parse(node.version())
                .isNewer(Version.parse(plugin.getPluginMeta().getVersion()))) {
            throw new IllegalStateException(
                    "Primary node is on version " + node.version() + ". However this node is just on "
                            + plugin.getPluginMeta().getVersion() + ". Please update this node.");
        }
        if (Version.parse(node.version())
                .isOlder(Version.parse(plugin.getPluginMeta().getVersion()))) {
            throw new IllegalStateException(
                    "Primary node is on version " + node.version() + ". However this node already on "
                            + plugin.getPluginMeta().getVersion() + ". Please update primary first.");
        }
    }

    private static String describe(Node node) {
        return node.uid() + " (version " + node.version() + ", last seen "
                + node.seen().map(LocalDateTime::toString).orElse("unknown") + ")";
    }

    /**
     * Get all nodes marked as main node.
     * <p>
     * Ideally there is only one main node at a time.
     *
     * @return list of main nodes
     */
    public abstract List<Node> getPrimaryNodes();

    public final <T> T loadPrimaryConfiguration(Key<T> key, JacksonConfig<?> config) {
        var content = loadPrimaryConfiguration(key.path().toString())
                .orElseThrow(() -> new IllegalStateException(
                        "No configuration present. Did you start the main node once already?"));
        try {
            return yaml(config).reader().readValue(content, key.configClazz());
        } catch (JacksonException e) {
            throw new ConfigurationException("Could not parse configuration " + key.path(), e);
        }
    }

    /**
     * Load the configuration of the path from the node marked as main.
     *
     * @param path path
     * @return configuration as string
     */
    protected abstract Optional<String> loadPrimaryConfiguration(String path);

    public void savePrimaryConfiguration(Key<?> key, JacksonConfig<?> config) {
        savePrimaryCopy(key, primaryCopy(key, config));
    }

    /**
     * Writes a configuration file as the database copy holds it, so it can be taken now and saved later.
     *
     * @param key    the file
     * @param config the configuration holding it
     * @return the file's content
     */
    public String primaryCopy(Key<?> key, JacksonConfig<?> config) {
        try {
            return yaml(config).writer().writeValueAsString(config.secondary(key));
        } catch (JacksonException e) {
            throw new ConfigurationException("Could not parse configuration " + key.path(), e);
        }
    }

    /**
     * Saves the database copy of a configuration file taken with {@link #primaryCopy(Key, JacksonConfig)}.
     *
     * @param key     the file
     * @param content the file's content
     */
    public void savePrimaryCopy(Key<?> key, String content) {
        savePrimaryConfiguration(key.path().toString(), content);
    }

    private static Format<?, ?> yaml(JacksonConfig<?> config) {
        return new Format<>(new YamlDataFormat(), config);
    }

    /**
     * Saves the configuration file as string.
     *
     * @param path    path
     * @param content configuration content
     */
    protected abstract void savePrimaryConfiguration(String path, String content);

    protected int getNodeId() {
        if (nodeId == -1) {
            nodeId = retrieveNodeId().orElse(-1);
        }
        return nodeId;
    }

    protected abstract Optional<Integer> retrieveNodeId();

    public NodeType nodeType() {
        return nodeSettings.nodeType();
    }

    public UUID nodeUid() {
        return nodeSettings.nodeUid();
    }

    /**
     * What removing a node did.
     */
    public enum Removal {
        /**
         * The node was removed.
         */
        REMOVED,
        /**
         * No node has that uid.
         */
        UNKNOWN,
        /**
         * The node is the one of this server and was kept.
         */
        CURRENT
    }
}
