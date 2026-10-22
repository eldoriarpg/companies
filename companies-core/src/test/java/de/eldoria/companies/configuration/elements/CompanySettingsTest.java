/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class CompanySettingsTest {

    @Test
    void deletingALevelRenumbersTheFollowingLevels() {
        var settings = new CompanySettings();
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
        var settings = new CompanySettings();
        settings.createLevel(1);

        Assertions.assertTrue(settings.level(0).isEmpty());
        Assertions.assertTrue(settings.level(2).isEmpty());
    }
}
