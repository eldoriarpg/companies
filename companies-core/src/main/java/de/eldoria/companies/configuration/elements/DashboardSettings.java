/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.dashboard.BrandingSettings;
import de.eldoria.companies.web.api.Features;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The web dashboard, the {@code dashboard} section of the config. It is synced to every node, so it holds nothing
 * secret: the dashboard key is entered with {@code /companyadmin dashboard key} or in {@code node.yml}, see
 * {@link NodeSettings#dashboardKey()}.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class DashboardSettings {
    /**
     * The gateway hosted by Eldoria, used unless the config names another one.
     */
    public static final String DEFAULT_GATEWAY = "wss://companies.eldoria.de/api/v1/servers/connect";

    private static final int DEFAULT_IDLE_MINUTES = 60;
    private static final int DEFAULT_MAX_HOURS = 24;
    private static final int MAX_IDLE_MINUTES = 7 * 24 * 60;
    private static final int MAX_HOURS = 30 * 24;

    private boolean enabled = true;
    private String gateway = DEFAULT_GATEWAY;
    private int sessionIdleMinutes = DEFAULT_IDLE_MINUTES;
    private int sessionMaxHours = DEFAULT_MAX_HOURS;
    private Map<String, Boolean> features = defaultFeatures();
    private BrandingSettings branding = new BrandingSettings();

    public DashboardSettings() {}

    /**
     * @param enabled whether the dashboard is enabled
     * @param gateway the gateway address as written
     */
    public DashboardSettings(boolean enabled, String gateway) {
        this.enabled = enabled;
        this.gateway = gateway;
    }

    private static Map<String, Boolean> defaultFeatures() {
        var defaults = new LinkedHashMap<String, Boolean>();
        for (var feature : Features.ALL) {
            if (!feature.startsWith(Features.ADMIN_PREFIX)) defaults.put(feature, true);
        }
        return defaults;
    }

    /**
     * Whether the nodes connect to the gateway and players can open the dashboard. On unless the config switches it
     * off.
     *
     * @return true when enabled
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * The websocket address of the gateway.
     *
     * @return the address, empty when it is no {@code ws://} or {@code wss://} address
     */
    public Optional<URI> gateway() {
        var value = gateway == null || gateway.isBlank() ? DEFAULT_GATEWAY : gateway.trim();
        try {
            var uri = new URI(value);
            var scheme = uri.getScheme();
            if (uri.getHost() == null || !("wss".equalsIgnoreCase(scheme) || "ws".equalsIgnoreCase(scheme))) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * Minutes a session may stay unused before it ends, between 5 minutes and a week.
     *
     * @return idle minutes
     */
    public int sessionIdleMinutes() {
        return Math.clamp(sessionIdleMinutes, 5, MAX_IDLE_MINUTES);
    }

    /**
     * Hours a session lasts at most, between one hour and 30 days.
     *
     * @return maximum age in hours
     */
    public int sessionMaxHours() {
        return Math.clamp(sessionMaxHours, 1, MAX_HOURS);
    }

    /**
     * Whether a feature is switched on. A feature missing from the config is on.
     *
     * @param feature the feature name, see {@link Features}
     * @return false when the config switches it off
     */
    public boolean feature(String feature) {
        if (features == null) return true;
        var value = features.get(feature);
        return value == null || value;
    }

    /**
     * How the server shows itself on the dashboard.
     *
     * @return the branding
     */
    public BrandingSettings branding() {
        return branding == null ? new BrandingSettings() : branding;
    }
}
