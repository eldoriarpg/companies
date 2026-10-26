/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.SoloOrderAcceptEvent;
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
 * Notes when a company or a player working alone takes an order of the server, on the node where it was taken, so
 * guessed orders learn how fast their orders go. Only the first outcome of an order counts.
 */
public final class GuessedOutcomes implements Listener {
    private final AOrderData orders;
    private final Clock clock;
    private final Executor executor;
    private final Logger logger;

    /**
     * @param orders   order repository
     * @param clock    clock giving the current time in UTC
     * @param executor writes the outcome off the event's thread
     * @param logger   reports outcomes that could not be written
     */
    public GuessedOutcomes(AOrderData orders, Clock clock, Executor executor, Logger logger) {
        this.orders = orders;
        this.clock = clock;
        this.executor = executor;
        this.logger = logger;
    }

    /**
     * @param event a company took an order
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAccept(OrderAcceptEvent event) {
        taken(event.order());
    }

    /**
     * @param event a player took an order alone
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSoloAccept(SoloOrderAcceptEvent event) {
        taken(event.order());
    }

    private void taken(ISimpleOrder order) {
        if (!SystemOwner.is(order.owner())) return;
        var at = LocalDateTime.now(clock);
        executor.execute(() -> {
            try {
                orders.systemOutcome(order.id(), GuessedLearning.TAKEN, at);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Could not note that the system order " + order.id() + " was taken", e);
            }
        });
    }
}
