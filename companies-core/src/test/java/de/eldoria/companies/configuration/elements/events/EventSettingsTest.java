/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventSettingsTest {
    @Test
    void theCapDefaultsToThree() {
        assertEquals(3.0, new EventSettings().maxMultiplier());
    }

    @Test
    void theCapIsNeverBelowOneNorUnreadable() {
        var settings = new EventSettings();

        settings.maxMultiplier(2.5);
        assertEquals(2.5, settings.maxMultiplier());
        settings.maxMultiplier(0.5);
        assertEquals(1.0, settings.maxMultiplier());
        settings.maxMultiplier(-3);
        assertEquals(1.0, settings.maxMultiplier());
        settings.maxMultiplier(Double.NaN);
        assertEquals(1.0, settings.maxMultiplier());
        settings.maxMultiplier(Double.POSITIVE_INFINITY);
        assertEquals(1.0, settings.maxMultiplier());
    }
}
