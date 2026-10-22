/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.elements.companysettings.ProfileSettings;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.util.ProfileColors;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Optional;

/**
 * What the profile editor shows: the current description, tag and colour with their limits, and the join mode, which
 * is changed elsewhere.
 *
 * @param name                 name of the company
 * @param description          current description
 * @param tag                  current tag
 * @param color                current colour as stored
 * @param joinMode             how players become members
 * @param palette              the colours to choose from, as stored
 * @param maxDescriptionLength the longest description
 * @param maxTagLength         the longest tag
 * @param error                why the last edit was refused, shown on top of the dialog
 */
public record ProfileEditView(
        String name,
        Optional<String> description,
        Optional<String> tag,
        Optional<String> color,
        JoinMode joinMode,
        List<String> palette,
        int maxDescriptionLength,
        int maxTagLength,
        Optional<CommandFailure> error) {

    /**
     * @param profile  the company
     * @param settings limits of the profile fields
     * @return the editor of the company's profile
     */
    public static ProfileEditView of(CompanyProfile profile, ProfileSettings settings) {
        var palette = settings.colors().stream()
                .flatMap(color -> ProfileColors.fromPalette(color, settings).stream())
                .distinct()
                .toList();
        return new ProfileEditView(
                profile.name(),
                profile.description(),
                profile.tag(),
                profile.color(),
                profile.joinMode(),
                palette,
                Math.max(1, settings.maxDescriptionLength()),
                settings.tagLength().max(),
                Optional.empty());
    }

    /**
     * @param failure why the last edit was refused
     * @return this view showing the error
     */
    public ProfileEditView withError(CommandFailure failure) {
        return new ProfileEditView(
                name,
                description,
                tag,
                color,
                joinMode,
                palette,
                maxDescriptionLength,
                maxTagLength,
                Optional.of(failure));
    }

    /**
     * @return the current colour, empty when there is none or it is unknown
     */
    public Optional<TextColor> textColor() {
        return color.flatMap(ProfileColors::resolve);
    }

    /**
     * Whether a palette entry is the current colour.
     *
     * @param entry palette entry
     * @return true when it is the current colour
     */
    public boolean selected(String entry) {
        return color.map(entry::equals).orElse(false);
    }
}
