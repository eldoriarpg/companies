/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.price;

/**
 * Which way an item goes in a shop, named from the player's point of view.
 */
public enum ShopSide {
    /**
     * The player buys the item and pays the shop.
     */
    BUY,
    /**
     * The player sells the item and the shop pays them.
     */
    SELL
}
