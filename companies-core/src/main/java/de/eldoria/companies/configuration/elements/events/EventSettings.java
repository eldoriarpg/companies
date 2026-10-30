/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.events;

/**
 * The server events, the file {@code events.yml}. The events themselves are stored in the database; this file holds
 * how their effects combine.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class EventSettings {
    private double maxMultiplier = 3.0;

    public EventSettings() {}

    /**
     * The highest factor the reward multipliers of all active events together put on a payout. Multipliers multiply,
     * so two events of 2 would pay four times the payout without the cap.
     *
     * @return the cap, at least 1
     */
    public double maxMultiplier() {
        return Double.isFinite(maxMultiplier) ? Math.max(1, maxMultiplier) : 1;
    }

    public void maxMultiplier(double maxMultiplier) {
        this.maxMultiplier = maxMultiplier;
    }
}
