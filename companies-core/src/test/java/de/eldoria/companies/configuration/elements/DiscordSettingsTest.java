/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.integrations.discord.WebhookEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordSettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    private DiscordSettings read(String yaml) {
        return mapper.readValue(yaml, DiscordSettings.class);
    }

    @Test
    void defaultsAreDisabledWithoutUrl() {
        var settings = new DiscordSettings();

        assertFalse(settings.enabled());
        assertEquals("", settings.url());
        assertTrue(settings.posts(WebhookEvent.ORDER_CREATED));
        assertTrue(settings.posts(WebhookEvent.ORDER_COMPLETED));
        assertTrue(settings.posts(WebhookEvent.LEVEL_UP));
        assertEquals(Optional.of("Companies"), settings.username());
        assertEquals(Optional.empty(), settings.avatarUrl());
        assertEquals(20, settings.maxPerMinute());
        assertEquals(100, settings.queueSize());
    }

    @Test
    void eventsCanBeTurnedOff() {
        var settings = read("events: [LEVEL_UP]");

        assertFalse(settings.posts(WebhookEvent.ORDER_CREATED));
        assertFalse(settings.posts(WebhookEvent.ORDER_COMPLETED));
        assertTrue(settings.posts(WebhookEvent.LEVEL_UP));
        assertFalse(read("events: []").posts(WebhookEvent.LEVEL_UP));
    }

    @Test
    void valuesAreKeptInRange() {
        assertEquals(1, read("maxPerMinute: 0").maxPerMinute());
        assertEquals(60, read("maxPerMinute: 500").maxPerMinute());
        assertEquals(1, read("queueSize: -3").queueSize());
        assertEquals(Optional.empty(), read("username: ' '").username());
        assertEquals("env:HOOK", read("url: ' env:HOOK '").url());
    }
}
