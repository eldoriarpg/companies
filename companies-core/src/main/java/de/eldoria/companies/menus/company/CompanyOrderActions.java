/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import org.bukkit.entity.Player;

/**
 * What the buttons of the company's order menus do. Every action checks membership, permission and order again, as
 * the typed command does.
 * <p>
 * The same menus show the orders a player works on alone; those actions run the solo commands instead.
 */
public interface CompanyOrderActions {
    /**
     * The command the chat menu suggests for delivering another amount, followed by order id and line.
     *
     * @return command with leading slash and without trailing space
     */
    default String deliverCommand() {
        return "/company order deliver";
    }

    /**
     * The locale key of the heading of the order list.
     *
     * @return locale key
     */
    default String listTitle() {
        return "company.order.list.orders";
    }

    /**
     * Shows an order of the player's company.
     *
     * @param player viewing member
     * @param id     id of the order
     */
    void info(Player player, int id);

    /**
     * Shows the orders the player's company works on.
     *
     * @param player viewing member
     */
    void list(Player player);

    /**
     * Opens the order search.
     *
     * @param player searching member
     */
    void search(Player player);

    /**
     * Delivers items to one line of an order.
     *
     * @param player delivering member
     * @param id     id of the order
     * @param line   line of the order, from one
     * @param amount the most items to deliver; {@link Integer#MAX_VALUE} for what the line misses
     */
    void deliver(Player player, int id, int line, int amount);

    /**
     * Delivers every matching item to every line of an order.
     *
     * @param player delivering member
     * @param id     id of the order
     */
    void deliverAll(Player player, int id);

    /**
     * Accepts an order for the player's company.
     *
     * @param player accepting member
     * @param id     id of the order
     */
    void accept(Player player, int id);

    /**
     * Asks to confirm giving an order back.
     *
     * @param player aborting member
     * @param id     id of the order
     */
    void abort(Player player, int id);
}
