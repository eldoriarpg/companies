/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.prices;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The prices read from shop plugins, the file {@code prices.yml}: which sources may be used, which of them count only
 * the prices of admins and the server, how often the shops are sampled and how much data each kind of price needs
 * before the baseline goes by it. Whether Companies' own orders are enough to go by is {@code market.minOrders} in
 * {@code order.yml}.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class PriceSettings {
    private Map<String, Boolean> sources = new LinkedHashMap<>();
    private List<String> adminOnly = new ArrayList<>();
    private List<String> zshopEconomies = new ArrayList<>();
    private double shopkeepersCurrencyValue = 0;
    private int sampleMinutes = 30;
    private int days = 14;
    private int minTrades = 5;
    private int minListings = 3;
    private double sellFactor = 1.0;
    private double buyFactor = 1.0;

    public PriceSettings() {}

    /**
     * @param id id of a source
     * @return whether the source may be used, its id matched ignoring case; a source not listed may
     */
    public boolean sourceEnabled(String id) {
        if (sources == null) return true;
        var key = id.toLowerCase(Locale.ROOT);
        for (var entry : sources.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(key)) return !Boolean.FALSE.equals(entry.getValue());
        }
        return true;
    }

    /**
     * @return the switches of the sources, by id
     */
    public Map<String, Boolean> sources() {
        return sources == null ? Map.of() : Map.copyOf(sources);
    }

    public void sources(Map<String, Boolean> sources) {
        this.sources = new LinkedHashMap<>(sources);
    }

    /**
     * @param id id of a source
     * @return whether only the prices of admins and the server count from the source, its id matched ignoring case
     */
    public boolean adminOnly(String id) {
        return adminOnly().contains(id.toLowerCase(Locale.ROOT));
    }

    /**
     * @return the ids of the sources whose player shops do not count, lower case
     */
    public List<String> adminOnly() {
        if (adminOnly == null) return List.of();
        return adminOnly.stream().map(id -> id.toLowerCase(Locale.ROOT)).toList();
    }

    public void adminOnly(List<String> adminOnly) {
        this.adminOnly = new ArrayList<>(adminOnly);
    }

    /**
     * zShop does not tell which currency an economy pays in, so this names the zShop economies that pay in the server's
     * main currency.
     *
     * @param name the name of a zShop economy
     * @return whether its prices count, matched ignoring case; every economy counts while none is listed
     */
    public boolean zshopEconomy(String name) {
        var listed = zshopEconomies();
        return listed.isEmpty() || listed.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * @return the names of the zShop economies that pay in the server's main currency, lower case; empty for all
     */
    public List<String> zshopEconomies() {
        if (zshopEconomies == null) return List.of();
        return zshopEconomies.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }

    public void zshopEconomies(List<String> zshopEconomies) {
        this.zshopEconomies = new ArrayList<>(zshopEconomies);
    }

    /**
     * Shopkeepers prices are counts of currency items, emeralds by default, so this says what one is worth.
     *
     * @return the money one currency item of Shopkeepers is worth, 0 while Shopkeepers is not read
     */
    public double shopkeepersCurrencyValue() {
        return Double.isFinite(shopkeepersCurrencyValue) ? Math.max(0, shopkeepersCurrencyValue) : 0;
    }

    public void shopkeepersCurrencyValue(double shopkeepersCurrencyValue) {
        this.shopkeepersCurrencyValue = shopkeepersCurrencyValue;
    }

    /**
     * @return how many minutes lie between two samples of the prices the shops offer, at least 1
     */
    public int sampleMinutes() {
        return Math.max(1, sampleMinutes);
    }

    public void sampleMinutes(int sampleMinutes) {
        this.sampleMinutes = sampleMinutes;
    }

    /**
     * @return how many days back trades count, and how old a sample may be, at least 1
     */
    public int days() {
        return Math.max(1, days);
    }

    public void days(int days) {
        this.days = days;
    }

    /**
     * @return the fewest trades of a material and side the baseline goes by, at least 1
     */
    public int minTrades() {
        return Math.max(1, minTrades);
    }

    public void minTrades(int minTrades) {
        this.minTrades = minTrades;
    }

    /**
     * @return the fewest player listings of a material and side the baseline goes by, when there are too few trades,
     *     at least 1
     */
    public int minListings() {
        return Math.max(1, minListings);
    }

    public void minListings(int minListings) {
        this.minListings = minListings;
    }

    /**
     * @return what a sell price is multiplied by when it is the only side known, above 0
     */
    public double sellFactor() {
        return sellFactor > 0 ? sellFactor : 1.0;
    }

    public void sellFactor(double sellFactor) {
        this.sellFactor = sellFactor;
    }

    /**
     * @return what a buy price is multiplied by when it is the only side known, above 0
     */
    public double buyFactor() {
        return buyFactor > 0 ? buyFactor : 1.0;
    }

    public void buyFactor(double buyFactor) {
        this.buyFactor = buyFactor;
    }
}
