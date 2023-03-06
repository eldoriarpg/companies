/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.level;

public interface ILevelRequirement {
    int orderCount();

    double earnedMoney();

    int deliveredItems();

    int memberCount();
}
