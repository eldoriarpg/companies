/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.eldoria.companies.components.level.ILevelSettings;

import java.util.Optional;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class LevelSettings implements ILevelSettings {
    private int maxMembers = 20;
    private int maxOrders = 5;
    private int maxOrderSize = 0;
    private int deliveryHoursBonus = 0;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double taxPercent = null;

    public LevelSettings() {}

    @Override
    public int maxMembers() {
        return maxMembers;
    }

    @Override
    public int maxOrders() {
        return maxOrders;
    }

    public void maxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public void maxOrders(int maxOrders) {
        this.maxOrders = maxOrders;
    }

    /**
     * The tax on payouts for companies of this level, replacing the global tax.
     *
     * @return percentage, where 5 means 5 %, or empty when the global tax applies
     */
    public Optional<Double> taxPercent() {
        return Optional.ofNullable(taxPercent);
    }

    /**
     * Sets or clears the tax override of this level.
     *
     * @param taxPercent percentage, or null to use the global tax
     */
    public void taxPercent(Double taxPercent) {
        this.taxPercent = taxPercent;
    }

    /**
     * The most items an order may have for companies of this level to accept it.
     *
     * @return item limit, 0 for no limit
     */
    public int maxOrderSize() {
        return maxOrderSize;
    }

    public void maxOrderSize(int maxOrderSize) {
        this.maxOrderSize = maxOrderSize;
    }

    /**
     * Whether an order of the given size is too large for companies of this level.
     *
     * @param amount items of the order
     * @return true when a limit is set and the order exceeds it
     */
    public boolean exceedsOrderSize(int amount) {
        return maxOrderSize > 0 && amount > maxOrderSize;
    }

    /**
     * Extra hours companies of this level get to deliver an order they accept.
     *
     * @return bonus hours, never negative
     */
    public int deliveryHoursBonus() {
        return Math.max(0, deliveryHoursBonus);
    }

    public void deliveryHoursBonus(int deliveryHoursBonus) {
        this.deliveryHoursBonus = deliveryHoursBonus;
    }
}
