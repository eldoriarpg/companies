/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ISimpleCompany;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The areas or the home of a company changed: an area was linked, unlinked, bought, rented, given back or renamed, a
 * rent was renewed or ended, or the home was set. Read the new state with {@code companies().areas(id)} and
 * {@code companies().home(id)}.
 * <p>
 * Fired asynchronously after the change was saved, only on the server where it happened.
 */
public class CompanyAreasChangeEvent extends CompanyEvent<ISimpleCompany> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Change change;

    /**
     * Creates a new asynchronous event.
     *
     * @param company the company
     * @param change  what changed
     */
    public CompanyAreasChangeEvent(ISimpleCompany company, Change change) {
        super(company, true);
        this.change = Objects.requireNonNull(change, "change");
    }

    /**
     * The handlers of this event.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * What changed.
     *
     * @return the areas or the home
     */
    public Change change() {
        return change;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * What of a company changed.
     */
    public enum Change {
        /**
         * Its areas: one was added, removed or renamed, or a rent was renewed or ended.
         */
        AREAS,
        /**
         * Its home.
         */
        HOME
    }
}
