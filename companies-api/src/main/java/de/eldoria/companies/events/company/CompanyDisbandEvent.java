/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A company gets disbanded by its owner.
 */
public class CompanyDisbandEvent extends CompanyEvent<ICompanyProfile> {
    public static final HandlerList HANDLERS = new HandlerList();

    public CompanyDisbandEvent(ICompanyProfile company) {
        super(company, true);
    }

    @SuppressWarnings("SameReturnValue")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
