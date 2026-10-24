/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.company.JoinMode;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * A member changed how players join a company.
 * <p>
 * Fired after the new join mode was saved and after the {@link CompanyProfileEditEvent} of the same change, off the
 * main thread, only on the server where the change was made.
 */
public class CompanyJoinModeChangeEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final JoinMode previous;
    private final UUID actor;

    /**
     * Creates a new event.
     *
     * @param company  the company with its new join mode
     * @param previous join mode before the change
     * @param actor    id of the member who changed it
     */
    public CompanyJoinModeChangeEvent(ICompanyProfile company, JoinMode previous, UUID actor) {
        super(company, true);
        this.previous = Objects.requireNonNull(previous, "previous");
        this.actor = Objects.requireNonNull(actor, "actor");
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
     * The join mode before the change.
     *
     * @return previous join mode
     */
    public JoinMode previous() {
        return previous;
    }

    /**
     * The join mode after the change.
     *
     * @return new join mode, the same as {@code company().joinMode()}
     */
    public JoinMode joinMode() {
        return company().joinMode();
    }

    /**
     * The member who changed the join mode.
     *
     * @return id of the member
     */
    public UUID actor() {
        return actor;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
