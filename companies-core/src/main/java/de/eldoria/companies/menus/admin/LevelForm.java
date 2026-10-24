/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reads the level editor form. Every entered value is parsed before anything changes, so a form with one invalid
 * value changes nothing.
 */
public final class LevelForm {
    /**
     * Key of the input choosing the position of a level.
     */
    public static final String POSITION = "position";

    private LevelForm() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Reads the inputs of the form.
     *
     * @param inputs text of an input by its key, null when the form has no such input
     * @return the text of each field the form holds
     */
    public static Map<LevelField, String> read(Function<String, String> inputs) {
        var entered = new EnumMap<LevelField, String>(LevelField.class);
        for (var field : LevelField.values()) {
            var text = inputs.apply(field.key());
            if (text != null) entered.put(field, text);
        }
        return entered;
    }

    /**
     * Reads a chosen position.
     *
     * @param inputs text of an input by its key, null when the form has no such input
     * @return the position, or empty when none was chosen
     */
    public static OptionalInt position(Function<String, String> inputs) {
        var text = inputs.apply(POSITION);
        if (text == null) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * Parses every entered value.
     *
     * @param entered text of each field
     * @return the changes, or the first field holding an invalid value
     */
    public static Result parse(Map<LevelField, String> entered) {
        Consumer<CompanyLevel> changes = level -> {};
        for (var entry : entered.entrySet()) {
            try {
                changes = changes.andThen(entry.getKey().parse(entry.getValue()));
            } catch (CommandFailure failure) {
                return new Invalid(entry.getKey(), failure);
            }
        }
        return new Valid(changes);
    }

    /**
     * The outcome of parsing a form.
     */
    public sealed interface Result permits Valid, Invalid {}

    /**
     * Every value is valid.
     *
     * @param changes applies all values to a level
     */
    public record Valid(Consumer<CompanyLevel> changes) implements Result {}

    /**
     * A value is invalid.
     *
     * @param field   the field holding it
     * @param failure the error
     */
    public record Invalid(LevelField field, CommandFailure failure) implements Result {}
}
