/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.ProfileColors;
import de.eldoria.companies.util.TagRules;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Profile fields a member wants to change, as entered. A field that is absent stays as it is; a blank value removes it.
 *
 * @param description new description
 * @param tag         new tag
 * @param color       new colour, a palette entry
 */
public record ProfileChange(Optional<String> description, Optional<String> tag, Optional<String> color) {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * @param description new description, blank to remove it
     * @return change of the description only
     */
    public static ProfileChange description(String description) {
        return new ProfileChange(Optional.of(description), Optional.empty(), Optional.empty());
    }

    /**
     * @param tag new tag, blank to remove it
     * @return change of the tag only
     */
    public static ProfileChange tag(String tag) {
        return new ProfileChange(Optional.empty(), Optional.of(tag), Optional.empty());
    }

    /**
     * @param color new colour, blank to remove it
     * @return change of the colour only
     */
    public static ProfileChange color(String color) {
        return new ProfileChange(Optional.empty(), Optional.empty(), Optional.of(color));
    }

    /**
     * @param description new description, blank to remove it
     * @param tag         new tag, blank to remove it
     * @param color       new colour, blank to remove it
     * @return change of every field, as a form submits it
     */
    public static ProfileChange all(String description, String tag, String color) {
        return new ProfileChange(Optional.of(description), Optional.of(tag), Optional.of(color));
    }

    /**
     * Validates the change and applies it.
     * <p>
     * Descriptions are stored with every run of whitespace, line breaks included, collapsed to one space. Tags are
     * normalised by {@link TagRules}, colours must be part of the palette.
     *
     * @param details  current details of the company
     * @param settings limits of the profile fields
     * @return the changed details
     * @throws CommandFailure when a value breaks the limits
     */
    public CompanyDetails applyTo(CompanyDetails details, ProfileSettings settings) {
        var changed = details;
        if (description.isPresent()) changed = changed.withDescription(validDescription(description.get(), settings));
        if (tag.isPresent()) changed = changed.withTag(validTag(tag.get(), settings));
        if (color.isPresent()) changed = changed.withColor(validColor(color.get(), settings));
        return changed;
    }

    private static @Nullable String validDescription(String input, ProfileSettings settings) {
        var text = WHITESPACE.matcher(input).replaceAll(" ").strip();
        if (text.isEmpty()) return null;
        var max = settings.maxDescriptionLength();
        if (text.length() > max) {
            throw CommandFailure.actionBar("company.profile.edit.error.tooLong", Values.number("max", max));
        }
        return text;
    }

    private static @Nullable String validTag(String input, ProfileSettings settings) {
        if (input.isBlank()) return null;
        var length = settings.tagLength();
        return TagRules.normalise(input, settings)
                .orElseThrow(() -> CommandFailure.actionBar(
                        "company.profile.edit.error.tagInvalid",
                        Values.number("min", length.min()),
                        Values.number("max", length.max())));
    }

    private static @Nullable String validColor(String input, ProfileSettings settings) {
        if (input.isBlank()) return null;
        return ProfileColors.fromPalette(input, settings)
                .orElseThrow(() -> CommandFailure.actionBar(
                        "company.profile.edit.error.colorUnknown", Values.text("color_name", input.strip())));
    }
}
