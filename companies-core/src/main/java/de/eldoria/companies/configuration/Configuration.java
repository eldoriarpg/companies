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
import de.eldoria.eldoutilities.configuration.EldoConfig;
import org.bukkit.plugin.Plugin;

public class Configuration extends EldoConfig {
    private CompanySettings companySettings;
    private UserSettings userSettings;
    private OrderSettings orderSetting;
    private GeneralSettings generalSettings;
    private DatabaseSettings databaseSettings;

    public Configuration(Plugin plugin) {
        super(plugin);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void reloadConfigs() {
        companySettings = getConfig().getObject("companySettings", CompanySettings.class, new CompanySettings());
        userSettings = getConfig().getObject("userSettings", UserSettings.class, new UserSettings());
        orderSetting = getConfig().getObject("orderSetting", OrderSettings.class, new OrderSettings());
        generalSettings = getConfig().getObject("generalSettings", GeneralSettings.class, new GeneralSettings());
        databaseSettings = getConfig().getObject("databaseSettings", DatabaseSettings.class, new DatabaseSettings());
    }

    @Override
    protected void saveConfigs() {
        getConfig().set("companySettings", companySettings);
        getConfig().set("userSettings", userSettings);
        getConfig().set("orderSetting", orderSetting);
        getConfig().set("generalSettings", generalSettings);
        getConfig().set("databaseSettings", databaseSettings);
    }

    public CompanySettings companySettings() {
        return companySettings;
    }

    public UserSettings userSettings() {
        return userSettings;
    }

    public OrderSettings orderSetting() {
        return orderSetting;
    }

    public GeneralSettings generalSettings() {
        return generalSettings;
    }

    public DatabaseSettings databaseSettings() {
        return databaseSettings;
    }
}
