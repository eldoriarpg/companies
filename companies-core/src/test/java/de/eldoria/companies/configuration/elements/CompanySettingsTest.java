/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.List;

class CompanySettingsTest {
    private final ObjectMapper mapper = YAMLMapper.builder()
                                                  .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                                                  .build();

    private static CompanySettings withoutLevels() {
        var settings = new CompanySettings();
        settings.level().clear();
        return settings;
    }

    @Test
    void deletingALevelRenumbersTheFollowingLevels() {
        var settings = withoutLevels();
        settings.createLevel(1);
        var second = settings.createLevel(2);
        settings.createLevel(3);

        Assertions.assertTrue(settings.deleteLevel(1));

        Assertions.assertEquals(1, second.level());
        Assertions.assertEquals(second, settings.level(1).orElseThrow());
        Assertions.assertEquals(List.of(1, 2), settings.level().stream().map(CompanyLevel::level).toList());
    }

    @Test
    void levelsOutsideTheRangeAreEmpty() {
        var settings = withoutLevels();
        settings.createLevel(1);

        Assertions.assertTrue(settings.level(0).isEmpty());
        Assertions.assertTrue(settings.level(2).isEmpty());
    }

    @Test
    void freshSettingsStartWithTheStarterLadder() {
        var settings = new CompanySettings();

        Assertions.assertEquals(List.of("Startup", "Business", "Corporation", "Conglomerate"),
                settings.level().stream().map(CompanyLevel::levelName).toList());
        Assertions.assertEquals(List.of(1, 2, 3, 4), settings.level().stream().map(CompanyLevel::level).toList());
    }

    @Test
    void starterLevelsMatchTheAgreedValues() {
        var corporation = new CompanySettings().level(3).orElseThrow();

        Assertions.assertEquals(50, corporation.requirement().orderCount());
        Assertions.assertEquals(100_000.0, corporation.requirement().earnedMoney());
        Assertions.assertEquals(10_000, corporation.requirement().deliveredItems());
        Assertions.assertEquals(4, corporation.requirement().memberCount());
        Assertions.assertEquals(20, corporation.settings().maxMembers());
        Assertions.assertEquals(7, corporation.settings().maxOrders());
    }

    @Test
    void configWithoutLevelKeyGetsTheStarterLadder() {
        var settings = mapper.readValue("deliveryHours: 24", CompanySettings.class);

        Assertions.assertEquals(4, settings.level().size());
    }

    @Test
    void configWithEmptyLevelListStaysEmpty() {
        var settings = mapper.readValue("level: []", CompanySettings.class);

        Assertions.assertTrue(settings.level().isEmpty());
    }

    @Test
    void configWithOwnLevelsKeepsThem() {
        var settings = mapper.readValue("""
                level:
                  - levelName: Solo
                    level: 1
                """, CompanySettings.class);

        Assertions.assertEquals(List.of("Solo"), settings.level().stream().map(CompanyLevel::levelName).toList());
    }

    @Test
    void writtenStarterLadderIsReadBackUnchanged() {
        var yaml = mapper.writeValueAsString(new CompanySettings());
        var settings = mapper.readValue(yaml, CompanySettings.class);

        Assertions.assertEquals(List.of(1, 2, 3, 4), settings.level().stream().map(CompanyLevel::level).toList());
        Assertions.assertEquals(12, settings.level(4).orElseThrow().settings().maxOrders());
    }
}
