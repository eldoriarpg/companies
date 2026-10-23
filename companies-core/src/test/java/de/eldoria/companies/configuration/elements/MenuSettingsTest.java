/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.ConfigFile;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.menus.MenuMode;
import dev.chojo.ocular.Configurations;
import dev.chojo.ocular.dataformats.YamlDataFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuSettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    @Test
    void freshConfigurationsShowDialogs() {
        var yaml = mapper.writeValueAsString(ConfigFile.fresh());

        assertTrue(Pattern.compile("(?m)^menus: \"?DIALOG\"?$").matcher(yaml).find(), yaml);
        assertEquals(
                MenuMode.DIALOG,
                mapper.readValue(yaml, ConfigFile.class).menus().mode());
    }

    @Test
    void configurationsWithoutTheSettingKeepChat() {
        var config = mapper.readValue("style: {}", ConfigFile.class);

        assertEquals(MenuMode.CHAT, config.menus().mode());
    }

    @Test
    void menusWithoutValueShowChat(@TempDir Path base) throws IOException {
        Files.writeString(base.resolve("config.yml"), "menus:\n");
        var configurations = Configurations.builder(Configuration.CONFIG_YML, new YamlDataFormat())
                .setBase(base)
                .build();

        assertEquals(MenuMode.CHAT, configurations.main().menus().mode());
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
