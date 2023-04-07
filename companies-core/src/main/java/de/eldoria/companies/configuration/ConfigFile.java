package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.DatabaseSettings;
import de.eldoria.companies.configuration.elements.GeneralSettings;
import de.eldoria.companies.configuration.elements.OrderSettings;
import de.eldoria.companies.configuration.elements.UserSettings;

@SuppressWarnings("FieldMayBeFinal")
public class ConfigFile {
    private CompanySettings companySettings = new CompanySettings();
    private UserSettings userSettings = new UserSettings();
    private OrderSettings orderSetting = new OrderSettings();
    private GeneralSettings generalSettings = new GeneralSettings();
    private DatabaseSettings databaseSettings = new DatabaseSettings();

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
