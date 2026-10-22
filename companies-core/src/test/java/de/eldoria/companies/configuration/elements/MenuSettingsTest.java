/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.ConfigFile;
import de.eldoria.companies.menus.MenuMode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuSettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    @Test
    void menusDefaultToChat() {
        var config = mapper.readValue("style: {}", ConfigFile.class);

        assertEquals(MenuMode.CHAT, config.menus().mode());
    }

    @Test
    void menusAreReadAsSingleValue() {
        var config = mapper.readValue("menus: DIALOG", ConfigFile.class);

        assertEquals(MenuMode.DIALOG, config.menus().mode());
    }

    @Test
    void menusAreWrittenAsSingleValue() {
        var yaml = mapper.writeValueAsString(new ConfigFile());

        assertTrue(Pattern.compile("(?m)^menus: \"?CHAT\"?$").matcher(yaml).find(), yaml);
    }
}
