/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companysettings;

/**
 * Saved order searches that notify a company about new matching orders.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class AlertSettings {
    private boolean enabled = true;
    private int maxPerCompany = 3;
    private int minMinutesBetween = 15;

    public AlertSettings() {}

    /**
     * Whether companies can save alerts and are notified by them.
     *
     * @return true when alerts are enabled
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * How many alerts a company may save when its level sets no own limit.
     *
     * @return alerts per company, 0 to disable them
     */
    public int maxPerCompany() {
        return Math.max(0, maxPerCompany);
    }

    public void maxPerCompany(int maxPerCompany) {
        this.maxPerCompany = maxPerCompany;
    }

    /**
     * The shortest time between two notifications of one alert. Matches in between are collected into a digest.
     *
     * @return minutes, at least 0
     */
    public int minMinutesBetween() {
        return Math.max(0, minMinutesBetween);
    }

    public void minMinutesBetween(int minMinutesBetween) {
        this.minMinutesBetween = minMinutesBetween;
    }
}
