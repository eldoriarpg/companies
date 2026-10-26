/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.components.order.IOrderDraft;
import de.eldoria.companies.components.order.OrderSource;
import de.eldoria.companies.data.repository.AOrderData;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Places orders on behalf of the server. A system order belongs to {@link SystemOwner}, is not charged (its fee is 0,
 * the server funds it), ignores the limits of active orders, and remembers the rule that placed it. The rule is noted
 * once the order exists, so listeners of the order's creation event see the order before its rule; a rule that could
 * not be noted is logged and the order stays.
 */
public final class SystemOrders {
    private final OrderCreationService creation;
    private final AOrderData orderData;
    private final Function<UUID, OfflinePlayer> players;
    private final Logger logger;

    /**
     * @param creation  creates orders
     * @param orderData order repository
     * @param players   an offline player by id, for the server's owner id
     * @param logger    reports a rule that could not be noted
     */
    public SystemOrders(
            OrderCreationService creation, AOrderData orderData, Function<UUID, OfflinePlayer> players, Logger logger) {
        this.creation = creation;
        this.orderData = orderData;
        this.players = players;
        this.logger = logger;
    }

    /**
     * Places a system order.
     *
     * @param draft the order
     * @param rule  the id of the rule placing it
     * @return the created order or why it was not created
     */
    public CompletableFuture<OrderCreationService.Outcome> place(IOrderDraft draft, String rule) {
        return creation.create(players.apply(SystemOwner.ID), draft, OrderSource.SYSTEM, false, false)
                .thenApply(outcome -> {
                    if (outcome instanceof OrderCreationService.Created created) remember(created, rule);
                    return outcome;
                });
    }

    private void remember(OrderCreationService.Created created, String rule) {
        try {
            orderData.systemRule(created.order().id(), rule);
        } catch (RuntimeException e) {
            logger.log(
                    Level.WARNING,
                    "Could not note the rule of the system order "
                            + created.order().id(),
                    e);
        }
    }
}
