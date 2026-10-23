/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.ordersettings;

/**
 * Repeating orders: an order posted again every day or week until a count or a date is reached.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class RepeatingSettings {
    private boolean enabled = true;
    private int maxSeriesPerPlayer = 3;
    private int maxCopies = 52;
    private int maxDays = 365;
    private int checkMinutes = 5;

    public RepeatingSettings() {}

    /**
     * Whether players can create repeating orders and existing ones keep posting.
     *
     * @return true when repeating orders are enabled
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * How many active repeating orders one player may have.
     *
     * @return active series per player
     */
    public int maxSeriesPerPlayer() {
        return Math.max(0, maxSeriesPerPlayer);
    }

    public void maxSeriesPerPlayer(int maxSeriesPerPlayer) {
        this.maxSeriesPerPlayer = maxSeriesPerPlayer;
    }

    /**
     * The most copies a repeating order may post.
     *
     * @return copies, at least 1
     */
    public int maxCopies() {
        return Math.max(1, maxCopies);
    }

    public void maxCopies(int maxCopies) {
        this.maxCopies = maxCopies;
    }

    /**
     * How far in the future the end date of a repeating order may lie.
     *
     * @return days, at least 1
     */
    public int maxDays() {
        return Math.max(1, maxDays);
    }

    public void maxDays(int maxDays) {
        this.maxDays = maxDays;
    }

    /**
     * How often due copies are posted.
     *
     * @return minutes between checks, at least 1
     */
    public int checkMinutes() {
        return Math.max(1, checkMinutes);
    }

    public void checkMinutes(int checkMinutes) {
        this.checkMinutes = checkMinutes;
    }
}
