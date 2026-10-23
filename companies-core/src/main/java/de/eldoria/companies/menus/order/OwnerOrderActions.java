/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * What the buttons of the owner's order menus do. Every action checks the order again, as the typed command does.
 */
public interface OwnerOrderActions {
    /**
     * Shows one order of the player.
     *
     * @param player owner of the order
     * @param id     id of the order
     */
    void info(Player player, int id);

    /**
     * Shows the orders of the player.
     *
     * @param player owner of the orders
     */
    void list(Player player);

    /**
     * Cancels an order nobody accepted yet.
     *
     * @param player owner of the order
     * @param id     id of the order
     */
    void cancel(Player player, int id);

    /**
     * Restarts the refund timer of an order nobody accepted yet.
     *
     * @param player owner of the order
     * @param id     id of the order
     */
    void extend(Player player, int id);

    /**
     * Asks to confirm raising the price of an order nobody accepted yet.
     *
     * @param player  owner of the order
     * @param id      id of the order
     * @param entered the increase as entered, refused unless it is a number above zero
     */
    void raise(Player player, int id, @Nullable String entered);

    /**
     * Hands a delivered order to its owner.
     *
     * @param player owner of the order
     * @param id     id of the order
     */
    void receive(Player player, int id);

    /**
     * Hands out the items of received orders which did not fit into the inventory.
     *
     * @param player owner of the items
     */
    void collect(Player player);

    /**
     * Shows the repeating order a copy belongs to.
     *
     * @param player owner of the series
     * @param id     id of the series
     */
    void series(Player player, int id);

    /**
     * Starts a new order.
     *
     * @param player player creating the order
     * @param name   name of the order as entered
     */
    void create(Player player, @Nullable String name);
}
