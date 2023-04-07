/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.companies.components.level.ILevelRequirement;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class LevelRequirement implements ILevelRequirement {
    private int orderCount = 0;
    private double earnedMoney = 0.0;
    private int deliveredItems = 0;
    private int memberCount = 0;

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

    public void orderCount(int orderCount) {
        this.orderCount = orderCount;
    }

    @Override
    public double earnedMoney() {
        return earnedMoney;
    }

    public void earnedMoney(double earnedMoney) {
        this.earnedMoney = earnedMoney;
    }

    @Override
    public int deliveredItems() {
        return deliveredItems;
    }

    public void deliveredItems(int deliveredItems) {
        this.deliveredItems = deliveredItems;
    }

    @Override
    public int memberCount() {
        return memberCount;
    }

    public void memberCount(int memberCount) {
        this.memberCount = memberCount;
    }
}
