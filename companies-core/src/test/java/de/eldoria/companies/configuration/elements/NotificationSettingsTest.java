/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationSettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    private NotificationSettings read(String yaml) {
        return mapper.readValue(yaml, NotificationSettings.class);
    }

    @Test
    void defaults() {
        var settings = new NotificationSettings();

        assertEquals(10, settings.pollSeconds());
        assertEquals(10, settings.pageSize());
        assertTrue(settings.actionBar());
        assertEquals(Optional.of("minecraft:entity.experience_orb.pickup"), settings.sound());
    }

    @Test
    void valuesAreKeptInRange() {
        assertEquals(1, read("pollSeconds: 0").pollSeconds());
        assertEquals(1, read("pageSize: -2").pageSize());
        assertEquals(50, read("pageSize: 500").pageSize());
        assertEquals(25, read("pageSize: 25").pageSize());
    }

    @Test
    void blankSoundPlaysNothing() {
        assertEquals(Optional.empty(), read("sound: ''").sound());
        assertEquals(
                Optional.of("minecraft:block.note_block.bell"),
                read("sound: ' minecraft:block.note_block.bell '").sound());
        assertFalse(read("actionBar: false").actionBar());
    }
}
