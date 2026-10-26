/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.configuration.ChangeTracker;
import de.eldoria.companies.configuration.elements.NodeSettings;
import de.eldoria.companies.configuration.elements.NodeType;
import de.eldoria.companies.data.wrapper.nodes.AppliedSettings;
import de.eldoria.companies.data.wrapper.nodes.Node;
import de.eldoria.companies.data.wrapper.nodes.SettingsChange;
import de.eldoria.companies.data.wrapper.nodes.SettingsRevision;
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
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class ANodeData {
    private static final int PUBLISH_ATTEMPTS = 5;

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

    /**
     * Stores a shared file as its next revision, unless its content equals the latest one. Two nodes publishing at once
     * cannot take the same revision; the later one tries again on top of the earlier.
     *
     * @param path    the file, such as {@code company.yml}
     * @param content the file's content
     * @param actor   who changed it, such as an admin's name or {@code startup}
     * @return the new revision, empty when the content equals the latest revision
     */
    public OptionalInt publishSettings(String path, String content, String actor) {
        var hash = SettingsRevision.hash(content);
        for (var attempt = 0; attempt < PUBLISH_ATTEMPTS; attempt++) {
            var latest = latestSettings(path);
            if (latest.isPresent() && latest.get().hash().equals(hash)) return OptionalInt.empty();
            var next = latest.map(found -> found.revision() + 1).orElse(1);
            if (insertSettings(path, next, content, hash, actor, Times.now())) {
                appliedSettings(path, next);
                return OptionalInt.of(next);
            }
        }
        throw new IllegalStateException("Could not store a new revision of " + path + ".");
    }

    /**
     * Stores a shared file as the revision after the one an edit was based on. It is refused when another revision was
     * stored after that base meanwhile, so an edit never silently replaces a newer one.
     *
     * @param path    the file
     * @param base    the revision the edit started from
     * @param content the file's content
     * @param actor   who changed it
     * @return what happened
     */
    public PublishOutcome publishSettingsOn(String path, int base, String content, String actor) {
        var hash = SettingsRevision.hash(content);
        var latest = latestSettings(path);
        if (latest.isPresent() && latest.get().hash().equals(hash)) {
            return new PublishOutcome.Unchanged(latest.get().revision());
        }
        if (latest.map(SettingsRevision::revision).orElse(0) != base
                || !insertSettings(path, base + 1, content, hash, actor, Times.now())) {
            return new PublishOutcome.Conflict(
                    latestSettings(path).map(SettingsRevision::revision).orElse(base));
        }
        appliedSettings(path, base + 1);
        return new PublishOutcome.Published(base + 1);
    }

    /**
     * What storing an edit did.
     */
    public sealed interface PublishOutcome {
        /**
         * The edit is the new revision.
         *
         * @param revision its number
         */
        record Published(int revision) implements PublishOutcome {}

        /**
         * The latest revision holds the same content already.
         *
         * @param revision the latest revision
         */
        record Unchanged(int revision) implements PublishOutcome {}

        /**
         * Another revision was stored after the edit's base.
         *
         * @param latest the latest revision
         */
        record Conflict(int latest) implements PublishOutcome {}
    }

    /**
     * @param path   the file
     * @param offset how many of the newest revisions to skip
     * @param limit  how many to return
     * @return the file's revisions, newest first
     */
    public abstract List<SettingsChange> settingsHistory(String path, int offset, int limit);

    /**
     * @return the revision every node applied of every shared file
     */
    public abstract List<AppliedSettings> settingsApplied();

    /**
     * @param path     a shared file
     * @param revision one of its revisions
     * @return who stored that revision, where and when, empty when there is no such revision
     */
    public abstract Optional<SettingsChange> settingsChange(String path, int revision);

    /**
     * Stores a file the shared settings refer to, such as the dashboard icon, unless one of that name exists; the name
     * comes from the content, so an existing one holds the same. Earlier files are kept, so every revision keeps the file
     * it names, a rollback included; each is small.
     *
     * @param name    the name the settings use, such as {@code icon-1a2b3c4d5e6f}
     * @param content the file as base64
     * @param hash    the SHA-256 of the file as lower-case hex
     * @param actor   who stored it
     * @return whether a file of that name is stored now
     */
    public boolean storeAsset(String name, String content, String hash, String actor) {
        if (asset(name).isEmpty()) insertAsset(name, content, hash, actor);
        return asset(name).isPresent();
    }

    /**
     * @param name    the name the settings use
     * @param content the file as base64
     * @param hash    the SHA-256 of the file as lower-case hex
     * @param actor   who stored it
     */
    protected abstract void insertAsset(String name, String content, String hash, String actor);

    /**
     * @param name the name the settings use
     * @return the file as base64, empty when there is none of that name
     */
    public abstract Optional<String> asset(String name);

    /**
     * Stores the settings this node reads only at start that changed since it started.
     *
     * @param settings the settings, none when no restart is pending
     */
    public void restartPending(List<ChangeTracker.Setting> settings) {
        restartPending(
                nodeUid(),
                settings.stream().map(ChangeTracker.Setting::toString).collect(Collectors.joining(", ")));
    }

    /**
     * @param node     the node
     * @param settings the settings, joined, empty when no restart is pending
     */
    protected abstract void restartPending(UUID node, String settings);

    /**
     * @return the settings each node waits to restart for, by node, only nodes that wait
     */
    public abstract Map<UUID, String> pendingRestarts();

    /**
     * @param path     the file
     * @param revision a revision
     * @return that revision, empty when it does not exist
     */
    public abstract Optional<SettingsRevision> settingsAt(String path, int revision);

    /**
     * @param path the file
     * @return the revision this node last applied, empty before it applied one
     */
    public abstract OptionalInt appliedRevision(String path);

    /**
     * @param path the file
     * @return its latest revision, empty before the primary stored one
     */
    public abstract Optional<SettingsRevision> latestSettings(String path);

    /**
     * @return the latest revision of every stored file, by path
     */
    public abstract Map<String, Integer> settingsRevisions();

    /**
     * Notes that this node applied a revision of a file.
     *
     * @param path     the file
     * @param revision the revision
     */
    public abstract void appliedSettings(String path, int revision);

    /**
     * Stores a revision unless it exists already.
     *
     * @param path     the file
     * @param revision the revision
     * @param content  the content
     * @param hash     the content's hash
     * @param actor    who changed it
     * @param changed  when
     * @return whether it was stored, false when another node took that revision first
     */
    protected abstract boolean insertSettings(
            String path, int revision, String content, String hash, String actor, LocalDateTime changed);

    /**
     * The primary's copy of a configuration file as the database holds it.
     *
     * @param key the file
     * @return the file's content, empty before the primary wrote it
     */
    public Optional<String> primaryContent(Key<?> key) {
        return loadPrimaryConfiguration(key.path().toString());
    }

    public final <T> T loadPrimaryConfiguration(Key<T> key, JacksonConfig<?> config) {
        var content = loadPrimaryConfiguration(key.path().toString())
                .orElseThrow(() -> new IllegalStateException(
                        "No configuration present. Did you start the main node once already?"));
        return readSettings(key, config, content);
    }

    /**
     * Reads a shared file's content as the configuration does.
     *
     * @param key     the file
     * @param config  the configuration whose mapper reads it
     * @param content the content
     * @param <T>     type of the file
     * @return the file's settings
     */
    public final <T> T readSettings(Key<T> key, JacksonConfig<?> config, String content) {
        try {
            return yaml(config).reader().readValue(content, key.configClazz());
        } catch (JacksonException e) {
            throw new ConfigurationException("Could not parse configuration " + key.path(), e);
        }
    }

    /**
     * Writes a shared file's settings as the database copy holds them.
     *
     * @param key      the file
     * @param config   the configuration whose mapper writes it
     * @param settings the file's settings
     * @param <T>      type of the file
     * @return the file's content
     */
    public final <T> String writeSettings(Key<T> key, JacksonConfig<?> config, T settings) {
        try {
            return yaml(config).writer().writeValueAsString(settings);
        } catch (JacksonException e) {
            throw new ConfigurationException("Could not write configuration " + key.path(), e);
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
