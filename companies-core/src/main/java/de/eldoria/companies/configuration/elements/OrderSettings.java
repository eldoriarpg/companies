/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.ordersettings.MarketSettings;
import de.eldoria.companies.configuration.elements.ordersettings.OrderFee;
import de.eldoria.companies.configuration.elements.ordersettings.RepeatingSettings;
import de.eldoria.companies.configuration.elements.ordersettings.SoloSettings;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class OrderSettings {
    private int maxItems = 64 * 8;
    private int maxMaterials = 5;
    private int maxUnclaimedHours = 24 * 7;
    private OrderFee fee = new OrderFee();
    private int maxExtensions = 2;
    private int purgeWarningHours = 24;
    private ItemMatchSettings matching = new ItemMatchSettings();
    private RepeatingSettings repeating = new RepeatingSettings();
    private SoloSettings solo = new SoloSettings();
    private MarketSettings market = new MarketSettings();

    public OrderSettings() {}

    public int maxItems() {
        return maxItems;
    }

    public void maxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    /**
     * How many lines an order may have.
     *
     * @return maximum number of lines
     */
    public int maxMaterials() {
        return maxMaterials;
    }

    public void maxMaterials(int maxMaterials) {
        this.maxMaterials = maxMaterials;
    }

    public int maxUnclaimedHours() {
        return maxUnclaimedHours;
    }

    public void maxUnclaimedHours(int maxUnclaimedHours) {
        this.maxUnclaimedHours = maxUnclaimedHours;
    }

    /**
     * The fee charged for placing an order.
     *
     * @return order fee
     */
    public OrderFee fee() {
        return fee;
    }

    /**
     * How often the owner may reset the purge timer of an order nobody accepted.
     *
     * @return extensions per order, 0 when extending is disabled
     */
    public int maxExtensions() {
        return maxExtensions;
    }

    public void maxExtensions(int maxExtensions) {
        this.maxExtensions = maxExtensions;
    }

    /**
     * How long before an unaccepted order is purged its owner is warned.
     *
     * @return warning window in hours
     */
    public int purgeWarningHours() {
        return purgeWarningHours;
    }

    public void purgeWarningHours(int purgeWarningHours) {
        this.purgeWarningHours = purgeWarningHours;
    }

    /**
     * How delivered items are matched against the requested items.
     *
     * @return matching settings
     */
    public ItemMatchSettings matching() {
        return matching;
    }

    /**
     * Repeating orders.
     *
     * @return repeating order settings
     */
    public RepeatingSettings repeating() {
        return repeating == null ? new RepeatingSettings() : repeating;
    }

    /**
     * Orders players work on alone, without a company.
     *
     * @return solo order settings
     */
    public SoloSettings solo() {
        return solo == null ? new SoloSettings() : solo;
    }

    /**
     * The market statistics of the dashboard.
     *
     * @return market settings
     */
    public MarketSettings market() {
        return market == null ? new MarketSettings() : market;
    }
}
