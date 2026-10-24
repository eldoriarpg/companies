/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.ordersettings;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.OrderSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

class RepeatingSettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    @Test
    void repeatingOrdersHaveTheAgreedDefaults() {
        var repeating = new OrderSettings().repeating();

        Assertions.assertTrue(repeating.enabled());
        Assertions.assertEquals(3, repeating.maxSeriesPerPlayer());
        Assertions.assertEquals(52, repeating.maxCopies());
        Assertions.assertEquals(365, repeating.maxDays());
        Assertions.assertEquals(5, repeating.checkMinutes());
    }

    @Test
    void configWithoutRepeatingGetsTheDefaults() {
        var settings = mapper.readValue("maxItems: 64", OrderSettings.class);

        Assertions.assertEquals(52, settings.repeating().maxCopies());
    }

    @Test
    void repeatingIsReadFromYaml() {
        var settings = mapper.readValue("""
                repeating:
                  enabled: false
                  maxSeriesPerPlayer: 1
                  maxCopies: 10
                  maxDays: 30
                  checkMinutes: 0
                """, OrderSettings.class);

        var repeating = settings.repeating();
        Assertions.assertFalse(repeating.enabled());
        Assertions.assertEquals(1, repeating.maxSeriesPerPlayer());
        Assertions.assertEquals(10, repeating.maxCopies());
        Assertions.assertEquals(30, repeating.maxDays());
        Assertions.assertEquals(1, repeating.checkMinutes());
    }

    @Test
    void writtenSettingsAreReadBackUnchanged() {
        var yaml = mapper.writeValueAsString(new OrderSettings());

        Assertions.assertTrue(yaml.contains("repeating:"), yaml);
        Assertions.assertEquals(
                3, mapper.readValue(yaml, OrderSettings.class).repeating().maxSeriesPerPlayer());
    }
}
