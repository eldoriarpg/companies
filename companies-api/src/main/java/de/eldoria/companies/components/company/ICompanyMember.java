/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

/**
 * A member of a company with their permissions in it.
 */
public interface ICompanyMember {
    /**
     * Whether the member has a permission. The owner has every permission.
     *
     * @param permissions permission to check
     * @return true when the member has it
     */
    boolean hasPermission(CompanyPermission permissions);

    /**
     * Whether the member has all of the permissions. The owner has every permission.
     *
     * @param permissions permissions to check
     * @return true when the member has every one of them
     */
    boolean hasPermissions(CompanyPermission... permissions);

    /**
     * Whether the member has at least one of the permissions. The owner has every permission.
     *
     * @param permissions permissions to check
     * @return true when the member has one of them
     */
    boolean hasAnyPermissions(CompanyPermission... permissions);

    /**
     * Grants a permission on this object. Nothing is saved.
     *
     * @param permission permission to grant
     * @return this member
     */
    ICompanyMember addPermission(CompanyPermission permission);

    /**
     * Revokes a permission on this object. Nothing is saved, and the owner keeps every permission.
     *
     * @param permission permission to revoke
     * @return this member
     */
    ICompanyMember removePermission(CompanyPermission permission);

    /**
     * The member as a player.
     *
     * @return player of the member
     */
    OfflinePlayer player();

    /**
     * The company of the member.
     *
     * @return id of the company
     */
    int company();

    /**
     * The id of the member.
     *
     * @return id of the player
     */
    UUID uuid();

    /**
     * The permissions of the member as a bit mask, see {@link CompanyPermission#mask()}.
     *
     * @return permission mask
     */
    long permission();

    /**
     * The permissions of the member.
     *
     * @return granted permissions
     */
    List<CompanyPermission> permissions();

    /**
     * Whether the member owns the company.
     *
     * @return true for the owner
     */
    boolean isOwner();
}
