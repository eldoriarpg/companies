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
}
