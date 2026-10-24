/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Texts;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes a company level: its number and name, the requirements and the limits.
 * <p>
 * Settings that change nothing, such as no order size limit, are left out. The values are named placeholders: the
 * number and name are {@code <level_number>} and {@code <level_name>}, every other value is named by its
 * {@link LevelField#key() field key}, such as {@code <order_count>}.
 */
public final class LevelDescription {
    private LevelDescription() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param level the level
     * @return the description, with the values as placeholders
     */
    public static MenuText of(CompanyLevel level) {
        var text = MenuText.create();
        for (var row : rows(level)) {
            text.text(row.template(), row.placeholders());
        }
        return text;
    }

    /**
     * The description as one MiniMessage text with the values written in and escaped, for API users who expect a
     * plain text.
     *
     * @param level the level
     * @return MiniMessage text using the style tags and locale tags of the plugin
     */
    public static String miniMessage(CompanyLevel level) {
        var text = new StringBuilder();
        for (var row : rows(level)) {
            var line = row.template();
            for (var value : row.values().entrySet()) {
                line = line.replace("<" + value.getKey() + ">", Texts.escape(value.getValue()));
            }
            text.append(line);
        }
        return text.toString();
    }

    private static List<Row> rows(CompanyLevel level) {
        var values = new LinkedHashMap<String, String>();
        values.put("level_number", String.valueOf(level.level()));
        values.put("level_name", level.levelName());
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("<name><level_number> - <value><level_name>", values));
        rows.addAll(group(level, LevelField.Group.REQUIREMENT));
        rows.addAll(group(level, LevelField.Group.LIMIT));
        return rows;
    }

    private static List<Row> group(CompanyLevel level, LevelField.Group group) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("\n<heading><i18n:" + group.headingKey() + ">:", Map.of()));
        for (var field : LevelField.values()) {
            if (field.group() != group) continue;
            field.shown(level)
                    .ifPresent(shown -> rows.add(new Row(
                            "\n  <name><i18n:" + field.labelKey() + ">: <value><" + field.key() + ">",
                            Map.of(field.key(), shown))));
        }
        return rows;
    }

    /**
     * One line of the description.
     *
     * @param template MiniMessage text using the placeholders
     * @param values   value of each placeholder
     */
    private record Row(String template, Map<String, String> values) {
        TagResolver.Single[] placeholders() {
            return values.entrySet().stream()
                    .map(value -> Values.text(value.getKey(), value.getValue()))
                    .toArray(TagResolver.Single[]::new);
        }
    }
}
