/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.level;

/**
 * What a company needs to reach a level. A company needs all values.
 */
public interface ILevelRequirement {
    /**
     * Completed orders needed.
     *
     * @return order count
     */
    int orderCount();

    /**
     * Money earned with completed orders needed, before tax.
     *
     * @return money
     */
    double earnedMoney();

    /**
     * Items delivered to completed orders needed.
     *
     * @return item count
     */
    int deliveredItems();

    /**
     * Members needed.
     *
     * @return member count
     */
    int memberCount();
}
