package de.eldoria.companies.configuration.elements.companylevel;

import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.serialization.SerializationUtil;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CompanyLevel implements ConfigurationSerializable {
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

    public String asComponent() {
        return MessageComposer.create()
                .text("<%s>%s - <%s>%s", Colors.NAME, level, Colors.VALUE, levelName).newLine()
                .text("<%s>", Colors.HEADING).localeCode("Requirements").text(":").newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("Members").text(": <%s>%s", Colors.VALUE, requirement.memberCount()).newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("Order count").text(": <%s>%s", Colors.VALUE, requirement.orderCount()).newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("Delivered Items").text(": <%s>%s", Colors.VALUE, requirement.deliveredItems()).newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("Earned Money").text(": <%s>%s", Colors.VALUE, requirement.earnedMoney()).newLine()
                .text("<%s>", Colors.HEADING).localeCode("Limits").text(":").newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("Max Member").text(": <%s>%s ", Colors.VALUE, settings.maxMembers()).newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("Max Orders").text(": <%s>%s ", Colors.VALUE, settings.maxOrders())
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
