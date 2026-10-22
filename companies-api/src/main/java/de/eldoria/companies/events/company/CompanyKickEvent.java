/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.OfflinePlayer;

/**
 * A member was removed from a company by another member.
 * <p>
 * A special case of {@link CompanyLeaveEvent}; listeners of that event receive it too.
 */
public class CompanyKickEvent extends CompanyLeaveEvent {
    /**
     * Creates a new event.
     *
     * @param company the company without the member
     * @param player  player who was removed
     */
    public CompanyKickEvent(ICompanyProfile company, OfflinePlayer player) {
        super(company, player);
    }
}
