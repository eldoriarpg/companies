/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import de.eldoria.companies.configuration.elements.EconomySettings;
import de.eldoria.companies.configuration.elements.GeneralSettings;
import de.eldoria.companies.configuration.elements.MenuSettings;
import de.eldoria.companies.configuration.elements.StyleSettings;

@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class ConfigFile {
    private GeneralSettings generalSettings = new GeneralSettings();
    private StyleSettings style = new StyleSettings();
    private MenuSettings menus = new MenuSettings();
    private EconomySettings economy = new EconomySettings();

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
}
