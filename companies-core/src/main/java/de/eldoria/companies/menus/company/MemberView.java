/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.company;

import de.eldoria.companies.components.company.CompanyPermission;
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
 * The owner comes first, then the members by name. Permissions are shown only to members of the company, and only
 * members who may manage permissions get the button to edit them.
 *
 * @param companyName      name of the company
 * @param members          the members, owner first
 * @param showPermissions  whether the viewer may see the permissions of the members
 * @param managePermissions whether the viewer may edit the permissions of the members
 */
public record MemberView(
        String companyName, List<MemberEntry> members, boolean showPermissions, boolean managePermissions) {
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
                        .orElse(false));
    }

    /**
     * @param uuid id of a player
     * @return the member with this id
     */
    public Optional<MemberEntry> member(UUID uuid) {
        return members.stream().filter(member -> member.uuid().equals(uuid)).findFirst();
    }
}
