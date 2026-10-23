/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.components;

import org.bukkit.OfflinePlayer;

/**
 * An event that involves the owner of an order.
 * <p>
 * Orders are anonymous towards players: never show the owner to the members of the company working on the order.
 */
@FunctionalInterface
public interface OwnerProvider {
    /**
     * The player who placed the order.
     *
     * @return owner of the order
     */
    OfflinePlayer owner();
}
