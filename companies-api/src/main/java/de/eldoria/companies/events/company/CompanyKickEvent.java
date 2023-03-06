/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.OfflinePlayer;

/**
 * A user was kicked from a company.
 * <p>
 * This is a speification of the {@link CompanyLeaveEvent}
 */
public class CompanyKickEvent extends CompanyLeaveEvent {
    public CompanyKickEvent(ICompanyProfile company, OfflinePlayer player) {
        super(company, player);
    }
}
