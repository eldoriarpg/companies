/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.companies.components.level.ILevelRequirement;
import de.eldoria.companies.data.wrapper.company.CompanyStats;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class LevelRequirement implements ILevelRequirement {
    private int orderCount = 1;
    private double earnedMoney = 1000.0;
    private int deliveredItems = 1000;
    private int memberCount = 1;

    public LevelRequirement() {
    }

    public boolean checkRequirements(CompanyStats stats) {
        return stats.orderCount() >= orderCount
               && stats.price() >= earnedMoney
               && stats.deliveredItems() >= deliveredItems
               && stats.memberCount() >= memberCount;
    }

    @Override
    public int orderCount() {
        return orderCount;
    }

    @Override
    public double earnedMoney() {
        return earnedMoney;
    }

    @Override
    public int deliveredItems() {
        return deliveredItems;
    }

    @Override
    public int memberCount() {
        return memberCount;
    }

    public void orderCount(int orderCount) {
        this.orderCount = orderCount;
    }

    public void earnedMoney(double earnedMoney) {
        this.earnedMoney = earnedMoney;
    }

    public void deliveredItems(int deliveredItems) {
        this.deliveredItems = deliveredItems;
    }

    public void memberCount(int memberCount) {
        this.memberCount = memberCount;
    }
}
