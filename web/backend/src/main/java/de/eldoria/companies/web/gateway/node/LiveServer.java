/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

import de.eldoria.companies.web.api.handshake.NodeInfo;
import de.eldoria.companies.web.api.handshake.NodeRole;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The connected nodes of one server. Data requests go to the primary node first and to any other ready node
 * otherwise; nodes on a discontinued version answer only status and are never asked. The server also knows on which
 * node each player with a dashboard session is online, as the nodes report it, so actions that move items reach the
 * node holding the player's inventory.
 */
public final class LiveServer {
    private final String id;
    private final Map<UUID, NodeLink> nodes = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> presence = new ConcurrentHashMap<>();

    /**
     * @param id the server id
     */
    public LiveServer(String id) {
        this.id = id;
    }

    /**
     * @return the server id
     */
    public String id() {
        return id;
    }

    /**
     * @return every welcomed node, ready or discontinued
     */
    public Collection<NodeLink> nodes() {
        return List.copyOf(nodes.values());
    }

    /**
     * @return the nodes that take requests
     */
    public List<NodeLink> ready() {
        return nodes.values().stream()
                .filter(link -> link.state() == NodeLink.State.READY)
                .toList();
    }

    /**
     * @return the node a data request goes to: the primary when it is ready, any ready node otherwise
     */
    public Optional<NodeLink> route() {
        return ready().stream()
                .min(Comparator.comparing((NodeLink link) -> link.agreed().role() != NodeRole.PRIMARY)
                        .thenComparing(NodeLink::connected));
    }

    /**
     * @return the ready nodes as they introduced themselves, primary first, then by uid
     */
    public List<NodeInfo> nodeInfos() {
        return ready().stream()
                .map(link -> link.agreed().hello().node())
                .sorted(Comparator.comparing((NodeInfo node) -> node.role() != NodeRole.PRIMARY)
                        .thenComparing(NodeInfo::uid))
                .toList();
    }

    /**
     * @return the primary node when it is ready, nothing otherwise
     */
    public Optional<NodeLink> primary() {
        return ready().stream()
                .filter(link -> link.agreed().role() == NodeRole.PRIMARY)
                .findFirst();
    }

    /**
     * @param node a node's uid
     * @return that node when it is ready, nothing otherwise
     */
    public Optional<NodeLink> exactly(UUID node) {
        NodeLink link = nodes.get(node);
        return link != null && link.state() == NodeLink.State.READY ? Optional.of(link) : Optional.empty();
    }

    /**
     * @param node a node's uid
     * @return that node when it is ready, otherwise the node {@link #route()} picks
     */
    public Optional<NodeLink> routeTo(UUID node) {
        NodeLink link = nodes.get(node);
        if (link != null && link.state() == NodeLink.State.READY) return Optional.of(link);
        return route();
    }

    /**
     * @param player a player
     * @return the node the player is online on when it is ready, otherwise the node {@link #route()} picks, which
     * then refuses what needs the player
     */
    public Optional<NodeLink> routeFor(UUID player) {
        UUID node = presence.get(player);
        return node == null ? route() : routeTo(node);
    }

    /**
     * @param player a player
     * @return whether the player is online on a node of the server
     */
    public boolean online(UUID player) {
        return presence.containsKey(player);
    }

    /**
     * Notes where a player is, as a node reported it. A node reporting that a player left forgets the player only when
     * the player was last seen on that node, so a switch to another node that is reported first is kept.
     *
     * @param player the player
     * @param node the reporting node's uid
     * @param online whether the player is now online on that node
     * @return whether the player's online state changed
     */
    boolean present(UUID player, UUID node, boolean online) {
        if (online) return presence.put(player, node) == null;
        return presence.remove(player, node);
    }

    /**
     * Forgets the players of a node that went away.
     *
     * @param node the node's uid
     * @return the players that are no longer online anywhere
     */
    List<UUID> forget(UUID node) {
        List<UUID> gone = presence.entrySet().stream()
                .filter(entry -> entry.getValue().equals(node))
                .map(Map.Entry::getKey)
                .toList();
        gone.forEach(player -> presence.remove(player, node));
        return gone;
    }

    /**
     * @return any welcomed node, for the server's version status: a ready one when there is one
     */
    public Optional<NodeLink> representative() {
        return route().or(() -> nodes.values().stream().findFirst());
    }

    /**
     * @param link a welcomed node
     * @return the node it replaces, a previous connection of the same uid
     */
    @Nullable
    NodeLink add(NodeLink link) {
        return nodes.put(link.agreed().uid(), link);
    }

    /**
     * @param link a node that went away
     * @return whether it was this server's current connection of its uid
     */
    boolean remove(NodeLink link) {
        return nodes.remove(link.agreed().uid(), link);
    }

    /**
     * @return whether no node is connected
     */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }
}
