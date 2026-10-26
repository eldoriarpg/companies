/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

/**
 * Where the creation of an order came from.
 */
public enum OrderSource {
    /**
     * A player created the order with {@code /order create}.
     */
    PLAYER,
    /**
     * A plugin created the order through {@link de.eldoria.companies.api.OrderApi#create}.
     */
    API,
    /**
     * A repeating order of a player posted its next copy. The copy is a normal order of that player.
     */
    REPEATING,
    /**
     * A player placed the order on the web dashboard. The player may be offline.
     */
    DASHBOARD,
    /**
     * The server placed the order itself, by a rule. Its owner is the server, not a player.
     */
    SYSTEM
}
