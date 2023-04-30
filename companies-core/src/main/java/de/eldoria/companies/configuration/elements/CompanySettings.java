/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.wrapper.company.CompanyStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class CompanySettings {
    private List<CompanyLevel> level = new ArrayList<>();
    private int deliveryHours = 48;
    private double foundingPrice = 20000.0;
    private double renamePrice = 10000.0;
    private int expiredOrderPenalty = 3;
    private int abortedOrderPenalty = 1;

    public CompanySettings() {
        updateLevel();
    }

    private void updateLevel() {
        for (var i = 0; i < level.size(); i++) {
            level.get(i)
                 .level(i + 1);
        }
    }

    public int deliveryHours() {
        return deliveryHours;
    }

    public double foundingPrice() {
        return foundingPrice;
    }

    public void deliveryHours(int deliveryHours) {
        this.deliveryHours = deliveryHours;
    }

    public void foundingPrice(float foundingPrice) {
        this.foundingPrice = foundingPrice;
    }

    public void foundingPrice(double foundingPrice) {
        this.foundingPrice = foundingPrice;
    }

    public int expiredOrderPenalty() {
        return expiredOrderPenalty;
    }

    public void expiredOrderPenalty(int expiredOrderPenalty) {
        this.expiredOrderPenalty = expiredOrderPenalty;
    }

    public int abortedOrderPenalty() {
        return abortedOrderPenalty;
    }

    public void abortedOrderPenalty(int abortedOrderPenalty) {
        this.abortedOrderPenalty = abortedOrderPenalty;
    }

    public double renamePrice() {
        return renamePrice;
    }

    public void renamePrice(double renamePrice) {
        this.renamePrice = renamePrice;
    }

    public CompanyLevel createLevel(int level) {
        var newLevel = new CompanyLevel();
        var clampedLevel = Math.max(0, Math.min(level - 1, this.level.size()));
        this.level.add(clampedLevel, newLevel);
        updateLevel();
        return newLevel;
    }

    public Optional<CompanyLevel> level(int level) {
        if (level > this.level.size()) return Optional.empty();
        return Optional.ofNullable(this.level.get(level - 1));
    }

    public void moveLevel(int source, int target) {
        if (source == target) return;
        var clampedTarget = Math.max(1, Math.min(target, level.size())) - 1;
        level.add(clampedTarget, level.remove(source - 1));
        updateLevel();
    }

    public List<CompanyLevel> level() {
        return level;
    }

    public CompanyLevel calcCompanyLevel(CompanyStats stats) {
        var finalLevel = level.get(0);
        for (var level : level) {
            if (!level.requirement()
                      .checkRequirements(stats)) break;
            finalLevel = level;
        }
        return finalLevel;
    }

    public boolean deleteLevel(int level) {
        if (level < 1 || level > this.level.size()) return false;
        this.level.remove(level - 1);
        return true;
    }
}
