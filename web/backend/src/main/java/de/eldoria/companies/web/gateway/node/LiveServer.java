/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.node;

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
 * otherwise; nodes on a discontinued version answer only status and are never asked.
 */
public final class LiveServer {
    private final String id;
    private final Map<UUID, NodeLink> nodes = new ConcurrentHashMap<>();

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
     * @param node a node's uid
     * @return that node when it is ready, otherwise the node {@link #route()} picks
     */
    public Optional<NodeLink> routeTo(UUID node) {
        NodeLink link = nodes.get(node);
        if (link != null && link.state() == NodeLink.State.READY) return Optional.of(link);
        return route();
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
