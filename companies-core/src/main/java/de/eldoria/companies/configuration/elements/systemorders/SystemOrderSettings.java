/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.systemorders;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The orders the server places itself, the file {@code system-orders.yml}. Off by default; while off, no system order
 * is placed, and the ones still open run out as usual.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class SystemOrderSettings {
    private boolean enabled = false;
    private int checkMinutes = 5;
    private double mintedDailyCap = 10000.0;
    private List<SystemOrderRule> rules = new ArrayList<>();

    public SystemOrderSettings() {}

    /**
     * Whether the server places system orders.
     *
     * @return true when system orders are placed
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * How often the primary places due system orders and removes the ones past their lifetime.
     *
     * @return minutes between checks, at least 1
     */
    public int checkMinutes() {
        return Math.max(1, checkMinutes);
    }

    /**
     * How much minted money all rules together may put into orders per day of the server's calendar.
     *
     * @return the cap, 0 when no minted money is spent
     */
    public double mintedDailyCap() {
        return Math.max(0, mintedDailyCap);
    }

    public void mintedDailyCap(double mintedDailyCap) {
        this.mintedDailyCap = mintedDailyCap;
    }

    /**
     * The rules an admin defined, in the order they are placed.
     *
     * @return the rules
     */
    public List<SystemOrderRule> rules() {
        return rules == null
                ? List.of()
                : rules.stream().filter(Objects::nonNull).toList();
    }

    public void rules(List<SystemOrderRule> rules) {
        this.rules = new ArrayList<>(rules);
    }
}
