/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.nodes.NodeMessage.OfferAnnounced;
import de.eldoria.companies.nodes.OfferNetwork;

import java.util.List;
import java.util.logging.Logger;

/**
 * The primary's part of the announcements to the players: it sends what happened to every node, which shows it to its
 * players through its {@link OfferAnnouncer}, and logs it. Progress is not shown in game.
 */
public final class NetworkAnnouncer implements EventOfferService.Announcer {
    private final OfferNetwork network;
    private final Logger logger;

    /**
     * @param network sends the announcements to every node
     * @param logger  logs what happened
     */
    public NetworkAnnouncer(OfferNetwork network, Logger logger) {
        this.network = network;
        this.logger = logger;
    }

    @Override
    public void started(EventOffer offer) {
        logger.info("The event offer #" + offer.id() + " (" + offer.name() + ") started.");
        network.announce(new OfferAnnounced(offer.id(), OfferAnnouncer.STARTED));
    }

    @Override
    public void reached(EventOffer offer) {
        logger.info("The event offer #" + offer.id() + " (" + offer.name() + ") reached its goal of " + offer.amount()
                + " items.");
        network.announce(new OfferAnnounced(offer.id(), OfferAnnouncer.REACHED));
    }

    @Override
    public void contestClosed(EventOffer offer, List<ContestEntry> ranking, List<ContestPrize> prizes) {
        logger.info("The contest #" + offer.id() + " (" + offer.name() + ") closed as " + offer.state() + " with "
                + offer.delivered() + " of " + offer.amount() + " items.");
        network.announce(new OfferAnnounced(offer.id(), OfferAnnouncer.CLOSED));
    }

    @Override
    public void contestCancelled(EventOffer offer) {
        logger.info("The contest #" + offer.id() + " (" + offer.name() + ") was cancelled.");
        network.announce(new OfferAnnounced(offer.id(), OfferAnnouncer.CANCELLED));
    }
}
