/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A signal one node sends to every node of the network, itself included.
 * <p>
 * Messages carry signals, never the only copy of data: the database stays the source of truth, and a lost message
 * costs a delay until the next poll or cache expiry, never data. The node that sent a message is part of the envelope
 * (see {@link NodeMessageCodec}), not of the message.
 */
public sealed interface NodeMessage {
    /**
     * A notification was stored pending for a player. The node the player is online on claims and shows it at once.
     *
     * @param entry  id of the inbox entry
     * @param player recipient of the notification
     */
    record InboxNew(long entry, UUID player) implements NodeMessage {}

    /**
     * A player joined or left the sending node.
     *
     * @param player the player
     * @param online true when the player joined, false when they left
     */
    record PlayerPresence(UUID player, boolean online) implements NodeMessage {}

    /**
     * Data behind a cache changed. Receivers drop the key, or the whole cache when no key is given.
     *
     * @param cache name of the cache, one of the names in {@link NodeCaches}
     * @param key   key of the changed value, null for every value
     */
    record CacheInvalidate(String cache, @Nullable String key) implements NodeMessage {
        /**
         * @return the key, empty for every value
         */
        public Optional<String> changed() {
            return Optional.ofNullable(key);
        }
    }

    /**
     * An order completed or a company changed its level, so rankings and their placeholders are outdated.
     */
    record RankingChanged() implements NodeMessage {}

    /**
     * An order was created, claimed, released or removed, so the counts of board signs are outdated.
     */
    record BoardsChanged() implements NodeMessage {}

    /**
     * A node stored a revision of a shared file. Nodes that applied an older one take it and run only the reload work
     * its changed settings need.
     *
     * @param path     the file, such as {@code company.yml}
     * @param revision its revision
     */
    record SettingsChanged(String path, int revision) implements NodeMessage {}

    /**
     * The sending node is alive, with every player online on it. Sent regularly; replaces what was known about the
     * players of that node.
     *
     * @param players players online on the sending node
     * @param bus     the {@link NodeBus.Transport#id()} the sending node uses, null when not told
     */
    record Heartbeat(List<UUID> players, @Nullable String bus) implements NodeMessage {
        /**
         * @param players players online on the sending node
         * @param bus     the transport the sending node uses, null when not told
         */
        public Heartbeat {
            players = List.copyOf(players);
        }

        /**
         * A heartbeat that does not tell the transport.
         *
         * @param players players online on the sending node
         */
        public Heartbeat(List<UUID> players) {
            this(players, null);
        }

        /**
         * @return the transport the sending node uses, empty when not told or unknown here
         */
        public Optional<NodeBus.Transport> transport() {
            return NodeBus.Transport.of(bus);
        }
    }

    /**
     * The sending node shuts down. Its players count as offline.
     */
    record NodeStopped() implements NodeMessage {}
}
