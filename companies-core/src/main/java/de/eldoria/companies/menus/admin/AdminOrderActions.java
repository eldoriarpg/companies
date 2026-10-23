/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * What the buttons of the admin order menus do. The destructive actions ask for a confirmation first.
 */
public interface AdminOrderActions {
    /**
     * Shows the details of an order.
     *
     * @param viewer viewing admin
     * @param order  id of the order
     */
    void info(Player viewer, int order);

    /**
     * Asks to take a claimed order away from its company.
     *
     * @param viewer viewing admin
     * @param order  id of the order
     */
    void unclaim(Player viewer, int order);

    /**
     * Asks to delete an order.
     *
     * @param viewer viewing admin
     * @param order  id of the order
     * @param refund true to refund price and fee to the owner
     */
    void cancel(Player viewer, int order, boolean refund);

    /**
     * Asks to mark a claimed order delivered.
     *
     * @param viewer viewing admin
     * @param order  id of the order
     */
    void deliver(Player viewer, int order);

    /**
     * Gives an order more time.
     *
     * @param viewer  viewing admin
     * @param order   id of the order
     * @param entered hours as entered, checked by the action
     */
    void extend(Player viewer, int order, @Nullable String entered);
}
