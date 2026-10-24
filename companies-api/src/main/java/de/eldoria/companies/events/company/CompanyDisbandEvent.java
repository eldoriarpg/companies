/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A company was disbanded.
 * <p>
 * Fired off the main thread.
 */
public class CompanyDisbandEvent extends CompanyEvent<ICompanyProfile> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    /**
     * Creates a new event.
     *
     * @param company the company before it was deleted
     */
    public CompanyDisbandEvent(ICompanyProfile company) {
        super(company, true);
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
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
