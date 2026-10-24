/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.dashboard;

/**
 * The name and icon the dashboard shows for the server, the {@code dashboard.branding} section of the config.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class BrandingSettings {
    /**
     * The icon used unless the config names another: the server's own icon.
     */
    public static final String DEFAULT_ICON = "server-icon.png";

    private String name = "";
    private String icon = DEFAULT_ICON;

    public BrandingSettings() {}

    /**
     * @param name the name as written
     * @param icon the icon file as written
     */
    public BrandingSettings(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    /**
     * The name shown on the dashboard.
     *
     * @return the name, empty to use the server's MOTD
     */
    public String name() {
        return name == null ? "" : name.trim();
    }

    /**
     * The icon file: a path in the plugin folder, or in the server folder for the server's own icon.
     *
     * @return the file name, empty for no icon
     */
    public String icon() {
        return icon == null ? "" : icon.trim();
    }
}
