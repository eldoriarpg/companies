/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.level.ICompanyLevel;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * The level of a company changed.
 * <p>
 * Fired off the main thread, always as one of its subclasses. Both share this handler list, so a listener of this
 * event receives every change. Listen to {@link CompanyLevelUpEvent} or {@link CompanyLevelDownEvent} for one
 * direction.
 */
public class CompanyLevelChangeEvent extends CompanyEvent<ICompanyProfile> {
    /**
     * The handlers of this event.
     */
    public static final HandlerList HANDLERS = new HandlerList();

    private final ICompanyLevel oldLevel;
    private final ICompanyLevel newLevel;

    /**
     * Creates a new event.
     *
     * @param company  the company
     * @param oldLevel level before the change
     * @param newLevel level after the change
     */
    public CompanyLevelChangeEvent(ICompanyProfile company, ICompanyLevel oldLevel, ICompanyLevel newLevel) {
        super(company, true);
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
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

    /**
     * The old level before the change
     *
     * @return company level
     */
    public ICompanyLevel oldLevel() {
        return oldLevel;
    }

    /**
     * The new level after the change
     *
     * @return company level
     */
    public ICompanyLevel newLevel() {
        return newLevel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
