/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.level.ICompanyLevel;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * The level of a company increased.
 * <p>
 * Fired off the main thread.
 */
public class CompanyLevelUpEvent extends CompanyLevelChangeEvent {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    /**
     * Creates a new event.
     *
     * @param company  the company
     * @param oldLevel level before the change
     * @param newLevel level after the change
     */
    public CompanyLevelUpEvent(ICompanyProfile company, ICompanyLevel oldLevel, ICompanyLevel newLevel) {
        super(company, oldLevel, newLevel);
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
