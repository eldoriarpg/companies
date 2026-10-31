/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.areas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The areas companies own, the file {@code areas.yml}: which plot and claim plugins they may come from and in which
 * order they are asked, the company home with its warm-up, cooldown and cost, and what selling back and renting
 * offered areas pay and warn. The areas and homes themselves are
 * stored in the database.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class AreaSettings {
    private boolean enabled = true;
    private List<String> providerOrder = new ArrayList<>(List.of("plotsquared", "worldguard"));
    private Map<String, Boolean> providers = defaultProviders();
    private int homeWarmupSeconds = 3;
    private int homeCooldownSeconds = 60;
    private double homeCost = 0;
    private boolean homePaidByTreasury = false;
    private boolean homeInsideArea = true;
    private int sellBackPercent = 50;
    private int rentWarnHours = 24;

    public AreaSettings() {}

    private static Map<String, Boolean> defaultProviders() {
        Map<String, Boolean> providers = new LinkedHashMap<>();
        providers.put("plotsquared", true);
        providers.put("worldguard", true);
        return providers;
    }

    /**
     * @return whether companies may get new areas; areas they have keep working either way
     */
    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The order the area providers are asked which area holds a location; the first that knows one wins. Providers not
     * listed come after the listed ones, in the order they registered.
     *
     * @return the provider ids, lower case
     */
    public List<String> providerOrder() {
        if (providerOrder == null) return List.of();
        return providerOrder.stream().map(id -> id.toLowerCase(Locale.ROOT)).toList();
    }

    public void providerOrder(List<String> providerOrder) {
        this.providerOrder = new ArrayList<>(providerOrder);
    }

    /**
     * @param id id of a provider
     * @return whether the provider may be used, its id matched ignoring case; a provider not listed may
     */
    public boolean providerEnabled(String id) {
        if (providers == null) return true;
        var key = id.toLowerCase(Locale.ROOT);
        for (var entry : providers.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(key)) return !Boolean.FALSE.equals(entry.getValue());
        }
        return true;
    }

    /**
     * @return the switches of the providers, by id
     */
    public Map<String, Boolean> providers() {
        return providers == null ? Map.of() : Map.copyOf(providers);
    }

    public void providers(Map<String, Boolean> providers) {
        this.providers = new LinkedHashMap<>(providers);
    }

    /**
     * @return how many seconds a player stands still before the teleport home, 0 for none
     */
    public int homeWarmupSeconds() {
        return Math.max(0, homeWarmupSeconds);
    }

    public void homeWarmupSeconds(int homeWarmupSeconds) {
        this.homeWarmupSeconds = homeWarmupSeconds;
    }

    /**
     * @return how many seconds a player waits after a teleport home before the next, 0 for none
     */
    public int homeCooldownSeconds() {
        return Math.max(0, homeCooldownSeconds);
    }

    public void homeCooldownSeconds(int homeCooldownSeconds) {
        this.homeCooldownSeconds = homeCooldownSeconds;
    }

    /**
     * @return what a teleport home costs, 0 for nothing
     */
    public double homeCost() {
        return Math.max(0, homeCost);
    }

    public void homeCost(double homeCost) {
        this.homeCost = homeCost;
    }

    /**
     * @return whether the company's treasury pays a teleport home instead of the player
     */
    public boolean homePaidByTreasury() {
        return homePaidByTreasury;
    }

    public void homePaidByTreasury(boolean homePaidByTreasury) {
        this.homePaidByTreasury = homePaidByTreasury;
    }

    /**
     * @return whether a company with areas must set its home inside one of them
     */
    public boolean homeInsideArea() {
        return homeInsideArea;
    }

    public void homeInsideArea(boolean homeInsideArea) {
        this.homeInsideArea = homeInsideArea;
    }

    /**
     * @return the part of its price, in percent, a company gets back for a bought area it sells back, and its owner
     *         when the company is disbanded
     */
    public int sellBackPercent() {
        return Math.clamp(sellBackPercent, 0, 100);
    }

    public void sellBackPercent(int sellBackPercent) {
        this.sellBackPercent = sellBackPercent;
    }

    /**
     * @return how many hours before a rent is due the company is told, 0 for never
     */
    public int rentWarnHours() {
        return Math.max(0, rentWarnHours);
    }

    public void rentWarnHours(int rentWarnHours) {
        this.rentWarnHours = rentWarnHours;
    }
}
