package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.eldoutilities.configuration.EldoConfig;
import org.bukkit.plugin.Plugin;

public class Configuration extends EldoConfig {
    private CompanySettings companySettings = new CompanySettings();
    private UserSettings userSettings = new UserSettings();

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
    }

    @Override
    protected void saveConfigs() {
        getConfig().set("companySettings",companySettings);
        getConfig().set("userSettings",userSettings);
    }

    public CompanySettings companySettings() {
        return companySettings;
    }

    public UserSettings userSettings() {
        return userSettings;
    }
}
