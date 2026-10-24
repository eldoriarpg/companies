/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.session;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Describes a browser from its {@code User-Agent} in a few words, such as {@code Firefox on Android}, so a player
 * recognises a login they did not do.
 */
public final class Devices {
    private static final List<Map.Entry<String, String>> BROWSERS = List.of(
            Map.entry("Edg/", "Edge"),
            Map.entry("OPR/", "Opera"),
            Map.entry("SamsungBrowser/", "Samsung Internet"),
            Map.entry("Firefox/", "Firefox"),
            Map.entry("FxiOS/", "Firefox"),
            Map.entry("CriOS/", "Chrome"),
            Map.entry("Chrome/", "Chrome"),
            Map.entry("Safari/", "Safari"));
    private static final List<Map.Entry<String, String>> SYSTEMS = List.of(
            Map.entry("Android", "Android"),
            Map.entry("iPhone", "iPhone"),
            Map.entry("iPad", "iPad"),
            Map.entry("Windows", "Windows"),
            Map.entry("Mac OS X", "macOS"),
            Map.entry("CrOS", "ChromeOS"),
            Map.entry("Linux", "Linux"));

    private Devices() {}

    /**
     * @param userAgent the browser's {@code User-Agent}
     * @return a short description, none when nothing is recognised
     */
    public static @Nullable String describe(@Nullable String userAgent) {
        if (userAgent == null) return null;
        String browser = first(BROWSERS, userAgent);
        String system = first(SYSTEMS, userAgent);
        if (browser == null && system == null) return null;
        if (browser == null) return "A browser on " + system;
        return system == null ? browser : browser + " on " + system;
    }

    private static @Nullable String first(List<Map.Entry<String, String>> names, String userAgent) {
        return names.stream()
                .filter(entry -> userAgent.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
