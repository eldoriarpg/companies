/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.SoloOrderDoneEvent;
import de.eldoria.companies.orders.SystemOwner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Counts a delivered chunk of a goal for its offer at once, on the node where it was completed, so the progress players
 * see moves with every delivery. The primary's {@link EventOfferService} settles the chunks this missed on its next
 * check; a chunk counts once either way.
 */
public final class ChunkDeliveries implements Listener {
    private final AOfferData offers;
    private final Clock clock;
    private final Executor executor;
    private final Logger logger;

    /**
     * @param offers   offer repository
     * @param clock    clock giving the current time in UTC
     * @param executor writes the delivery off the event's thread
     * @param logger   reports deliveries that could not be written
     */
    public ChunkDeliveries(AOfferData offers, Clock clock, Executor executor, Logger logger) {
        this.offers = offers;
        this.clock = clock;
        this.executor = executor;
        this.logger = logger;
    }

    /**
     * @param event a company completed an order
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDone(OrderDoneEvent event) {
        delivered(event.order());
    }

    /**
     * @param event a player working alone completed an order
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloDone(SoloOrderDoneEvent event) {
        delivered(event.order());
    }

    private void delivered(ISimpleOrder order) {
        if (!SystemOwner.is(order.owner())) return;
        var at = LocalDateTime.now(clock);
        executor.execute(() -> {
            try {
                offers.chunkDelivered(order.id(), at);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not count the delivered order " + order.id() + " for its offer", e);
            }
        });
    }
}
