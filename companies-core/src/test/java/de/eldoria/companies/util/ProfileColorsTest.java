/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class ProfileColorsTest {
    private final ProfileSettings settings = new ProfileSettings();

    @Test
    void namedColorsResolveInAnyCase() {
        Assertions.assertEquals(Optional.of(NamedTextColor.DARK_AQUA), ProfileColors.resolve("Dark_Aqua"));
    }

    @Test
    void hexColorsResolve() {
        Assertions.assertEquals(Optional.of(TextColor.color(0x12AB34)), ProfileColors.resolve("#12ab34"));
        Assertions.assertEquals(Optional.of(TextColor.color(0x12AB34)), ProfileColors.resolve("#12AB34"));
    }

    @Test
    void unknownColorsDoNotResolve() {
        Assertions.assertTrue(ProfileColors.resolve("pink").isEmpty());
        Assertions.assertTrue(ProfileColors.resolve("#12ab3").isEmpty());
        Assertions.assertTrue(ProfileColors.resolve("#12ab3g").isEmpty());
    }

    @Test
    void paletteColorIsStoredInLowerCase() {
        Assertions.assertEquals(Optional.of("gold"), ProfileColors.fromPalette("GOLD", settings));
    }

    @Test
    void colorsOutsideThePaletteAreRefused() {
        Assertions.assertTrue(ProfileColors.fromPalette("black", settings).isEmpty());
        Assertions.assertTrue(ProfileColors.fromPalette("#ff0000", settings).isEmpty());
    }

    @Test
    void hexColorsCanBeAddedToThePalette() {
        settings.colors(List.of("#FF8800", "nope"));

        Assertions.assertEquals(Optional.of("#ff8800"), ProfileColors.fromPalette("#ff8800", settings));
        Assertions.assertTrue(ProfileColors.fromPalette("nope", settings).isEmpty());
    }
}
