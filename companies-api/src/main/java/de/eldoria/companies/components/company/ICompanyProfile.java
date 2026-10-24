/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Optional;

/**
 * A company with its members and profile details.
 */
public interface ICompanyProfile extends ISimpleCompany {
    /**
     * The membership of a player in this company.
     *
     * @param player player to look up
     * @return the member, or empty when the player is not a member
     */
    Optional<? extends ICompanyMember> member(OfflinePlayer player);

    /**
     * All members, the owner included.
     *
     * @return members
     */
    List<? extends ICompanyMember> members();

    /**
     * The members who have all of the permissions. The owner has every permission.
     *
     * @param permissions required permissions
     * @return matching members
     */
    List<? extends ICompanyMember> members(CompanyPermission... permissions);

    /**
     * The owner of the company.
     *
     * @return owner
     */
    ICompanyMember owner();

    /**
     * The member with a player name.
     *
     * @param name name of the player, in any case
     * @return the member, or empty when no member has this name
     */
    Optional<? extends ICompanyMember> memberByName(String name);

    /**
     * The free text the company shows on its profile.
     *
     * @return description as entered, or empty when none is set
     */
    Optional<String> description();

    /**
     * The short tag of the company.
     *
     * @return upper case tag of letters and digits, or empty when none is set
     */
    Optional<String> tag();

    /**
     * The colour of the company's tag and name.
     *
     * @return name of a named text colour or a {@code #rrggbb} value, or empty when none is set
     */
    Optional<String> color();

    /**
     * How players can become members of the company.
     *
     * @return join mode
     */
    JoinMode joinMode();
}
