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

/**
 * Places orders on behalf of the server. A system order belongs to {@link SystemOwner}, is not charged (its fee is 0,
 * the server funds it), ignores the limits of active orders, and remembers the rule that placed it.
 */
public final class SystemOrders {
    private final OrderCreationService creation;
    private final AOrderData orderData;
    private final Function<UUID, OfflinePlayer> players;

    /**
     * @param creation  creates orders
     * @param orderData order repository
     * @param players   an offline player by id, for the server's owner id
     */
    public SystemOrders(OrderCreationService creation, AOrderData orderData, Function<UUID, OfflinePlayer> players) {
        this.creation = creation;
        this.orderData = orderData;
        this.players = players;
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
                    if (outcome instanceof OrderCreationService.Created created) {
                        orderData.systemRule(created.order().id(), rule);
                    }
                    return outcome;
                });
    }
}
