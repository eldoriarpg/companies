/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.companies.components.level.ICompanyLevel;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;

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

    @Override
    public String asComponent() {
        var composer = MessageComposer.create()
                .text("<name>%s - <value>%s", level, levelName)
                .newLine()
                .text("<heading>")
                .localeCode("level.requirements")
                .text(":")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("words.member")
                .text(": <value>%s", requirement.memberCount())
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.orderCount")
                .text(": <value>%s", requirement.orderCount())
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.deliveredItems")
                .text(": <value>%s", requirement.deliveredItems())
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.earnedMoney")
                .text(": <value>%s", requirement.earnedMoney())
                .newLine()
                .text("<heading>")
                .localeCode("level.limits")
                .text(":")
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.maxMember")
                .text(": <value>%s ", settings.maxMembers())
                .newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.maxOrders")
                .text(": <value>%s ", settings.maxOrders());
        settings.taxPercent().ifPresent(percent -> composer.newLine()
                .space(2)
                .text("<name>")
                .localeCode("level.tax")
                .text(": <value>%s %%", Texts.percent(percent)));
        return composer.build();
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
