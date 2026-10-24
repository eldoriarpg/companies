/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.companies.components.level.ICompanyLevel;
import de.eldoria.companies.menus.admin.LevelDescription;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor", "CanBeFinal"})
public class CompanyLevel implements ICompanyLevel {
    public static final CompanyLevel DEFAULT = new CompanyLevel();
    private LevelRequirement requirement = new LevelRequirement();
    private LevelSettings settings = new LevelSettings();
    private int level = -1;
    private String levelName = "none";

    public CompanyLevel() {}

    public void level(int level) {
        this.level = level;
    }

    /**
     * The description of the level as MiniMessage text with its values written in, for API users. Menus use
     * {@link LevelDescription#of(CompanyLevel)}, which keeps the values as placeholders.
     *
     * @return MiniMessage text using the style and locale tags of the plugin
     */
    @Override
    public String asComponent() {
        return LevelDescription.miniMessage(this);
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public String levelName() {
        return levelName;
    }

    @Override
    public LevelRequirement requirement() {
        return requirement;
    }

    @Override
    public LevelSettings settings() {
        return settings;
    }

    public void levelName(String levelName) {
        this.levelName = levelName;
    }
}
