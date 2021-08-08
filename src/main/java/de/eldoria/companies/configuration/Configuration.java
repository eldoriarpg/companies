package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.GeneralSettings;
import de.eldoria.companies.configuration.elements.OrderSetting;
import de.eldoria.companies.configuration.elements.UserSettings;
import de.eldoria.eldoutilities.configuration.EldoConfig;
import org.bukkit.plugin.Plugin;

public class Configuration extends EldoConfig {
    private CompanySettings companySettings;
    private UserSettings userSettings;
    private OrderSetting orderSetting;
    private GeneralSettings generalSettings;

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
        orderSetting = getConfig().getObject("orderSetting", OrderSetting.class, new OrderSetting());
    }

    @Override
    protected void saveConfigs() {
        getConfig().set("companySettings", companySettings);
        getConfig().set("userSettings", userSettings);
        getConfig().set("orderSetting", orderSetting);
    }

    public CompanySettings companySettings() {
        return companySettings;
    }

    public UserSettings userSettings() {
        return userSettings;
    }

    public OrderSetting orderSetting() {
        return orderSetting;
    }

    public GeneralSettings generalSettings() {
        return generalSettings;
    }
}
