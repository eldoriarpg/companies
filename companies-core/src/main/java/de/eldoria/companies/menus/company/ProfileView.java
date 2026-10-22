/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.JoinMode;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.configuration.elements.companylevel.LevelProgress;
import de.eldoria.companies.configuration.elements.companylevel.LevelRequirement;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.util.ProfileColors;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What the profile of a company shows.
 * <p>
 * Members see their own company with the progress towards the next level and the number of orders it works on. Other
 * players see the public profile without either. Both show the description, the tag and name in the company colour,
 * and the join mode.
 *
 * @param id          id of the company
 * @param name        name of the company
 * @param level       current level
 * @param next        the next level with the company's progress towards it, only on the own profile
 * @param founded     founding date, formatted
 * @param leader      name of the owner
 * @param memberCount number of members
 * @param orderCount  number of orders the company works on, only on the own profile
 * @param description description of the company, as entered
 * @param tag         tag of the company
 * @param color       colour of the tag and name
 * @param joinMode    how players become members
 * @param editable    whether the viewer may edit the profile
 */
public record ProfileView(
        int id,
        String name,
        CompanyLevel level,
        Optional<NextLevel> next,
        String founded,
        String leader,
        int memberCount,
        OptionalInt orderCount,
        Optional<String> description,
        Optional<String> tag,
        Optional<TextColor> color,
        JoinMode joinMode,
        boolean editable) {

    /**
     * The profile a member sees of their own company.
     *
     * @param profile    the company
     * @param settings   the configured levels
     * @param stats      current values of the company, or null when they could not be loaded
     * @param orderCount number of orders the company works on
     * @param editable   whether the viewer may edit the profile
     * @return the view
     */
    public static ProfileView own(
            CompanyProfile profile,
            CompanySettings settings,
            @Nullable CompanyStats stats,
            int orderCount,
            boolean editable) {
        var next = settings.level(profile.level() + 1).map(level -> NextLevel.of(level, stats));
        return of(profile, settings, next, OptionalInt.of(orderCount), editable);
    }

    /**
     * The public profile of a company.
     *
     * @param profile  the company
     * @param settings the configured levels
     * @return the view
     */
    public static ProfileView external(CompanyProfile profile, CompanySettings settings) {
        return of(profile, settings, Optional.empty(), OptionalInt.empty(), false);
    }

    private static ProfileView of(
            CompanyProfile profile,
            CompanySettings settings,
            Optional<NextLevel> next,
            OptionalInt orderCount,
            boolean editable) {
        var level = settings.level(profile.level()).orElse(CompanyLevel.DEFAULT);
        return new ProfileView(
                profile.id(),
                profile.name(),
                level,
                next,
                profile.foundedString(),
                MemberEntry.nameOf(profile.owner().player()),
                profile.members().size(),
                orderCount,
                profile.description(),
                profile.tag(),
                profile.color().flatMap(ProfileColors::resolve),
                profile.joinMode(),
                editable);
    }

    /**
     * @return whether this is the profile a member sees of their own company
     */
    public boolean own() {
        return orderCount.isPresent();
    }

    /**
     * The next level and how far the company has come towards its requirements.
     *
     * @param level    the next level
     * @param progress one entry per requirement, empty when the values of the company are unknown
     */
    public record NextLevel(CompanyLevel level, List<Progress> progress) {
        /**
         * @param level the next level
         * @param stats current values of the company, or null when unknown
         * @return the next level with the progress towards it
         */
        public static NextLevel of(CompanyLevel level, @Nullable CompanyStats stats) {
            if (stats == null) return new NextLevel(level, List.of());
            return new NextLevel(level, Progress.of(level.requirement(), stats));
        }
    }

    /**
     * The company's value of one requirement of a level.
     *
     * @param label    locale key naming the requirement
     * @param value    the company's value
     * @param required the value the level requires
     * @param money    whether the values are amounts of money
     */
    public record Progress(String label, double value, double required, boolean money) {
        /**
         * @param requirement requirements of a level
         * @param stats       current values of the company
         * @return the progress towards each requirement
         */
        public static List<Progress> of(LevelRequirement requirement, CompanyStats stats) {
            return List.of(
                    new Progress("words.member", stats.memberCount(), requirement.memberCount(), false),
                    new Progress("level.orderCount", stats.orderCount(), requirement.orderCount(), false),
                    new Progress("level.deliveredItems", stats.deliveredItems(), requirement.deliveredItems(), false),
                    new Progress("level.earnedMoney", stats.price(), requirement.earnedMoney(), true));
        }

        /**
         * @return the share of the requirement reached, from 0 to 1
         */
        public double share() {
            return LevelProgress.of(value, required);
        }
    }
}
