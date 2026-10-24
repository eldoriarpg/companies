/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * A player applied to a company.
 * <p>
 * Fired after the application was saved, off the main thread, only on the server where the player applied.
 */
public class CompanyApplicationEvent extends CompanyEvent<ICompanyProfile> {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID applicant;
    private final String message;

    /**
     * Creates a new event.
     *
     * @param company   the company the player applied to
     * @param applicant id of the applying player
     * @param message   message of the application, on one line
     */
    public CompanyApplicationEvent(ICompanyProfile company, UUID applicant, String message) {
        super(company, true);
        this.applicant = Objects.requireNonNull(applicant, "applicant");
        this.message = Objects.requireNonNull(message, "message");
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
     * The applying player.
     *
     * @return id of the player
     */
    public UUID applicant() {
        return applicant;
    }

    /**
     * The message of the application, as it was saved.
     *
     * @return message on one line
     */
    public String message() {
        return message;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
