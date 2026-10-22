/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.configuration.elements.companylevel.LevelSettings;
import de.eldoria.companies.configuration.elements.companylevel.StarterLevels;
import de.eldoria.companies.configuration.elements.companysettings.JoiningSettings;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.configuration.elements.companysettings.TaxSettings;
import de.eldoria.companies.data.wrapper.company.CompanyStats;

import java.util.List;
import java.util.Optional;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class CompanySettings {
    private List<CompanyLevel> level = StarterLevels.create();
    private int deliveryHours = 48;
    private double foundingPrice = 20000.0;
    private double renamePrice = 10000.0;
    private int expiredOrderPenalty = 3;
    private int abortedOrderPenalty = 1;
    private boolean allowMemberOrders = false;
    private int inviteHours = 24;
    private int expiryWarningHours = 12;
    private TaxSettings tax = new TaxSettings();
    private JoiningSettings joining = new JoiningSettings();
    private ProfileSettings profile = new ProfileSettings();

    public CompanySettings() {
        updateLevel();
    }

    private void updateLevel() {
        for (var i = 0; i < level.size(); i++) {
            level.get(i).level(i + 1);
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

    /**
     * Whether a company may accept orders placed by its own members.
     *
     * @return true when companies may work on their members' orders
     */
    public boolean allowMemberOrders() {
        return allowMemberOrders;
    }

    public void allowMemberOrders(boolean allowMemberOrders) {
        this.allowMemberOrders = allowMemberOrders;
    }

    public CompanyLevel createLevel(int level) {
        var newLevel = new CompanyLevel();
        var clampedLevel = Math.max(0, Math.min(level - 1, this.level.size()));
        this.level.add(clampedLevel, newLevel);
        updateLevel();
        return newLevel;
    }

    public Optional<CompanyLevel> level(int level) {
        if (level < 1 || level > this.level.size()) return Optional.empty();
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
        if (level.isEmpty()) return null;
        var finalLevel = level.get(0);
        for (var level : level) {
            if (!level.requirement().checkRequirements(stats)) break;
            finalLevel = level;
        }
        return finalLevel;
    }

    public boolean deleteLevel(int level) {
        if (level < 1 || level > this.level.size()) return false;
        this.level.remove(level - 1);
        updateLevel();
        return true;
    }

    /**
     * How long an invite or an application stays open before it expires.
     *
     * @return lifetime of an invite or application in hours
     */
    public int inviteHours() {
        return inviteHours;
    }

    /**
     * How long before its deadline a claimed order is shown to the company's members when they join.
     *
     * @return warning window in hours
     */
    public int expiryWarningHours() {
        return expiryWarningHours;
    }

    /**
     * The global tax on payouts.
     *
     * @return tax settings
     */
    public TaxSettings tax() {
        return tax;
    }

    /**
     * The join mode of new companies and the limits of applications.
     *
     * @return joining settings
     */
    public JoiningSettings joining() {
        return joining == null ? new JoiningSettings() : joining;
    }

    /**
     * The limits of the editable profile fields.
     *
     * @return profile settings
     */
    public ProfileSettings profile() {
        return profile == null ? new ProfileSettings() : profile;
    }

    /**
     * The tax on payouts for a company of the given level: the level's override when it has one, otherwise the global
     * tax.
     *
     * @param level level of the company
     * @return percentage, where 5 means 5 %
     */
    public double taxPercent(int level) {
        return level(level).flatMap(l -> l.settings().taxPercent()).orElse(tax.percent());
    }

    /**
     * The settings of a level, or the defaults when the level does not exist.
     *
     * @param level level of the company
     * @return settings of the level
     */
    public LevelSettings levelSettings(int level) {
        return level(level).orElse(CompanyLevel.DEFAULT).settings();
    }
}
