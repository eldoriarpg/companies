/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the member list of a company shows.
 * <p>
 * The owner comes first, then the members by name. Permissions and contributions are shown only to members of the
 * company, and only members who may manage permissions get the button to edit them.
 *
 * @param companyName       name of the company
 * @param members           the members, owner first
 * @param showPermissions   whether the viewer may see the permissions of the members
 * @param managePermissions whether the viewer may edit the permissions of the members
 * @param contributions     what the members contributed, present only for members of the company
 */
public record MemberView(
        String companyName,
        List<MemberEntry> members,
        boolean showPermissions,
        boolean managePermissions,
        Optional<MemberContributions> contributions) {
    private static final Comparator<MemberEntry> ORDER = Comparator.comparing((MemberEntry entry) -> !entry.owner())
            .thenComparing(MemberEntry::name, String.CASE_INSENSITIVE_ORDER);

    /**
     * The member list a player sees of a company.
     *
     * @param profile the company
     * @param viewer  the viewing player
     * @return the view
     */
    public static MemberView of(CompanyProfile profile, Player viewer) {
        var self = profile.member(viewer);
        var members = profile.members().stream()
                .map(member -> MemberEntry.of((CompanyMember) member))
                .sorted(ORDER)
                .toList();
        return new MemberView(
                profile.name(),
                members,
                self.isPresent(),
                self.map(member -> member.hasPermission(CompanyPermission.MANAGE_PERMISSIONS))
                        .orElse(false),
                Optional.empty());
    }

    /**
     * Adds what the members contributed. Viewers outside the company never see it.
     *
     * @param contributions contributions of the members
     * @return the view with the contributions, or this view when the viewer is not a member
     */
    public MemberView withContributions(MemberContributions contributions) {
        if (!showPermissions) return this;
        return new MemberView(companyName, members, true, managePermissions, Optional.of(contributions));
    }

    /**
     * The members in the order of a sorting. Without contributions the members stay owner first, then by name.
     *
     * @param sorting what to sort by
     * @param range   range of the contributions to sort by
     * @return the sorted members
     */
    public List<MemberEntry> sorted(MemberSorting sorting, TimeRange range) {
        if (sorting == MemberSorting.NAME || contributions.isEmpty()) return members;
        var stats = contributions.get();
        Comparator<MemberEntry> byValue =
                Comparator.comparingDouble(entry -> sorting.value(stats.of(entry.uuid(), range)));
        return members.stream()
                .sorted(byValue.reversed().thenComparing(MemberEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * @param uuid id of a player
     * @return the member with this id
     */
    public Optional<MemberEntry> member(UUID uuid) {
        return members.stream().filter(member -> member.uuid().equals(uuid)).findFirst();
    }
}
