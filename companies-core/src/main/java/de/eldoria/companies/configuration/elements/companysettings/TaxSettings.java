/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companysettings;

/**
 * The tax taken from payouts to workers, unless the company level overrides it.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class TaxSettings {
    private double percent = 0.0;

    public TaxSettings() {}

    /**
     * The tax on payouts.
     *
     * @return percentage, where 5 means 5 %
     */
    public double percent() {
        return percent;
    }

    public void percent(double percent) {
        this.percent = percent;
    }
}
