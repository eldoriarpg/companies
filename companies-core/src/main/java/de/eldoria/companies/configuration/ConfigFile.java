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
import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.companies.configuration.elements.UserSettings;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class ConfigFile {
    private GeneralSettings generalSettings = new GeneralSettings();
    private StyleSettings style = new StyleSettings();

    public GeneralSettings generalSettings() {
        return generalSettings;
    }

    public StyleSettings style() {
        return style;
    }
}
