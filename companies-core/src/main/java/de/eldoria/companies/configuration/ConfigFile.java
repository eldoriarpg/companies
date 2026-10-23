/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import de.eldoria.companies.configuration.elements.BoardSettings;
import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.companies.configuration.elements.EconomySettings;
import de.eldoria.companies.configuration.elements.GeneralSettings;
import de.eldoria.companies.configuration.elements.MenuSettings;
import de.eldoria.companies.configuration.elements.NotificationSettings;
import de.eldoria.companies.configuration.elements.PlaceholderSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.configuration.elements.TopSettings;
import de.eldoria.companies.menus.MenuMode;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class ConfigFile {
    private GeneralSettings generalSettings = new GeneralSettings();
    private StyleSettings style = new StyleSettings();

    @JsonSetter(nulls = Nulls.SKIP)
    private MenuSettings menus = new MenuSettings();

    private EconomySettings economy = new EconomySettings();
    private NotificationSettings notifications = new NotificationSettings();
    private PlaceholderSettings placeholders = new PlaceholderSettings();
    private TopSettings top = new TopSettings();
    private BoardSettings boards = new BoardSettings();
    private DiscordSettings discord = new DiscordSettings();

    /**
     * The configuration written for a new installation, which shows menus as dialogs.
     * <p>
     * A file without the {@code menus} setting was written before menus had dialogs. It is read with chat menus, so
     * an existing installation keeps showing menus the way it did until the setting is changed. The setting without
     * a value ({@code menus:}) is read the same way.
     *
     * @return the configuration of a new installation
     */
    public static ConfigFile fresh() {
        var config = new ConfigFile();
        config.menus = new MenuSettings(MenuMode.DIALOG);
        return config;
    }

    public GeneralSettings generalSettings() {
        return generalSettings;
    }

    public StyleSettings style() {
        return style;
    }

    /**
     * @return how menus are shown
     */
    public MenuSettings menus() {
        return menus;
    }

    public EconomySettings economy() {
        return economy;
    }

    /**
     * @return how notifications are kept
     */
    public NotificationSettings notifications() {
        return notifications;
    }

    /**
     * @return how the PlaceholderAPI values are cached
     */
    public PlaceholderSettings placeholders() {
        return placeholders;
    }

    /**
     * @return how the company ranking is cached
     */
    public TopSettings top() {
        return top;
    }

    /**
     * @return the order boards on signs and NPCs
     */
    public BoardSettings boards() {
        return boards;
    }

    /**
     * @return the Discord webhook
     */
    public DiscordSettings discord() {
        return discord;
    }
}
