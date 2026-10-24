/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.level;

/**
 * What a company on a level may do.
 */
public interface ILevelSettings {
    /**
     * The maximum number of members.
     *
     * @return member limit
     */
    int maxMembers();

    /**
     * The maximum number of orders the company may work on at the same time.
     *
     * @return order limit
     */
    int maxOrders();
}
