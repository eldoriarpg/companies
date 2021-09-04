package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CompanyLevel implements ConfigurationSerializable {
    private int level;
    private String levelName = "Level";
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

    public String asComponent() {
        return MessageComposer.create()
                .text("%s - %s", level, levelName).newLine()
                .localeCode("Requirements").text(":").newLine()
                .space().localeCode("Members").text(": %s", requirement.memberCount()).newLine()
                .space().localeCode("Order count").text(": %s", requirement.orderCount()).newLine()
                .space().localeCode("Delivered Items").text(": %s", requirement.deliveredItems()).newLine()
                .space().localeCode("Earned Money").text(": %s", requirement.earnedMoney()).newLine()
                .localeCode("Limits").text(":").newLine()
                .space().localeCode("Max Member").text(": %s ", settings.maxMembers()).newLine()
                .space().localeCode("Max Orders").text(": %s ", settings.maxOrders())
                .build();
    }

    public int level() {
        return level;
    }

    public String levelName() {
        return levelName;
    }

    public void levelName(String levelName) {
        this.levelName = levelName;
    }

    public LevelRequirement requirement() {
        return requirement;
    }

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
