/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration;

import java.util.List;
import java.util.Optional;

/**
 * The pages of the guided setup on the dashboard, in order. A page shows a few settings of the {@link SettingsCatalogue}
 * and saves them like the settings editor does; a page without settings explains something or links another tool,
 * such as the level editor. The setup only picks and orders settings, so what it saves is checked and shared as any
 * other change.
 */
public final class SetupPages {
    private static final String CONFIG = "config.yml";
    private static final String COMPANY = "company.yml";
    private static final String ORDER = "order.yml";

    /**
     * Every page, in the order the setup walks through them.
     */
    public static final List<Page> PAGES = List.of(
            new Page(
                    "economy",
                    List.of(
                            field(COMPANY, "foundingPrice"),
                            field(COMPANY, "renamePrice"),
                            field(COMPANY, "tax.percent"),
                            field(ORDER, "fee.fixed"),
                            field(ORDER, "fee.percent"),
                            field(ORDER, "fee.min"),
                            field(ORDER, "fee.max"),
                            field(CONFIG, "economy.collectorAccount"))),
            new Page("levels", List.of()),
            new Page(
                    "language",
                    List.of(
                            field(CONFIG, "generalSettings.language"),
                            field(CONFIG, "generalSettings.timezone"),
                            field(CONFIG, "menus"))),
            new Page(
                    "integrations",
                    List.of(
                            field(CONFIG, "generalSettings.hooks.placeholderapi"),
                            field(CONFIG, "generalSettings.hooks.citizens"),
                            field(CONFIG, "generalSettings.hooks.fancynpcs"),
                            field(CONFIG, "boards.enabled"),
                            field(CONFIG, "discord.enabled"),
                            field(CONFIG, "discord.url"),
                            field(CONFIG, "discord.events"))),
            new Page(
                    "dashboard",
                    List.of(
                            field(CONFIG, "dashboard.branding.name"),
                            field(CONFIG, "dashboard.branding.icon"),
                            field(CONFIG, "dashboard.features"))),
            new Page("permissions", List.of()));

    private SetupPages() {}

    /**
     * @param id a page's id
     * @return the page of that id
     */
    public static Optional<Page> page(String id) {
        return PAGES.stream().filter(page -> page.id().equals(id)).findFirst();
    }

    private static SettingsCatalogue.Field field(String file, String key) {
        return SettingsCatalogue.field(file, key)
                .orElseThrow(() -> new IllegalStateException(file + " " + key + " is no setting"));
    }

    /**
     * One page of the setup.
     *
     * @param id     its id, such as {@code economy}
     * @param fields the settings it shows, none for a page that explains or links
     */
    public record Page(String id, List<SettingsCatalogue.Field> fields) {}
}
