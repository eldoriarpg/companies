/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import net.kyori.adventure.text.Component;

import java.util.stream.IntStream;

/**
 * Texts and inputs shared by the level menus.
 */
final class LevelTexts {
    private static final String COMMAND = "/companyadmin level ";

    private LevelTexts() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param level the level
     * @return number and name of the level, as {@code <level_number>} and {@code <level_name>}
     */
    static MenuText label(CompanyLevel level) {
        return label(level, "level");
    }

    /**
     * @param level  the level
     * @param prefix prefix of the keys, so several labels fit into one text
     * @return number and name of the level, as {@code <prefix_number>} and {@code <prefix_name>}
     */
    static MenuText label(CompanyLevel level, String prefix) {
        var number = Keys.of(prefix, "number");
        var name = Keys.of(prefix, "name");
        return MenuText.create()
                .text(
                        "<name><" + number + "> - <value><" + name + ">",
                        Values.number(number, level.level()),
                        Values.text(name, level.levelName()));
    }

    /**
     * @param texts   renders for the viewer
     * @param failure the error
     * @return the error in the error style
     */
    static Component error(MenuComponents texts, CommandFailure failure) {
        return texts.messages().serializeError(texts.player(), "<i18n:" + failure.localeKey() + ">", failure.values());
    }

    /**
     * @param texts     renders for the viewer
     * @param rejection why an action was refused
     * @return the error in the error style, naming the invalid field
     */
    static Component error(MenuComponents texts, LevelView.Rejection rejection) {
        var failure = rejection.failure();
        var field = rejection.field().map(f -> "<i18n:" + f.labelKey() + ">: ").orElse("");
        return texts.messages()
                .serializeError(texts.player(), field + "<i18n:" + failure.localeKey() + ">", failure.values());
    }

    /**
     * @param level the level
     * @return the typed move command of the level, waiting for the target
     */
    static String moveCommand(CompanyLevel level) {
        return COMMAND + "move " + level.level() + " ";
    }

    /**
     * @param level the level
     * @param field the field
     * @return the typed edit command of the field, waiting for the value
     */
    static String editCommand(CompanyLevel level, LevelField field) {
        return COMMAND + "edit " + level.level() + " " + field.key() + " ";
    }

    /**
     * A choice of the positions 1 to {@code last}.
     *
     * @param label    label of the input
     * @param last     highest position
     * @param selected position chosen at first
     * @return the input with the key {@link LevelForm#POSITION}
     */
    static DialogInput positionInput(Component label, int last, int selected) {
        var entries = IntStream.rangeClosed(1, Math.max(1, last))
                .mapToObj(position -> SingleOptionDialogInput.OptionEntry.create(
                        String.valueOf(position), Component.text(position), position == selected))
                .toList();
        return DialogInput.singleOption(LevelForm.POSITION, label, entries).build();
    }
}
