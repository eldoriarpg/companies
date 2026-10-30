/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.ServerEventChanged;

import java.util.function.Consumer;

/**
 * Tells every node at once that a server event was created, started, closed or changed, so it reads its events again
 * instead of waiting for its cache to expire.
 * <p>
 * The node that changed an event signals through here. While the bus delivers at once, the signal is published and
 * every node, the sender included, handles it when it arrives. Without a bus that delivers at once, such as a single
 * server or a network without messaging, only the sending node handles it, and the other nodes see the change once
 * their cache of events expires.
 * <p>
 * Like every node message, the signal is not stored: the events stay in the database, and a lost signal costs the
 * other nodes the lifetime of their cache, never an event.
 */
public final class EventNetwork {
    private final NodeBus bus;
    private final Consumer<ServerEventChanged> local;

    /**
     * @param bus   the bus of this node
     * @param local handles a change on this node
     */
    public EventNetwork(NodeBus bus, Consumer<ServerEventChanged> local) {
        this.bus = bus;
        this.local = local;
    }

    /**
     * Handles the changes of every node on this node.
     *
     * @return this
     */
    public EventNetwork start() {
        bus.subscribe(ServerEventChanged.class, (sender, message) -> local.accept(message));
        return this;
    }

    /**
     * Tells every node about a change.
     *
     * @param message the change
     */
    public void changed(ServerEventChanged message) {
        if (bus.realtime()) bus.publish(message);
        else local.accept(message);
    }
}
