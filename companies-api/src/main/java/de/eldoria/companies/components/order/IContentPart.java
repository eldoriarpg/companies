/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.order;

import java.util.UUID;

/**
 * The share one worker delivered to a line of an order.
 */
public interface IContentPart {
    /**
     * The worker who delivered.
     *
     * @return id of the worker
     */
    UUID worker();

    /**
     * How many items the worker delivered to the line.
     *
     * @return amount
     */
    int amount();
}
