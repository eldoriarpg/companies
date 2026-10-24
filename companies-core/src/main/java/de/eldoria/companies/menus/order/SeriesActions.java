/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import org.bukkit.entity.Player;

/**
 * What the buttons of the repeating order menus do. Every action checks the series again, as the typed command does.
 */
public interface SeriesActions {
    /**
     * Shows the repeating orders of the player.
     *
     * @param player owner of the series
     */
    void list(Player player);

    /**
     * Shows one repeating order of the player.
     *
     * @param player owner of the series
     * @param id     id of the series
     */
    void info(Player player, int id);

    /**
     * Asks to confirm stopping a repeating order.
     *
     * @param player owner of the series
     * @param id     id of the series
     */
    void stop(Player player, int id);

    /**
     * Resumes a stopped repeating order.
     *
     * @param player owner of the series
     * @param id     id of the series
     */
    void resume(Player player, int id);

    /**
     * Shows the current copy of a repeating order.
     *
     * @param player owner of the order
     * @param id     id of the order
     */
    void order(Player player, int id);
}
