/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.wrapper.nodes.Node;
import de.eldoria.eldoutilities.config.ConfigKey;
import de.eldoria.eldoutilities.config.JacksonConfig;
import de.eldoria.eldoutilities.config.exceptions.ConfigurationException;
import de.eldoria.eldoutilities.utils.Version;
import org.bukkit.plugin.Plugin;

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
     * Updates the state of the current node.
     *
     * @param plugin plugin
     */
    public void updateNode(Plugin plugin) {
        updateNode(nodeSettings.nodeUid(), nodeSettings.nodeType(), plugin.getDescription().getVersion());
    }

    protected abstract void updateNode(UUID nodeId, NodeType type, String version);

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
            String nodes = mainNodes.stream().map(node -> node.uid().toString()).collect(Collectors.joining(", "));
            throw new IllegalStateException("Multiple nodes are set as primary. Only one node is allowed to be a primary node. Nodes marked as primary: " + nodes);
        }
        Node node = mainNodes.get(0);
        if (Version.parse(node.version()).isNewer(Version.parse(plugin.getDescription().getVersion()))) {
            throw new IllegalStateException("Primary node is on version " + node.version() + ". However this node is just on "
                                            + plugin.getDescription().getVersion() + ". Please update this node.");
        }
        if (Version.parse(node.version()).isOlder(Version.parse(plugin.getDescription().getVersion()))) {
            throw new IllegalStateException("Primary node is on version " + node.version() + ". However this node already on "
                                            + plugin.getDescription().getVersion() + ". Please update primary first.");
        }
    }

    /**
     * Get all nodes marked as main node.
     * <p>
     * Ideally there is only one main node at a time.
     *
     * @return list of main nodes
     */
    public abstract List<Node> getPrimaryNodes();

    public final <T> T loadPrimaryConfiguration(ConfigKey<T> key, JacksonConfig<?> config) {
        var content = loadPrimaryConfiguration(key.path().toString());
        if (content.isEmpty()) new IllegalStateException("No configuration present. Did you start the main node once already?");
        try {
            return config.reader().readValue(content.get(), key.configClazz());
        } catch (JsonProcessingException e) {
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

    public void savePrimaryConfiguration(ConfigKey<?> key, JacksonConfig<?> config) {
        try {
            String content = config.writer().writeValueAsString(config.secondary(key));
            savePrimaryConfiguration(key.path().toString(), content);
        } catch (JsonProcessingException e) {
            throw new ConfigurationException("Could not parse configuration " + key.path(), e);
        }
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
}
