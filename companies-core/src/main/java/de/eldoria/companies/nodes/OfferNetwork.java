/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.nodes;

import de.eldoria.companies.nodes.NodeMessage.OfferAnnounced;

import java.util.function.Consumer;

/**
 * Brings the announcements of event offers to the players of every node.
 * <p>
 * The primary announces through here. While the bus delivers at once, the announcement is published and every node,
 * the primary included, shows it to its players when it arrives. Without a bus that delivers at once, such as a single
 * server or a network without messaging, only the players of the primary see it.
 * <p>
 * Like every node message, an announcement is a signal and not stored: one the bus drops, such as during a reconnect
 * that the bus did not notice yet, is shown on no node, the primary included. Nothing else depends on it; the offer's
 * state and its Discord posts are kept apart from it.
 */
public final class OfferNetwork {
    private final NodeBus bus;
    private final Consumer<OfferAnnounced> local;

    /**
     * @param bus   the bus of this node
     * @param local shows an announcement to the players of this node
     */
    public OfferNetwork(NodeBus bus, Consumer<OfferAnnounced> local) {
        this.bus = bus;
        this.local = local;
    }

    /**
     * Shows the announcements of every node to the players of this node.
     *
     * @return this
     */
    public OfferNetwork start() {
        bus.subscribe(OfferAnnounced.class, (sender, message) -> local.accept(message));
        return this;
    }

    /**
     * Announces to the players of every node.
     *
     * @param message the announcement
     */
    public void announce(OfferAnnounced message) {
        if (bus.realtime()) bus.publish(message);
        else local.accept(message);
    }
}
