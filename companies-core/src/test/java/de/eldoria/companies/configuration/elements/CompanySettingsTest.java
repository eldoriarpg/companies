/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import de.eldoria.companies.components.company.JoinMode;
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
        Assertions.assertEquals(
                List.of(1, 2),
                settings.level().stream().map(CompanyLevel::level).toList());
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

        Assertions.assertEquals(
                List.of("Startup", "Business", "Corporation", "Conglomerate"),
                settings.level().stream().map(CompanyLevel::levelName).toList());
        Assertions.assertEquals(
                List.of(1, 2, 3, 4),
                settings.level().stream().map(CompanyLevel::level).toList());
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

        Assertions.assertEquals(
                List.of("Solo"),
                settings.level().stream().map(CompanyLevel::levelName).toList());
    }

    @Test
    void writtenStarterLadderIsReadBackUnchanged() {
        var yaml = mapper.writeValueAsString(new CompanySettings());
        var settings = mapper.readValue(yaml, CompanySettings.class);

        Assertions.assertEquals(
                List.of(1, 2, 3, 4),
                settings.level().stream().map(CompanyLevel::level).toList());
        Assertions.assertEquals(12, settings.level(4).orElseThrow().settings().maxOrders());
    }

    @Test
    void levelWithoutOverrideUsesTheGlobalTax() {
        var settings = new CompanySettings();
        settings.tax().percent(5);

        Assertions.assertEquals(5.0, settings.taxPercent(2));
    }

    @Test
    void levelOverrideReplacesTheGlobalTax() {
        var settings = new CompanySettings();
        settings.tax().percent(5);
        settings.level(3).orElseThrow().settings().taxPercent(2.5);

        Assertions.assertEquals(2.5, settings.taxPercent(3));
        Assertions.assertEquals(5.0, settings.taxPercent(4));
    }

    @Test
    void unknownLevelUsesTheGlobalTax() {
        var settings = new CompanySettings();
        settings.tax().percent(3);

        Assertions.assertEquals(3.0, settings.taxPercent(99));
    }

    @Test
    void taxDefaultsToZero() {
        Assertions.assertEquals(0.0, new CompanySettings().taxPercent(1));
    }

    @Test
    void unsetOverrideIsNotWritten() {
        var yaml = mapper.writeValueAsString(new CompanySettings());

        Assertions.assertFalse(yaml.contains("taxPercent"), yaml);
        Assertions.assertTrue(yaml.contains("tax:"), yaml);
    }

    @Test
    void overrideIsReadFromYaml() {
        var settings = mapper.readValue("""
                tax:
                  percent: 4
                level:
                  - levelName: Solo
                    settings:
                      taxPercent: 1.5
                  - levelName: Duo
                """, CompanySettings.class);

        Assertions.assertEquals(1.5, settings.taxPercent(1));
        Assertions.assertEquals(4.0, settings.taxPercent(2));
    }

    @Test
    void levelPerksDefaultToNone() {
        var level = new CompanySettings().levelSettings(1);

        Assertions.assertEquals(0, level.maxOrderSize());
        Assertions.assertEquals(0, level.deliveryHoursBonus());
        Assertions.assertFalse(level.exceedsOrderSize(1_000_000));
    }

    @Test
    void levelPerksAreReadFromYaml() {
        var settings = mapper.readValue("""
                level:
                  - levelName: Solo
                    settings:
                      maxOrderSize: 64
                      deliveryHoursBonus: 12
                """, CompanySettings.class);

        var level = settings.levelSettings(1);
        Assertions.assertEquals(12, level.deliveryHoursBonus());
        Assertions.assertTrue(level.exceedsOrderSize(65));
        Assertions.assertFalse(level.exceedsOrderSize(64));
    }

    @Test
    void negativeDeliveryBonusCountsAsNone() {
        var settings = new CompanySettings();
        settings.levelSettings(1).deliveryHoursBonus(-5);

        Assertions.assertEquals(0, settings.levelSettings(1).deliveryHoursBonus());
    }

    @Test
    void unknownLevelHasDefaultSettings() {
        Assertions.assertEquals(0, new CompanySettings().levelSettings(99).maxOrderSize());
    }

    @Test
    void joiningAndProfileHaveTheAgreedDefaults() {
        var settings = new CompanySettings();

        Assertions.assertEquals(24, settings.inviteHours());
        Assertions.assertEquals(JoinMode.INVITE_ONLY, settings.joining().defaultMode());
        Assertions.assertEquals(120, settings.joining().maxApplicationLength());
        Assertions.assertEquals(3, settings.joining().maxPendingApplications());
        Assertions.assertEquals(160, settings.profile().maxDescriptionLength());
        Assertions.assertEquals(2, settings.profile().tagLength().min());
        Assertions.assertEquals(5, settings.profile().tagLength().max());
        Assertions.assertEquals(
                List.of("ADMIN", "MOD", "STAFF"), settings.profile().blockedTags());
        Assertions.assertEquals(15, settings.profile().colors().size());
        Assertions.assertTrue(settings.profile().colors().contains("dark_purple"));
    }

    @Test
    void configWithoutJoiningAndProfileGetsTheDefaults() {
        var settings = mapper.readValue("deliveryHours: 24", CompanySettings.class);

        Assertions.assertEquals(JoinMode.INVITE_ONLY, settings.joining().defaultMode());
        Assertions.assertEquals(
                List.of("ADMIN", "MOD", "STAFF"), settings.profile().blockedTags());
    }

    @Test
    void joiningAndProfileAreReadFromYaml() {
        var settings = mapper.readValue("""
                joining:
                  defaultMode: OPEN
                  maxApplicationLength: 80
                  maxPendingApplications: 1
                profile:
                  maxDescriptionLength: 100
                  tagLength: {min: 3, max: 4}
                  blockedTags: [owner]
                  colors: [red, "#ff8800"]
                """, CompanySettings.class);

        Assertions.assertEquals(JoinMode.OPEN, settings.joining().defaultMode());
        Assertions.assertEquals(80, settings.joining().maxApplicationLength());
        Assertions.assertEquals(1, settings.joining().maxPendingApplications());
        Assertions.assertEquals(100, settings.profile().maxDescriptionLength());
        Assertions.assertEquals(3, settings.profile().tagLength().min());
        Assertions.assertEquals(4, settings.profile().tagLength().max());
        Assertions.assertEquals(List.of("OWNER"), settings.profile().blockedTags());
        Assertions.assertEquals(List.of("red", "#ff8800"), settings.profile().colors());
    }

    @Test
    void writtenJoiningAndProfileAreReadBackUnchanged() {
        var yaml = mapper.writeValueAsString(new CompanySettings());
        var settings = mapper.readValue(yaml, CompanySettings.class);

        Assertions.assertTrue(yaml.contains("joining:"), yaml);
        Assertions.assertTrue(yaml.contains("blockedTags:"), yaml);
        Assertions.assertEquals(JoinMode.INVITE_ONLY, settings.joining().defaultMode());
        Assertions.assertEquals(15, settings.profile().colors().size());
        Assertions.assertEquals(5, settings.profile().tagLength().max());
    }

    @Test
    void treasuryAndAlertsHaveTheAgreedDefaults() {
        var settings = new CompanySettings();

        Assertions.assertTrue(settings.treasury().enabled());
        Assertions.assertEquals(25, settings.treasury().maxPayoutShare());
        Assertions.assertEquals(1.0, settings.treasury().minTransaction());
        Assertions.assertTrue(settings.treasury().payRename());
        Assertions.assertEquals(365, settings.treasury().logRetentionDays());
        Assertions.assertEquals(10, settings.treasury().logPageSize());
        Assertions.assertTrue(settings.alerts().enabled());
        Assertions.assertEquals(3, settings.alerts().maxPerCompany());
        Assertions.assertEquals(15, settings.alerts().minMinutesBetween());
    }

    @Test
    void configWithoutTreasuryAndAlertsGetsTheDefaults() {
        var settings = mapper.readValue("deliveryHours: 24", CompanySettings.class);

        Assertions.assertEquals(25, settings.treasury().maxPayoutShare());
        Assertions.assertEquals(3, settings.maxAlerts(1));
    }

    @Test
    void treasuryAndAlertsAreReadFromYaml() {
        var settings = mapper.readValue("""
                treasury:
                  enabled: false
                  maxPayoutShare: 40
                  minTransaction: 5
                  payRename: false
                  logRetentionDays: 0
                  logPageSize: 20
                alerts:
                  maxPerCompany: 7
                  minMinutesBetween: 30
                """, CompanySettings.class);

        Assertions.assertFalse(settings.treasury().enabled());
        Assertions.assertEquals(40, settings.treasury().maxPayoutShare());
        Assertions.assertEquals(5.0, settings.treasury().minTransaction());
        Assertions.assertFalse(settings.treasury().payRename());
        Assertions.assertEquals(0, settings.treasury().logRetentionDays());
        Assertions.assertEquals(20, settings.treasury().logPageSize());
        Assertions.assertEquals(7, settings.alerts().maxPerCompany());
        Assertions.assertEquals(30, settings.alerts().minMinutesBetween());
    }

    @Test
    void payoutShareIsClampedToTheCurrentCap() {
        var treasury = new CompanySettings().treasury();

        Assertions.assertEquals(10, treasury.effectiveShare(10));
        Assertions.assertEquals(25, treasury.effectiveShare(80));
        Assertions.assertEquals(0, treasury.effectiveShare(-3));
        treasury.maxPayoutShare(5);
        Assertions.assertEquals(5, treasury.effectiveShare(10));
        treasury.maxPayoutShare(500);
        Assertions.assertEquals(100, treasury.maxPayoutShare());
        treasury.enabled(false);
        Assertions.assertEquals(0, treasury.effectiveShare(10));
    }

    @Test
    void levelAlertLimitReplacesTheGlobalLimit() {
        var settings = new CompanySettings();
        settings.levelSettings(3).maxAlerts(5);
        settings.levelSettings(4).maxAlerts(0);

        Assertions.assertEquals(3, settings.maxAlerts(1));
        Assertions.assertEquals(5, settings.maxAlerts(3));
        Assertions.assertEquals(0, settings.maxAlerts(4));
        Assertions.assertEquals(3, settings.maxAlerts(99));
        settings.alerts().enabled(false);
        Assertions.assertEquals(0, settings.maxAlerts(3));
    }

    @Test
    void unsetAlertLimitIsNotWritten() {
        var yaml = mapper.writeValueAsString(new CompanySettings());

        Assertions.assertFalse(yaml.contains("maxAlerts"), yaml);
        Assertions.assertTrue(yaml.contains("treasury:"), yaml);
        Assertions.assertTrue(yaml.contains("alerts:"), yaml);
    }

    @Test
    void levelAlertLimitIsReadFromYaml() {
        var settings = mapper.readValue("""
                level:
                  - levelName: Solo
                    settings:
                      maxAlerts: 6
                  - levelName: Duo
                """, CompanySettings.class);

        Assertions.assertEquals(6, settings.maxAlerts(1));
        Assertions.assertEquals(3, settings.maxAlerts(2));
        Assertions.assertTrue(mapper.writeValueAsString(settings).contains("maxAlerts: 6"));
    }
}
