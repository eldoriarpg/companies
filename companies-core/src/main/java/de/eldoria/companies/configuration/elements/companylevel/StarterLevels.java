/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The level ladder a fresh installation starts with. Existing configurations keep their own levels.
 */
public final class StarterLevels {
    private StarterLevels() {}

    /**
     * Creates the starter ladder from Startup to Conglomerate.
     *
     * @return a new, modifiable list of four levels
     */
    public static List<CompanyLevel> create() {
        var levels = new ArrayList<CompanyLevel>();
        levels.add(level("Startup", 0, 0, 0, 1, 5, 2));
        levels.add(level("Business", 10, 20_000, 1_000, 2, 10, 4));
        levels.add(level("Corporation", 50, 100_000, 10_000, 4, 20, 7));
        levels.add(level("Conglomerate", 200, 500_000, 50_000, 8, 40, 12));
        return levels;
    }

    private static CompanyLevel level(
            String name,
            int orderCount,
            double earnedMoney,
            int deliveredItems,
            int memberCount,
            int maxMembers,
            int maxOrders) {
        var level = new CompanyLevel();
        level.levelName(name);
        level.requirement().orderCount(orderCount);
        level.requirement().earnedMoney(earnedMoney);
        level.requirement().deliveredItems(deliveredItems);
        level.requirement().memberCount(memberCount);
        level.settings().maxMembers(maxMembers);
        level.settings().maxOrders(maxOrders);
        return level;
    }
}
