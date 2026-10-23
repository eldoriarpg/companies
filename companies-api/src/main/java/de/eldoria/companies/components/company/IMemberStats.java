/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import java.util.UUID;

/**
 * What one worker contributed to a company in a time range.
 */
public interface IMemberStats {
    /**
     * The worker. This may be a former member of the company.
     *
     * @return id of the worker
     */
    UUID worker();

    /**
     * Items the worker delivered to orders that were completed in the range.
     *
     * @return number of items
     */
    int items();

    /**
     * Money paid out to the worker for completed orders, after tax. Payments a listener cancelled are not counted.
     *
     * @return earned money
     */
    double earned();

    /**
     * Completed orders the worker delivered to.
     *
     * @return number of orders
     */
    int orders();
}
