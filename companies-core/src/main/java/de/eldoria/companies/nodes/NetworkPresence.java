/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which player is online on which node of the network, and which nodes were heard from lately, as told over the
 * node bus. Thread safe.
 * <p>
 * Every node sends a heartbeat with its players every {@link #HEARTBEAT}; joins and quits arrive in between. A node
 * not heard from for {@link #TIMEOUT} counts as gone, together with its players. Without a realtime bus nothing is
 * known and every lookup is empty.
 */
public final class NetworkPresence {
    /**
     * How often a node sends its heartbeat.
     */
    public static final Duration HEARTBEAT = Duration.ofSeconds(30);
    /**
     * How long a node counts as alive after its last heartbeat or message.
     */
    public static final Duration TIMEOUT = HEARTBEAT.multipliedBy(3);

    private final Map<UUID, UUID> players = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> nodes = new ConcurrentHashMap<>();
    private final Map<UUID, NodeBus.Transport> transports = new ConcurrentHashMap<>();
    private final Clock clock;

    /**
     * @param clock source of time for the heartbeats
     */
    public NetworkPresence(Clock clock) {
        this.clock = clock;
    }

    /**
     * A player joined or left a node.
     *
     * @param node   the node
     * @param player the player
     * @param online true when the player joined
     */
    public void presence(UUID node, UUID player, boolean online) {
        nodes.put(node, clock.instant());
        if (online) {
            players.put(player, node);
        } else {
            players.remove(player, node);
        }
    }

    /**
     * A node sent its heartbeat; its players replace the ones known for it.
     *
     * @param node   the node
     * @param online players online on the node
     */
    public void heartbeat(UUID node, Collection<UUID> online) {
        heartbeat(node, online, Optional.empty());
    }

    /**
     * A node sent its heartbeat; its players replace the ones known for it.
     *
     * @param node      the node
     * @param online    players online on the node
     * @param transport how the node sends its messages, empty when it did not tell
     */
    public void heartbeat(UUID node, Collection<UUID> online, Optional<NodeBus.Transport> transport) {
        nodes.put(node, clock.instant());
        transport.ifPresentOrElse(value -> transports.put(node, value), () -> transports.remove(node));
        var current = Set.copyOf(online);
        players.entrySet().removeIf(entry -> entry.getValue().equals(node) && !current.contains(entry.getKey()));
        current.forEach(player -> players.put(player, node));
    }

    /**
     * A node shut down; it and its players are gone.
     *
     * @param node the node
     */
    public void stopped(UUID node) {
        nodes.remove(node);
        transports.remove(node);
        players.values().removeIf(node::equals);
    }

    /**
     * Forgets every node and player, when the bus lost its connection and the knowledge gets outdated.
     */
    public void clear() {
        nodes.clear();
        transports.clear();
        players.clear();
    }

    /**
     * Forgets nodes not heard from within {@link #TIMEOUT}, together with their players.
     */
    public void expire() {
        var limit = clock.instant().minus(TIMEOUT);
        for (var entry : Map.copyOf(nodes).entrySet()) {
            if (entry.getValue().isBefore(limit)) stopped(entry.getKey());
        }
    }

    /**
     * @param node a node
     * @return true when the node was heard from within {@link #TIMEOUT}
     */
    public boolean alive(UUID node) {
        var seen = nodes.get(node);
        return seen != null && !seen.isBefore(clock.instant().minus(TIMEOUT));
    }

    /**
     * @param node a node
     * @return how the node sends its messages as told in its last heartbeat, empty when not alive or not told
     */
    public Optional<NodeBus.Transport> transport(UUID node) {
        return Optional.ofNullable(transports.get(node)).filter(value -> alive(node));
    }

    /**
     * @param player a player
     * @return the node the player is online on, empty when unknown or offline
     */
    public Optional<UUID> nodeOf(UUID player) {
        return Optional.ofNullable(players.get(player)).filter(this::alive);
    }
}
