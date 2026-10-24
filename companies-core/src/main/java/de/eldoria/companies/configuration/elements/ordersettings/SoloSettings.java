/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.ordersettings;

/**
 * Solo orders: a player claims an order without a company, delivers it alone and is paid the whole price.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class SoloSettings {
    private boolean enabled = false;
    private int maxActive = 1;
    private int maxOrderSize = 64;
    private int deliveryHours = 24;
    private double taxPercent = 0.0;
    private boolean forCompanyMembers = false;
    private int abortCooldownMinutes = 60;

    public SoloSettings() {}

    /**
     * Whether players can claim orders alone.
     *
     * @return true when solo orders are enabled
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * How many orders one player may work on alone at the same time.
     *
     * @return claimed solo orders per player, at least 1
     */
    public int maxActive() {
        return Math.max(1, maxActive);
    }

    public void maxActive(int maxActive) {
        this.maxActive = maxActive;
    }

    /**
     * The largest order a player may take alone.
     *
     * @return total items of the order, 0 for no limit
     */
    public int maxOrderSize() {
        return Math.max(0, maxOrderSize);
    }

    public void maxOrderSize(int maxOrderSize) {
        this.maxOrderSize = maxOrderSize;
    }

    /**
     * Whether an order is larger than a player may take alone.
     *
     * @param amount total items of the order
     * @return true when there is a limit and the order exceeds it
     */
    public boolean exceedsOrderSize(int amount) {
        return maxOrderSize() > 0 && amount > maxOrderSize();
    }

    /**
     * How long a player has to deliver an order they claimed alone.
     *
     * @return hours, at least 1
     */
    public int deliveryHours() {
        return Math.max(1, deliveryHours);
    }

    public void deliveryHours(int deliveryHours) {
        this.deliveryHours = deliveryHours;
    }

    /**
     * The tax taken from the payout of a solo order.
     *
     * @return percent, clamped to 0 to 100
     */
    public double taxPercent() {
        return Math.clamp(taxPercent, 0.0, 100.0);
    }

    public void taxPercent(double taxPercent) {
        this.taxPercent = taxPercent;
    }

    /**
     * Whether members of a company may claim orders alone as well.
     *
     * @return true when company members may take solo orders
     */
    public boolean forCompanyMembers() {
        return forCompanyMembers;
    }

    public void forCompanyMembers(boolean forCompanyMembers) {
        this.forCompanyMembers = forCompanyMembers;
    }

    /**
     * How long a player may not claim a solo order after they aborted one or let one expire.
     *
     * @return minutes, 0 for no cooldown
     */
    public int abortCooldownMinutes() {
        return Math.max(0, abortCooldownMinutes);
    }

    public void abortCooldownMinutes(int abortCooldownMinutes) {
        this.abortCooldownMinutes = abortCooldownMinutes;
    }
}
