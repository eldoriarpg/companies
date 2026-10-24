/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * A player took back their application to a company.
 * <p>
 * Fired after the application was removed, off the main thread, only on the server where it was taken back.
 */
public class CompanyApplicationWithdrawEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID applicant;

    /**
     * Creates a new event.
     *
     * @param company   the company the player had applied to
     * @param applicant id of the player who took the application back
     */
    public CompanyApplicationWithdrawEvent(ICompanyProfile company, UUID applicant) {
        super(company, true);
        this.applicant = Objects.requireNonNull(applicant, "applicant");
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
     * The player who took the application back.
     *
     * @return id of the player
     */
    public UUID applicant() {
        return applicant;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
