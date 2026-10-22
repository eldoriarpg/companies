/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Optional;

public interface ICompanyProfile extends ISimpleCompany {
    Optional<? extends ICompanyMember> member(OfflinePlayer player);

    List<? extends ICompanyMember> members();

    List<? extends ICompanyMember> members(CompanyPermission... permissions);

    ICompanyMember owner();

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
