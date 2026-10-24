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

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardSettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    private BoardSettings read(String yaml) {
        return mapper.readValue(yaml, BoardSettings.class);
    }

    @Test
    void defaultsHaveTheIronExample() {
        var settings = new BoardSettings();

        assertTrue(settings.enabled());
        assertEquals(Duration.ofSeconds(60), settings.refresh());
        assertEquals("[Companies]", settings.signHeader());
        assertEquals(List.of("iron"), List.copyOf(settings.presets().keySet()));
        var iron = settings.preset("iron").orElseThrow().filter();
        assertEquals(List.of("iron_ingot", "iron_block", "raw_iron"), iron.materials());
        assertTrue(iron.exactMatch());
        assertTrue(iron.anyMaterial());
    }

    @Test
    void presetsAreReadWithTheirFilter() {
        var settings = read("""
                presets:
                  Gold:
                    label: "Gold bars"
                    name: " bars "
                    materials: [Gold_Ingot, " gold_block ", ""]
                    anyMaterial: false
                    minPrice: 10
                    maxPrice: 500
                    minSize: 5
                    maxSize: 64
                """);

        var gold = settings.preset("GOLD").orElseThrow();
        var filter = gold.filter();
        assertEquals("Gold bars", gold.label("gold"));
        assertEquals("bars", filter.name());
        assertEquals(List.of("gold_ingot", "gold_block"), filter.materials());
        assertFalse(filter.anyMaterial());
        assertFalse(filter.exactMatch());
        assertEquals(10.0, filter.minPrice());
        assertEquals(500.0, filter.maxPrice());
        assertEquals(5, filter.minSize());
        assertEquals(64, filter.maxSize());
        assertEquals(List.of("gold"), List.copyOf(settings.presets().keySet()));
    }

    @Test
    void missingValuesMeanNoLimit() {
        var settings = read("""
                presets:
                  any: {}
                """);

        var any = settings.preset("any").orElseThrow();
        var filter = any.filter();
        assertEquals("any", any.label("any"));
        assertNull(filter.name());
        assertTrue(filter.materials().isEmpty());
        assertEquals(Double.MAX_VALUE, filter.maxPrice());
        assertEquals(Integer.MAX_VALUE, filter.maxSize());
    }

    @Test
    void valuesAreKeptInRange() {
        var settings = read("""
                refreshSeconds: 1
                signHeader: "  "
                presets:
                  odd:
                    minPrice: -5
                    maxPrice: 3
                    minSize: 10
                    maxSize: 2
                """);

        var filter = settings.preset("odd").orElseThrow().filter();
        assertEquals(Duration.ofSeconds(10), settings.refresh());
        assertEquals("[Companies]", settings.signHeader());
        assertEquals(0.0, filter.minPrice());
        assertEquals(3.0, filter.maxPrice());
        assertEquals(10, filter.minSize());
        assertEquals(10, filter.maxSize());
    }

    @Test
    void emptyPresetsStayEmpty() {
        var settings = read("presets: {}");

        assertTrue(settings.presets().isEmpty());
        assertTrue(settings.preset("iron").isEmpty());
    }
}
