/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What the placeholders show about a player. Holds nothing about the companies working on the player's orders.
 *
 * @param player        id of the player
 * @param company       id of the player's company, or empty without one
 * @param role          role in the company, or empty without one
 * @param openOrders    own orders not delivered yet
 * @param orderLimit    most orders the player may have open at once
 * @param itemsWaiting  stored items waiting for the player, returned and not yet received ones
 * @param soloActive    orders the player works on alone right now
 * @param soloCompleted orders the player delivered alone
 */
public record PlayerView(
        UUID player,
        OptionalInt company,
        Optional<Role> role,
        int openOrders,
        int orderLimit,
        int itemsWaiting,
        int soloActive,
        int soloCompleted) {
    /**
     * The values of a player who never worked alone.
     *
     * @param player       id of the player
     * @param company      id of the player's company, or empty without one
     * @param role         role in the company, or empty without one
     * @param openOrders   own orders not delivered yet
     * @param orderLimit   most orders the player may have open at once
     * @param itemsWaiting stored items waiting for the player, returned and not yet received ones
     */
    public PlayerView(
            UUID player, OptionalInt company, Optional<Role> role, int openOrders, int orderLimit, int itemsWaiting) {
        this(player, company, role, openOrders, orderLimit, itemsWaiting, 0, 0);
    }
}
