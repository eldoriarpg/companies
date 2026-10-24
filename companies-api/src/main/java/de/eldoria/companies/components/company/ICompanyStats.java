/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import java.time.LocalDateTime;

/**
 * Statistics of a company.
 */
public interface ICompanyStats {
    /**
     * The id of the company.
     *
     * @return id
     */
    int id();

    /**
     * The name of the company.
     *
     * @return name
     */
    String name();

    /**
     * When the company was founded.
     *
     * @return founding time in UTC
     */
    LocalDateTime founded();

    /**
     * The current number of members, the owner included.
     *
     * @return member count
     */
    int memberCount();

    /**
     * The number of completed orders.
     *
     * @return order count
     */
    int orderCount();

    /**
     * The money earned with completed orders, before tax.
     *
     * @return earned money
     */
    double price();

    /**
     * The number of items delivered to completed orders.
     *
     * @return delivered items
     */
    int deliveredItems();
}
