/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.companies.components.level.ICompanyLevel;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CompanyLevel implements ConfigurationSerializable, ICompanyLevel {
    public static final CompanyLevel DEFAULT = new CompanyLevel();

    private int level = -1;
    private String levelName = "none";
    private LevelRequirement requirement = new LevelRequirement();
    private LevelSettings settings = new LevelSettings();

    public CompanyLevel() {
    }

    public CompanyLevel(Map<String, Object> objectMap) {
        var map = SerializationUtil.mapOf(objectMap);
        levelName = map.getValueOrDefault("levelName", levelName);
        requirement = map.getValueOrDefault("requirement", requirement);
        settings = map.getValueOrDefault("settings", settings);
    }

    public void level(int level) {
        this.level = level;
    }

    @Override
    public String asComponent() {
        return MessageComposer.create()
                .text("<%s>%s - <%s>%s", Colors.NAME, level, Colors.VALUE, levelName).newLine()
                .text("<%s>", Colors.HEADING).localeCode("level.requirements").text(":").newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("words.member").text(": <%s>%s", Colors.VALUE, requirement.memberCount()).newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.orderCount").text(": <%s>%s", Colors.VALUE, requirement.orderCount()).newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.deliveredItems").text(": <%s>%s", Colors.VALUE, requirement.deliveredItems()).newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.earnedMoney").text(": <%s>%s", Colors.VALUE, requirement.earnedMoney()).newLine()
                .text("<%s>", Colors.HEADING).localeCode("level.limits").text(":").newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.maxMember").text(": <%s>%s ", Colors.VALUE, settings.maxMembers()).newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.maxOrders").text(": <%s>%s ", Colors.VALUE, settings.maxOrders())
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

    @Override
    @NotNull
    public Map<String, Object> serialize() {
        return SerializationUtil.newBuilder()
                .add("levelName", levelName)
                .add("requirement", requirement)
                .add("settings", settings)
                .build();
    }
}
