/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import de.eldoria.companies.configuration.elements.GeneralSettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.eldoutilities.config.ConfigKey;
import de.eldoria.eldoutilities.config.JacksonConfig;
import org.bukkit.plugin.Plugin;

public class Configuration extends JacksonConfig<ConfigFile> {

    public Configuration(Plugin plugin) {
        super(plugin, ConfigKey.defaultConfig(ConfigFile.class, ConfigFile::new));
    }

    public CompanySettings companySettings() {
        return main().companySettings();
    }

    public UserSettings userSettings() {
        return main().userSettings();
    }

    public OrderSettings orderSetting() {
        return main().orderSetting();
    }

    public GeneralSettings generalSettings() {
        return main().generalSettings();
    }

    public DatabaseSettings databaseSettings() {
        return main().databaseSettings();
    }
}
