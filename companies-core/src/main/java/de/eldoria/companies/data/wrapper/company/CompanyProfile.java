/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.OfflinePlayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class CompanyProfile extends SimpleCompany implements ICompanyProfile {
    private final List<CompanyMember> members;

    public CompanyProfile(
            int id,
            String name,
            LocalDateTime founded,
            int level,
            CompanyDetails details,
            List<CompanyMember> members) {
        super(id, name, founded, level, details);
        this.members = members;
    }

    /**
     * Copies this profile with other details and the same members.
     *
     * @param details new details
     * @return changed copy
     */
    public CompanyProfile withDetails(CompanyDetails details) {
        return new CompanyProfile(id(), name(), founded(), level(), details, members);
    }

    @Override
    public Optional<CompanyMember> member(OfflinePlayer player) {
        return members.stream()
                .filter(m -> m.uuid().equals(player.getUniqueId()))
                .findAny();
    }

    @Override
    public List<? extends ICompanyMember> members() {
        return members;
    }

    @Override
    public List<CompanyMember> members(CompanyPermission... permissions) {
        return members.stream().filter(m -> m.hasPermissions(permissions)).collect(Collectors.toList());
    }

    @Override
    public CompanyMember owner() {
        return members.stream()
                .filter(m -> m.hasPermission(CompanyPermission.OWNER))
                .findFirst()
                .get();
    }

    @Override
    public Optional<CompanyMember> memberByName(String name) {
        return members.stream()
                .filter(member -> Optional.ofNullable(member.player().getName())
                        .map(n -> n.equalsIgnoreCase(name))
                        .orElse(false))
                .findFirst();
    }

    /**
     * Whether the player is a member of this company.
     *
     * @param player id of the player
     * @return true when the player is a member
     */
    public boolean isMember(UUID player) {
        return members.stream().anyMatch(member -> member.uuid().equals(player));
    }

    /**
     * Whether this company may work on an order of the given owner.
     *
     * @param owner             id of the order owner
     * @param allowMemberOrders whether companies may accept orders of their own members
     * @return false when the owner is a member and member orders are not allowed
     */
    public boolean mayAcceptOrderOf(UUID owner, boolean allowMemberOrders) {
        return allowMemberOrders || !isMember(owner);
    }
}
