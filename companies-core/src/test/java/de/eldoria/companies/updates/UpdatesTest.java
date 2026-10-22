/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.updates;

import de.eldoria.companies.configuration.elements.generalsettings.UpdateSettings;
import de.eldoria.eldoutilities.updater.AutoUpdateDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatesTest {
    @Test
    void enablingAutoUpdateKeepsAsking() {
        var settings = new UpdateSettings();

        Updates.decide(settings, AutoUpdateDecision.ENABLED);

        assertTrue(settings.autoUpdate());
        assertTrue(settings.askForAutoUpdate());
    }

    @Test
    void neverAskKeepsAutoUpdateOff() {
        var settings = new UpdateSettings();

        Updates.decide(settings, AutoUpdateDecision.NEVER_ASK);

        assertFalse(settings.autoUpdate());
        assertFalse(settings.askForAutoUpdate());
    }
}
