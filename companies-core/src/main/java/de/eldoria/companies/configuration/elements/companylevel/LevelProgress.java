/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

/**
 * How far a company has come towards a requirement of a level.
 */
public final class LevelProgress {
    private LevelProgress() {}

    /**
     * The share of a requirement a company has reached.
     *
     * @param value    the company's value
     * @param required the value the level requires
     * @return a value from 0 to 1, which is 1 when nothing is required
     */
    public static double of(double value, double required) {
        if (!(required > 0)) return 1;
        if (!(value > 0)) return 0;
        return Math.min(1, value / required);
    }
}
