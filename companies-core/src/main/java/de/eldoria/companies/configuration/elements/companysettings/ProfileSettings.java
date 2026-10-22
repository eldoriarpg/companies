/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements.companysettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Limits of the profile fields members can edit: description, tag and colour.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
public class ProfileSettings {
    /**
     * The longest tag the database can store.
     */
    public static final int MAX_TAG_LENGTH = 5;

    private int maxDescriptionLength = 160;
    private TagLength tagLength = new TagLength();
    private List<String> blockedTags = new ArrayList<>(List.of("ADMIN", "MOD", "STAFF"));
    private List<String> colors = new ArrayList<>(List.of(
            "white",
            "gray",
            "dark_gray",
            "red",
            "dark_red",
            "gold",
            "yellow",
            "green",
            "dark_green",
            "aqua",
            "dark_aqua",
            "blue",
            "dark_blue",
            "light_purple",
            "dark_purple"));

    public ProfileSettings() {}

    /**
     * The longest description a company may have.
     *
     * @return maximum number of characters
     */
    public int maxDescriptionLength() {
        return maxDescriptionLength;
    }

    public void maxDescriptionLength(int maxDescriptionLength) {
        this.maxDescriptionLength = maxDescriptionLength;
    }

    /**
     * The allowed length of a tag.
     *
     * @return tag length
     */
    public TagLength tagLength() {
        return tagLength == null ? new TagLength() : tagLength;
    }

    /**
     * Tags no company may use, compared in upper case.
     *
     * @return blocked tags in upper case
     */
    public List<String> blockedTags() {
        if (blockedTags == null) return List.of();
        return blockedTags.stream().map(tag -> tag.toUpperCase(Locale.ROOT)).toList();
    }

    public void blockedTags(List<String> blockedTags) {
        this.blockedTags = new ArrayList<>(blockedTags);
    }

    /**
     * The colours a company may choose for its tag and name: names of named text colours or {@code #rrggbb} values.
     *
     * @return palette as configured
     */
    public List<String> colors() {
        return colors == null ? List.of() : List.copyOf(colors);
    }

    public void colors(List<String> colors) {
        this.colors = new ArrayList<>(colors);
    }

    /**
     * Shortest and longest tag. The longest tag is capped at {@link #MAX_TAG_LENGTH}, the width of the tag column.
     */
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "RedundantNoArgConstructor"})
    public static class TagLength {
        private int min = 2;
        private int max = MAX_TAG_LENGTH;

        public TagLength() {}

        /**
         * The shortest tag.
         *
         * @return minimum number of characters, at least 1
         */
        public int min() {
            return Math.max(1, Math.min(min, max()));
        }

        /**
         * The longest tag.
         *
         * @return maximum number of characters, at most {@link #MAX_TAG_LENGTH}
         */
        public int max() {
            return Math.max(1, Math.min(max, MAX_TAG_LENGTH));
        }

        public void min(int min) {
            this.min = min;
        }

        public void max(int max) {
            this.max = max;
        }
    }
}
