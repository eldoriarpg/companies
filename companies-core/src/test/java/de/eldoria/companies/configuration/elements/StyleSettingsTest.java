/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.ConfigFile;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StyleSettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    @Test
    void defaultsKeepThePreviousLook() {
        var style = new StyleSettings();

        assertEquals("<#00a6ff>[C]", style.prefix());
        assertEquals(NamedTextColor.DARK_AQUA, style.message());
        assertEquals(TextColor.fromHexString("#f05316"), style.error());
        assertEquals(
                Map.of(
                        "heading", NamedTextColor.GOLD,
                        "name", NamedTextColor.AQUA,
                        "value", NamedTextColor.DARK_GREEN,
                        "remove", NamedTextColor.RED,
                        "add", NamedTextColor.GREEN,
                        "modify", NamedTextColor.YELLOW,
                        "show", NamedTextColor.GREEN,
                        "inactive", NamedTextColor.DARK_GRAY,
                        "active", NamedTextColor.GREEN,
                        "neutral", NamedTextColor.DARK_AQUA),
                style.tags());
    }

    @Test
    void hexAndNamedColoursAreRead() {
        var style = mapper.readValue("""
                heading: "#123456"
                error: Light_Purple
                """, StyleSettings.class);

        assertEquals(TextColor.color(0x123456), style.tags().get("heading"));
        assertEquals(NamedTextColor.LIGHT_PURPLE, style.error());
    }

    @Test
    void invalidColourFallsBackToTheDefault() {
        var style = mapper.readValue("""
                name: not-a-colour
                message: "#12"
                """, StyleSettings.class);

        assertEquals(NamedTextColor.AQUA, style.tags().get("name"));
        assertEquals(NamedTextColor.DARK_AQUA, style.message());
    }

    @Test
    void missingStyleSectionUsesTheDefaults() {
        var config = mapper.readValue("generalSettings:\n  language: de_DE", ConfigFile.class);

        assertEquals("<#00a6ff>[C]", config.style().prefix());
    }
}
