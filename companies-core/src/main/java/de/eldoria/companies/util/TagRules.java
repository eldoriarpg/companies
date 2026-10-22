/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns a tag entered by a player into the stored form: upper case letters and digits, within the configured length
 * and not blocked.
 */
public final class TagRules {
    private static final Pattern ALLOWED = Pattern.compile("[A-Z0-9]+");

    private TagRules() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Normalises a tag.
     *
     * @param input    tag as entered
     * @param settings profile settings with length and blocked tags
     * @return the tag in upper case, or empty when it has other characters, a length outside the limits or is blocked
     */
    public static Optional<String> normalise(String input, ProfileSettings settings) {
        var tag = input.strip().toUpperCase(Locale.ROOT);
        var length = settings.tagLength();
        if (tag.length() < length.min() || tag.length() > length.max()) return Optional.empty();
        if (!ALLOWED.matcher(tag).matches()) return Optional.empty();
        if (settings.blockedTags().contains(tag)) return Optional.empty();
        return Optional.of(tag);
    }
}
