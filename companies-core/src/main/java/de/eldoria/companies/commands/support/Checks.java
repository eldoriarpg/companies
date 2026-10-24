/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.support;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Checks of command input that end the command with a {@link CommandFailure}.
 */
public final class Checks {
    private Checks() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param condition condition that has to hold
     * @param localeKey error shown when it does not
     * @param values    values of the placeholders in the error
     */
    public static void isTrue(boolean condition, String localeKey, TagResolver... values) {
        if (!condition) throw CommandFailure.of(localeKey, values);
    }

    /**
     * @param condition condition that must not hold
     * @param localeKey error shown when it does
     * @param values    values of the placeholders in the error
     */
    public static void isFalse(boolean condition, String localeKey, TagResolver... values) {
        isTrue(!condition, localeKey, values);
    }

    /**
     * Rejects input of {@code max} characters or more, like EldoUtil's length check did.
     *
     * @param input entered text
     * @param max   length the input has to stay below
     */
    public static void shorterThan(String input, int max) {
        isTrue(input.length() < max, "error.invalidLength", Placeholder.unparsed("max", String.valueOf(max)));
    }

    /**
     * @param input entered text
     * @return the number
     */
    public static int integer(String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            throw CommandFailure.of("error.invalidNumber");
        }
    }

    /**
     * @param input entered text
     * @return the finite number
     */
    public static double finiteDouble(String input) {
        try {
            var value = Double.parseDouble(input.trim());
            isTrue(Double.isFinite(value), "error.invalidNumber");
            return value;
        } catch (NumberFormatException e) {
            throw CommandFailure.of("error.invalidNumber");
        }
    }

    /**
     * Parses an enum constant by its name, ignoring case.
     *
     * @param input entered text
     * @param type  enum class
     * @param <T>   enum type
     * @return the constant
     */
    public static <T extends Enum<T>> T enumValue(String input, Class<T> type) {
        return parseEnum(input, type)
                .orElseThrow(() -> CommandFailure.of(
                        "error.invalidEnumValue",
                        Placeholder.unparsed(
                                "values",
                                Arrays.stream(type.getEnumConstants())
                                        .map(value -> value.name().toLowerCase(Locale.ROOT))
                                        .collect(Collectors.joining(", ")))));
    }

    /**
     * @param input entered text
     * @param type  enum class
     * @param <T>   enum type
     * @return the constant named like the input, ignoring case
     */
    public static <T extends Enum<T>> Optional<T> parseEnum(String input, Class<T> type) {
        return Arrays.stream(type.getEnumConstants())
                .filter(value -> value.name().equalsIgnoreCase(input))
                .findFirst();
    }
}
