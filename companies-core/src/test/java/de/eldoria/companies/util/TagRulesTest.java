/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

class TagRulesTest {
    private final ProfileSettings settings = new ProfileSettings();

    @Test
    void tagIsStoredInUpperCase() {
        Assertions.assertEquals(Optional.of("IRON"), TagRules.normalise("iron", settings));
        Assertions.assertEquals(Optional.of("MC42"), TagRules.normalise(" Mc42 ", settings));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "TOOLONG", "", "AB-C", "ÄBC", "A B", "<red>"})
    void tagsOutsideTheRulesAreRefused(String tag) {
        Assertions.assertTrue(TagRules.normalise(tag, settings).isEmpty());
    }

    @Test
    void blockedTagsAreRefusedInAnyCase() {
        Assertions.assertTrue(TagRules.normalise("mod", settings).isEmpty());
        Assertions.assertTrue(TagRules.normalise("Staff", settings).isEmpty());
    }

    @Test
    void blockedTagsMayBeConfiguredInLowerCase() {
        settings.blockedTags(List.of("ops"));

        Assertions.assertTrue(TagRules.normalise("OPS", settings).isEmpty());
        Assertions.assertEquals(Optional.of("MOD"), TagRules.normalise("mod", settings));
    }

    @Test
    void configuredLengthIsApplied() {
        settings.tagLength().min(3);
        settings.tagLength().max(4);

        Assertions.assertTrue(TagRules.normalise("AB", settings).isEmpty());
        Assertions.assertTrue(TagRules.normalise("ABCDE", settings).isEmpty());
        Assertions.assertEquals(Optional.of("ABCD"), TagRules.normalise("abcd", settings));
    }

    @Test
    void longestTagIsCappedByTheColumnWidth() {
        settings.tagLength().max(10);

        Assertions.assertEquals(
                ProfileSettings.MAX_TAG_LENGTH, settings.tagLength().max());
        Assertions.assertTrue(TagRules.normalise("ABCDEF", settings).isEmpty());
    }
}
