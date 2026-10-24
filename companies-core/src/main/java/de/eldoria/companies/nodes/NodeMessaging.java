/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.BoardsChanged;
import de.eldoria.companies.nodes.NodeMessage.CacheInvalidate;
import de.eldoria.companies.nodes.NodeMessage.ConfigSynced;
import de.eldoria.companies.nodes.NodeMessage.Heartbeat;
import de.eldoria.companies.nodes.NodeMessage.InboxNew;
import de.eldoria.companies.nodes.NodeMessage.NodeStopped;
import de.eldoria.companies.nodes.NodeMessage.PlayerPresence;
import de.eldoria.companies.nodes.NodeMessage.RankingChanged;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Acts on the messages of the other nodes and keeps this node's side of the bus running:
 * <ul>
 *     <li>{@code inbox.new}: the player's pending entries are claimed and shown here when the player is online here;
 *     the claim succeeds once across all nodes, so an entry is shown once.</li>
 *     <li>{@code player.presence}, {@code node.heartbeat}, {@code node.stopped}: kept in the {@link NetworkPresence}.
 *     A node uid the database does not know is reported once, since it points at a node of another network using
 *     the same Redis server or PostgreSQL database and channel prefix.</li>
 *     <li>{@code cache.invalidate}, {@code ranking.changed}, {@code boards.changed}: passed to the {@link NodeCaches}.
 *     </li>
 *     <li>{@code config.synced}: a secondary loads the configuration from the database and applies it.</li>
 * </ul>
 * A heartbeat with the players online here and the transport of this node is sent every
 * {@link NetworkPresence#HEARTBEAT}. When the bus changes its
 * state, the inbox polls change their interval and poll once. After a reconnect every cache is dropped once, since
 * messages may have been missed meanwhile.
 */
public final class NodeMessaging {
    private final NodeBus bus;
    private final NodeCaches caches;
    private final NetworkPresence presence;
    private final Logger logger;
    private final Set<UUID> reported = ConcurrentHashMap.newKeySet();
    private ScheduledFuture<?> heartbeat;

    /**
     * @param bus      the bus of this node
     * @param caches   the caches other nodes can invalidate
     * @param presence where the players of the network are
     * @param logger   reports unknown nodes and failures
     */
    public NodeMessaging(NodeBus bus, NodeCaches caches, NetworkPresence presence, Logger logger) {
        this.bus = bus;
        this.caches = caches;
        this.presence = presence;
        this.logger = logger;
    }

    /**
     * Subscribes to the messages, starts the heartbeat and follows the state of the bus.
     *
     * @param node  what this node does with the messages
     * @param timer runs the heartbeat
     */
    public void start(Node node, ScheduledExecutorService timer) {
        bus.subscribe(InboxNew.class, (sender, message) -> node.inbox().accept(message.player()));
        bus.subscribe(PlayerPresence.class, (sender, message) -> {
            checkKnown(sender, node.knownNodes());
            presence.presence(sender, message.player(), message.online());
        });
        bus.subscribe(Heartbeat.class, (sender, message) -> {
            checkKnown(sender, node.knownNodes());
            presence.heartbeat(sender, message.players(), message.transport());
        });
        bus.subscribe(NodeStopped.class, (sender, message) -> presence.stopped(sender));
        bus.subscribe(
                CacheInvalidate.class, (sender, message) -> caches.invalidate(message.cache(), message.changed()));
        bus.subscribe(
                RankingChanged.class, (sender, message) -> caches.invalidate(NodeCaches.RANKING, Optional.empty()));
        bus.subscribe(BoardsChanged.class, (sender, message) -> caches.invalidate(NodeCaches.BOARDS, Optional.empty()));
        bus.subscribe(ConfigSynced.class, (sender, message) -> {
            if (!node.primary()) node.configSynced().run();
        });
        bus.onStateChange(state -> stateChanged(state, node));
        heartbeat = timer.scheduleWithFixedDelay(
                () -> beat(node), 0, NetworkPresence.HEARTBEAT.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Tells the other nodes that this one stops, and closes the bus.
     */
    public void stop() {
        if (heartbeat != null) heartbeat.cancel(false);
        bus.publish(new NodeStopped());
        bus.close();
    }

    private void stateChanged(NodeBus.State state, Node node) {
        node.stateChanged().run();
        if (state != NodeBus.State.CONNECTED) {
            presence.clear();
            return;
        }
        caches.clear();
        beat(node);
    }

    private void beat(Node node) {
        try {
            presence.expire();
            if (bus.realtime())
                bus.publish(
                        new Heartbeat(node.onlineHere().get(), bus.transport().id()));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Could not send the node heartbeat.", e);
        }
    }

    private void checkKnown(UUID sender, Supplier<List<UUID>> known) {
        if (reported.contains(sender) || presence.alive(sender)) return;
        if (known.get().contains(sender)) return;
        if (reported.add(sender)) {
            logger.warning("Received a message from the node " + sender + ", which is not registered in this"
                    + " database. All nodes of a network must use the same channel prefix, and other networks on the"
                    + " same Redis server or PostgreSQL database need their own messaging.channelPrefix.");
        }
    }

    /**
     * What this node does with the messages of the others.
     *
     * @param primary      true on the primary node, which ignores {@code config.synced}
     * @param inbox        claims and shows the pending entries of a player when the player is online here; blocks
     * @param configSynced loads the configuration from the database and applies it; blocks
     * @param stateChanged runs whenever the bus changes its state, to change the poll interval and poll once
     * @param onlineHere   the players online on this node; blocks
     * @param knownNodes   the uids of the nodes registered in the database; blocks
     */
    public record Node(
            boolean primary,
            Consumer<UUID> inbox,
            Runnable configSynced,
            Runnable stateChanged,
            Supplier<List<UUID>> onlineHere,
            Supplier<List<UUID>> knownNodes) {}
}
