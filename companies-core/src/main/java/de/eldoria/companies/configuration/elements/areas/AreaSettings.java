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
 * order they are asked. The areas themselves are stored in the database.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class AreaSettings {
    private boolean enabled = true;
    private List<String> providerOrder = new ArrayList<>(List.of("plotsquared", "worldguard"));
    private Map<String, Boolean> providers = defaultProviders();

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
}
