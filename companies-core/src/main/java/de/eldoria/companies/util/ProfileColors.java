/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves the stored colour of a company and checks colours against the configured palette.
 */
public final class ProfileColors {
    private static final Pattern HEX = Pattern.compile("#[0-9a-f]{6}");

    private ProfileColors() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Resolves a colour name or a {@code #rrggbb} value.
     *
     * @param color name of a named text colour or hex value, in any case
     * @return the colour, or empty when the value is neither
     */
    public static Optional<TextColor> resolve(String color) {
        var value = color.strip().toLowerCase(Locale.ROOT);
        if (HEX.matcher(value).matches()) return Optional.ofNullable(TextColor.fromHexString(value));
        return Optional.ofNullable(NamedTextColor.NAMES.value(value));
    }

    /**
     * Normalises a colour a player chose and checks that it is part of the palette.
     *
     * @param color    colour as entered
     * @param settings profile settings with the palette
     * @return the colour in lower case as it is stored, or empty when it is unknown or not in the palette
     */
    public static Optional<String> fromPalette(String color, ProfileSettings settings) {
        var value = color.strip().toLowerCase(Locale.ROOT);
        if (resolve(value).isEmpty()) return Optional.empty();
        return settings.colors().stream()
                .filter(entry -> entry.strip().equalsIgnoreCase(value))
                .findFirst()
                .map(entry -> value);
    }
}
