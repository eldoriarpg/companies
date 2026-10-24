/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import org.bukkit.entity.Player;

/**
 * Moves between the pages and the entries of one history.
 */
public interface HistoryNavigation {
    /**
     * Shows a page of the history.
     *
     * @param viewer viewing player
     * @param page   number of the page, from one
     */
    void page(Player viewer, int page);

    /**
     * Shows one entry of the history.
     *
     * @param viewer     viewing player
     * @param entry      id of the entry
     * @param returnPage page the entry's back button returns to
     */
    void entry(Player viewer, long entry, int returnPage);
}
