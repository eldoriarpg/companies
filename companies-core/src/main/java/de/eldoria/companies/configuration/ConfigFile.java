/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.*;

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
