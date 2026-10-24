/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileChangeTest {
    private static final CompanyDetails CURRENT = new CompanyDetails("We dig.", "MINE", "gold", JoinMode.OPEN);

    private final ProfileSettings settings = new ProfileSettings();

    private String failure(ProfileChange change) {
        return assertThrows(CommandFailure.class, () -> change.applyTo(CURRENT, settings))
                .localeKey();
    }

    @Test
    void aDescriptionIsStoredOnOneLine() {
        var details = ProfileChange.description("  We\ndig\t deep. ").applyTo(CURRENT, settings);

        assertEquals("We dig deep.", details.description());
        assertEquals(CURRENT.withDescription("We dig deep."), details);
    }

    @Test
    void aDescriptionIsStoredAsEntered() {
        var details = ProfileChange.description("<red>We dig</red>").applyTo(CURRENT, settings);

        assertEquals("<red>We dig</red>", details.description());
    }

    @Test
    void aBlankDescriptionRemovesIt() {
        assertNull(ProfileChange.description("   ").applyTo(CURRENT, settings).description());
    }

    @Test
    void aDescriptionLongerThanTheLimitIsRefused() {
        settings.maxDescriptionLength(5);

        assertEquals(
                CURRENT.withDescription("12345"),
                ProfileChange.description("12345").applyTo(CURRENT, settings));
        assertEquals("company.profile.edit.error.tooLong", failure(ProfileChange.description("123456")));
    }

    @Test
    void aTagIsNormalisedToUpperCase() {
        assertEquals(
                "IRON", ProfileChange.tag(" iron ").applyTo(CURRENT, settings).tag());
    }

    @Test
    void invalidTagsAreRefused() {
        assertEquals("company.profile.edit.error.tagInvalid", failure(ProfileChange.tag("A")));
        assertEquals("company.profile.edit.error.tagInvalid", failure(ProfileChange.tag("TOOLONG")));
        assertEquals("company.profile.edit.error.tagInvalid", failure(ProfileChange.tag("A-B")));
        assertEquals("company.profile.edit.error.tagInvalid", failure(ProfileChange.tag("<b>")));
    }

    @Test
    void blockedTagsAreRefusedInAnyCase() {
        settings.blockedTags(List.of("staff"));

        assertEquals("company.profile.edit.error.tagInvalid", failure(ProfileChange.tag("Staff")));
    }

    @Test
    void aBlankTagRemovesIt() {
        assertNull(ProfileChange.tag("").applyTo(CURRENT, settings).tag());
    }

    @Test
    void aColourMustBeInThePalette() {
        settings.colors(List.of("red", "#00ff00"));

        assertEquals(
                "red", ProfileChange.color("RED").applyTo(CURRENT, settings).color());
        assertEquals(
                "#00ff00",
                ProfileChange.color("#00FF00").applyTo(CURRENT, settings).color());
        assertEquals("company.profile.edit.error.colorUnknown", failure(ProfileChange.color("blue")));
        assertEquals("company.profile.edit.error.colorUnknown", failure(ProfileChange.color("rainbow")));
    }

    @Test
    void aBlankColourRemovesIt() {
        assertNull(ProfileChange.color(" ").applyTo(CURRENT, settings).color());
    }

    @Test
    void aFormChangesEveryFieldAndKeepsTheJoinMode() {
        var details = ProfileChange.all("Smelting.", "ore", "red").applyTo(CURRENT, settings);

        assertEquals(new CompanyDetails("Smelting.", "ORE", "red", JoinMode.OPEN), details);
    }

    @Test
    void oneInvalidFieldRefusesTheWholeForm() {
        assertEquals("company.profile.edit.error.colorUnknown", failure(ProfileChange.all("Smelting.", "ore", "x")));
    }
}
