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

class SoloSettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    @Test
    void soloOrdersAreOffByDefault() {
        var solo = new OrderSettings().solo();

        Assertions.assertFalse(solo.enabled());
        Assertions.assertEquals(1, solo.maxActive());
        Assertions.assertEquals(64, solo.maxOrderSize());
        Assertions.assertEquals(24, solo.deliveryHours());
        Assertions.assertEquals(0.0, solo.taxPercent());
        Assertions.assertFalse(solo.forCompanyMembers());
        Assertions.assertEquals(60, solo.abortCooldownMinutes());
    }

    @Test
    void configWithoutSoloGetsTheDefaults() {
        var settings = mapper.readValue("maxItems: 64", OrderSettings.class);

        Assertions.assertFalse(settings.solo().enabled());
        Assertions.assertEquals(1, settings.solo().maxActive());
    }

    @Test
    void soloIsReadFromYamlAndClamped() {
        var settings = mapper.readValue("""
                solo:
                  enabled: true
                  maxActive: 0
                  maxOrderSize: -5
                  deliveryHours: 0
                  taxPercent: 120.0
                  forCompanyMembers: true
                  abortCooldownMinutes: -1
                """, OrderSettings.class);

        var solo = settings.solo();
        Assertions.assertTrue(solo.enabled());
        Assertions.assertEquals(1, solo.maxActive());
        Assertions.assertEquals(0, solo.maxOrderSize());
        Assertions.assertEquals(1, solo.deliveryHours());
        Assertions.assertEquals(100.0, solo.taxPercent());
        Assertions.assertTrue(solo.forCompanyMembers());
        Assertions.assertEquals(0, solo.abortCooldownMinutes());
    }

    @Test
    void theSizeLimitAppliesOnlyWhenSet() {
        var solo = new SoloSettings();

        Assertions.assertFalse(solo.exceedsOrderSize(64));
        Assertions.assertTrue(solo.exceedsOrderSize(65));
        solo.maxOrderSize(0);
        Assertions.assertFalse(solo.exceedsOrderSize(10_000));
    }

    @Test
    void writtenSettingsAreReadBackUnchanged() {
        var yaml = mapper.writeValueAsString(new OrderSettings());

        Assertions.assertTrue(yaml.contains("solo:"), yaml);
        Assertions.assertEquals(
                60, mapper.readValue(yaml, OrderSettings.class).solo().abortCooldownMinutes());
    }
}
