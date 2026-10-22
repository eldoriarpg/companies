/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
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
    API
}
