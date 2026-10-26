/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.configuration.SetupPages;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupReminderTest {
    @Test
    void remindsUntilEveryPageIsDoneOrSkippedOrTheReminderIsHidden() {
        assertTrue(SetupReminder.open(Map.of()));

        var progress = new HashMap<String, String>();
        SetupPages.PAGES.forEach(page -> progress.put(page.id(), "done"));
        progress.put(SetupPages.PAGES.getFirst().id(), "skipped");
        assertFalse(SetupReminder.open(progress));

        progress.put(SetupPages.PAGES.getLast().id(), "open");
        assertTrue(SetupReminder.open(progress));
        progress.put("banner", "dismissed");
        assertFalse(SetupReminder.open(progress));
    }
}
