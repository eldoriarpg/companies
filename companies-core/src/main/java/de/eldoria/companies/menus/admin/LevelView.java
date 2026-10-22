/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The model of the editor of one level.
 *
 * @param level      the edited level
 * @param levelCount how many levels exist, the positions the level can move to
 * @param entered    the text of each input, the current values unless a refused entry is shown again
 * @param rejection  why the last action was refused, shown on top
 */
public record LevelView(
        CompanyLevel level, int levelCount, Map<LevelField, String> entered, Optional<Rejection> rejection) {
    /**
     * @param level      the edited level
     * @param levelCount how many levels exist, the positions the level can move to
     * @param entered    the text of each input, the current values unless a refused entry is shown again
     * @param rejection  why the last action was refused, shown on top
     */
    public LevelView {
        entered = Collections.unmodifiableMap(copy(entered));
    }

    /**
     * @param level      the edited level
     * @param levelCount how many levels exist
     * @return the editor showing the current values
     */
    public static LevelView of(CompanyLevel level, int levelCount) {
        Map<LevelField, String> current = new EnumMap<>(LevelField.class);
        for (var field : LevelField.values()) {
            current.put(field, field.input(level));
        }
        return new LevelView(level, levelCount, current, Optional.empty());
    }

    /**
     * @param entered   what was entered
     * @param rejection why it was refused
     * @return the editor showing the entry again with the reason on top
     */
    public LevelView rejected(Map<LevelField, String> entered, Rejection rejection) {
        return new LevelView(level, levelCount, entered, Optional.of(rejection));
    }

    /**
     * @param failure why the last action was refused
     * @return the editor showing the failure on top
     */
    public LevelView rejected(CommandFailure failure) {
        return new LevelView(level, levelCount, entered, Optional.of(new Rejection(Optional.empty(), failure)));
    }

    /**
     * @param field input to read
     * @return the text of the input
     */
    public String entered(LevelField field) {
        return entered.getOrDefault(field, "");
    }

    private static Map<LevelField, String> copy(Map<LevelField, String> entered) {
        Map<LevelField, String> copy = new EnumMap<>(LevelField.class);
        copy.putAll(entered);
        return copy;
    }

    /**
     * Why an action of the editor was refused.
     *
     * @param field   the input holding the invalid value, if one does
     * @param failure the error
     */
    public record Rejection(Optional<LevelField> field, CommandFailure failure) {}
}
