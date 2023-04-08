/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.companies.components.level.ICompanyLevel;
import de.eldoria.eldoutilities.localization.MessageComposer;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public class CompanyLevel implements ICompanyLevel {
    public static final CompanyLevel DEFAULT = new CompanyLevel();
    private final LevelRequirement requirement = new LevelRequirement();
    private final LevelSettings settings = new LevelSettings();
    private int level = -1;
    private String levelName = "none";

    public CompanyLevel() {
    }

    public void level(int level) {
        this.level = level;
    }

    @Override
    public String asComponent() {
        return MessageComposer.create()
                .text("<name>%s - <value>%s", level, levelName).newLine()
                .text("<heading>").localeCode("level.requirements").text(":").newLine()
                .space(2).text("<name>").localeCode("words.member").text(": <value>%s", requirement.memberCount()).newLine()
                .space(2).text("<name>").localeCode("level.orderCount").text(": <value>%s", requirement.orderCount()).newLine()
                .space(2).text("<name>").localeCode("level.deliveredItems").text(": <value>%s", requirement.deliveredItems()).newLine()
                .space(2).text("<name>").localeCode("level.earnedMoney").text(": <value>%s", requirement.earnedMoney()).newLine()
                .text("<heading>").localeCode("level.limits").text(":").newLine()
                .space(2).text("<name>").localeCode("level.maxMember").text(": <value>%s ", settings.maxMembers()).newLine()
                .space(2).text("<name>").localeCode("level.maxOrders").text(": <value>%s ", settings.maxOrders())
                .build();
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public String levelName() {
        return levelName;
    }

    public void levelName(String levelName) {
        this.levelName = levelName;
    }

    @Override
    public LevelRequirement requirement() {
        return requirement;
    }

    @Override
    public LevelSettings settings() {
        return settings;
    }
}
