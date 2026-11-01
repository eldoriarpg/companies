/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.price;

/**
 * Who set a shop price.
 */
public enum ShopOrigin {
    /**
     * A player's shop, such as a chest shop or an auction of a player.
     */
    PLAYER,
    /**
     * A shop an admin set up in the world, such as an admin sign, an admin NPC or an unlimited chest.
     */
    ADMIN,
    /**
     * A price configured for the server, such as an item of a GUI shop.
     */
    SERVER
}
